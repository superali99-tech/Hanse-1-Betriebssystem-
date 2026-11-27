"""Nova Disk – what fills the disk: drives, folders, file types, largest files.

Qt-independent and tested (tests/unit/test_disk.py). Scanning never follows symbolic
links and stays on one file system (like `du -x`).
"""

from __future__ import annotations

import heapq
import json
import os
import shutil
import subprocess
from dataclasses import dataclass, field

CATEGORIES = {
    "images": ("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff", "raw", "cr2", "nef", "arw", "dng", "svg"),
    "videos": ("mp4", "mkv", "mov", "avi", "webm", "m4v", "wmv", "flv", "3gp", "mts", "m2ts"),
    "music": ("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav", "wma"),
    "documents": ("pdf", "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "txt", "md", "rtf", "csv", "epub"),
    "archives": ("zip", "tar", "gz", "xz", "bz2", "zst", "7z", "rar", "iso", "img", "deb", "flatpak"),
}
_EXT = {e: cat for cat, exts in CATEGORIES.items() for e in exts}


def category(name: str) -> str:
    ext = name.rsplit(".", 1)[-1].lower() if "." in name else ""
    return _EXT.get(ext, "other")


def human_size(n: float, german: bool = True) -> str:
    units = ["B", "KB", "MB", "GB", "TB"]
    i = 0
    while n >= 1000 and i < len(units) - 1:
        n /= 1000
        i += 1
    s = f"{n:.0f}" if i == 0 or n >= 100 else f"{n:.1f}"
    return (s.replace(".", ",") if german else s) + " " + units[i]


@dataclass
class ScanResult:
    root: str
    total: int = 0
    files: int = 0
    children: dict[str, int] = field(default_factory=dict)     # direct children of root -> size
    by_category: dict[str, int] = field(default_factory=dict)
    largest: list[tuple[int, str]] = field(default_factory=list)  # (size, path), biggest first
    skipped: int = 0                                             # folders without permission

    def top_children(self, n: int = 12) -> list[tuple[str, int]]:
        return sorted(self.children.items(), key=lambda kv: -kv[1])[:n]


def scan(root: str, progress=None, cancelled=lambda: False, keep_largest: int = 50) -> ScanResult:
    """Walks root (one file system, no symlinks). progress(files, bytes) is called now and then."""
    res = ScanResult(root)
    try:
        dev = os.lstat(root).st_dev
    except OSError:
        return res
    heap: list[tuple[int, str]] = []
    # the first level decides which child a file belongs to
    stack: list[tuple[str, str | None]] = [(root, None)]
    while stack:
        if cancelled():
            break
        path, child = stack.pop()
        try:
            it = os.scandir(path)
        except OSError:
            res.skipped += 1
            continue
        with it:
            for e in it:
                try:
                    st = e.stat(follow_symlinks=False)
                except OSError:
                    continue
                key = child if child is not None else e.name
                if e.is_dir(follow_symlinks=False):
                    if st.st_dev == dev:
                        stack.append((e.path, key))
                        res.children.setdefault(key, 0)
                    continue
                if not e.is_file(follow_symlinks=False):
                    continue
                size = st.st_size
                res.total += size
                res.files += 1
                res.children[key] = res.children.get(key, 0) + size
                cat = category(e.name)
                res.by_category[cat] = res.by_category.get(cat, 0) + size
                if len(heap) < keep_largest:
                    heapq.heappush(heap, (size, e.path))
                elif size > heap[0][0]:
                    heapq.heapreplace(heap, (size, e.path))
                if progress and res.files % 2000 == 0:
                    progress(res.files, res.total)
    res.largest = sorted(heap, reverse=True)
    return res


# ------------------------------------------------------------------ drives
@dataclass
class Drive:
    name: str          # sda, nvme0n1, mmcblk0
    path: str          # /dev/sda
    size: int
    model: str
    removable: bool
    transport: str     # usb, nvme, sata, mmc ...
    partitions: list[dict] = field(default_factory=list)   # {path, size, fstype, label, mounts}

    @property
    def kind(self) -> str:
        if self.transport == "usb" or self.removable:
            return "usb"
        if self.name.startswith("mmcblk"):
            return "sd"
        return "nvme" if self.transport == "nvme" or self.name.startswith("nvme") else "disk"


def parse_lsblk(text: str) -> list[Drive]:
    """`lsblk -J -b -o NAME,PATH,SIZE,TYPE,MOUNTPOINTS,FSTYPE,LABEL,MODEL,RM,TRAN` -> drives (no loop/zram/rom)."""
    try:
        data = json.loads(text or "{}")
    except ValueError:
        return []
    drives = []
    for d in data.get("blockdevices", []):
        if d.get("type") != "disk" or str(d.get("name", "")).startswith(("loop", "zram", "ram")):
            continue

        def parts(node):
            out = []
            for c in node.get("children", []) or []:
                mounts = [m for m in (c.get("mountpoints") or []) if m]
                out.append({"path": c.get("path", ""), "size": int(c.get("size") or 0), "fstype": c.get("fstype") or "",
                            "label": c.get("label") or "", "mounts": mounts})
                out.extend(parts(c))   # e.g. LUKS -> filesystem
            return out

        drives.append(Drive(d.get("name", ""), d.get("path", ""), int(d.get("size") or 0), (d.get("model") or "").strip(),
                            bool(d.get("rm")), d.get("tran") or "", parts(d)))
    return drives


def drives() -> list[Drive]:
    try:
        res = subprocess.run(["lsblk", "-J", "-b", "-o", "NAME,PATH,SIZE,TYPE,MOUNTPOINTS,FSTYPE,LABEL,MODEL,RM,TRAN"],
                             capture_output=True, text=True, timeout=20)
        return parse_lsblk(res.stdout)
    except (OSError, subprocess.SubprocessError):
        return []


def usage(mount: str) -> tuple[int, int, int]:
    """(total, used, free) in bytes of a mounted file system."""
    try:
        u = shutil.disk_usage(mount)
        return u.total, u.used, u.free
    except OSError:
        return 0, 0, 0


def device_of(path: str) -> int:
    """st_dev of a path (to tell whether two paths are on the same file system)."""
    try:
        return os.stat(path).st_dev
    except OSError:
        return -1
