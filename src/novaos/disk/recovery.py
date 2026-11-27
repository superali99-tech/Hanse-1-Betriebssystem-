"""Nova Disk – getting files back: from the trash, and deleted files from drives (PhotoRec).

PhotoRec (package testdisk) reads the drive only and writes what it finds into a folder
that must be on ANOTHER drive - writing onto the drive you rescue from could overwrite the
files you want back. Qt-independent and tested (tests/unit/test_disk.py).
"""

from __future__ import annotations

import os
import re
import shutil
import urllib.parse
from dataclasses import dataclass

# what to look for: PhotoRec file families (the names PhotoRec 7.2 accepts, checked one by one):
# tif also finds camera RAW (CR2, NEF, ARW, DNG), mov finds MP4/M4V/3GP and iPhone photos (HEIC),
# riff finds AVI, WAV and WebP, asf finds WMV/WMA, zip finds DOCX/XLSX/PPTX/ODT/EPUB,
# doc finds old Word/Excel/PowerPoint files
FILE_GROUPS = {
    "photos": ("jpg", "png", "gif", "bmp", "tif", "raw", "riff", "mov"),
    "videos": ("mov", "mkv", "riff", "mpg", "asf", "flv", "m2ts"),
    "documents": ("pdf", "doc", "zip", "txt"),
    "music": ("mp3", "flac", "ogg", "mov", "riff", "asf"),
    "archives": ("zip", "rar", "7z", "gz", "tar"),
}
STOP_FILE = ".nova-disk-stop"   # created in the target folder to stop a running search
DEVICE_RE = re.compile(r"^/dev/(sd[a-z]+\d*|nvme\d+n\d+(p\d+)?|mmcblk\d+(p\d+)?|vd[a-z]+\d*)$")


# ------------------------------------------------------------------ trash
@dataclass
class TrashItem:
    name: str          # name in the trash
    original: str      # where it was
    deleted: str       # "2026-11-27T10:15:00"
    size: int


def trash_dir() -> str:
    base = os.environ.get("XDG_DATA_HOME") or os.path.join(os.path.expanduser("~"), ".local", "share")
    return os.path.join(base, "Trash")


def trash_items(trash: str | None = None) -> list[TrashItem]:
    trash = trash or trash_dir()
    out = []
    info_dir = os.path.join(trash, "info")
    try:
        names = os.listdir(info_dir)
    except OSError:
        return out
    for n in names:
        if not n.endswith(".trashinfo"):
            continue
        name = n[: -len(".trashinfo")]
        orig = deleted = ""
        try:
            with open(os.path.join(info_dir, n), encoding="utf-8", errors="replace") as fh:
                for line in fh:
                    if line.startswith("Path="):
                        orig = urllib.parse.unquote(line[5:].strip())
                    elif line.startswith("DeletionDate="):
                        deleted = line[13:].strip()
        except OSError:
            continue
        f = os.path.join(trash, "files", name)
        if not os.path.lexists(f):
            continue
        out.append(TrashItem(name, orig, deleted, _size(f)))
    return sorted(out, key=lambda t: t.deleted, reverse=True)


def _size(path: str) -> int:
    if os.path.isdir(path) and not os.path.islink(path):
        total = 0
        for dp, _dn, fn in os.walk(path):
            for f in fn:
                try:
                    total += os.lstat(os.path.join(dp, f)).st_size
                except OSError:
                    pass
        return total
    try:
        return os.lstat(path).st_size
    except OSError:
        return 0


def free_name(path: str) -> str:
    """path, or "name (2).ext", "name (3).ext" ... if it exists already."""
    if not os.path.lexists(path):
        return path
    base, ext = os.path.splitext(path)
    i = 2
    while os.path.lexists(f"{base} ({i}){ext}"):
        i += 1
    return f"{base} ({i}){ext}"


def to_trash(path: str, trash: str | None = None, now: str | None = None) -> str:
    """Moves a file of the home folder into the trash (freedesktop.org trash, restorable)."""
    import time as _t
    trash = trash or trash_dir()
    os.makedirs(os.path.join(trash, "files"), exist_ok=True)
    os.makedirs(os.path.join(trash, "info"), exist_ok=True)
    name = os.path.basename(path)
    target = free_name(os.path.join(trash, "files", name))
    name = os.path.basename(target)
    with open(os.path.join(trash, "info", name + ".trashinfo"), "w", encoding="utf-8") as fh:
        fh.write("[Trash Info]\nPath=" + urllib.parse.quote(os.path.abspath(path)) + "\nDeletionDate="
                 + (now or _t.strftime("%Y-%m-%dT%H:%M:%S")) + "\n")
    shutil.move(path, target)
    return target


def restore(item: TrashItem, trash: str | None = None) -> str:
    """Puts the item back where it was (a new name if something is there now). Returns the new path."""
    trash = trash or trash_dir()
    target = free_name(item.original)
    os.makedirs(os.path.dirname(target) or "/", exist_ok=True)
    shutil.move(os.path.join(trash, "files", item.name), target)
    try:
        os.unlink(os.path.join(trash, "info", item.name + ".trashinfo"))
    except FileNotFoundError:
        pass
    return target


# ------------------------------------------------------------------ PhotoRec
def valid_device(path: str) -> bool:
    return bool(DEVICE_RE.match(path or ""))


def photorec_command(source: str, outdir: str, groups: list[str], whole: bool = True) -> list[str]:
    """photorec in batch mode: only the chosen file types, results in outdir/recup_dir.N."""
    exts: list[str] = []
    for g in groups or list(FILE_GROUPS):
        exts += [e for e in FILE_GROUPS.get(g, ()) if e not in exts]
    opts = ["partition_none", "options", "keep_corrupted_file_no", "fileopt", "everything", "disable"]
    for e in exts:
        opts += [e, "enable"]
    opts += ["wholespace" if whole else "freespace", "search"]
    return ["photorec", "/log", "/d", os.path.join(outdir, "recup_dir"), "/cmd", source, ",".join(opts)]


def found_files(outdir: str) -> int:
    """How many files PhotoRec has written so far."""
    n = 0
    try:
        for e in os.scandir(outdir):
            if e.is_dir() and e.name.startswith("recup_dir"):
                n += sum(1 for f in os.scandir(e.path) if f.is_file() and f.name != "report.xml")
    except OSError:
        pass
    return n


def same_device(outdir: str, mounts_of_source: list[str]) -> bool:
    """Is the target folder on a file system of the drive we rescue from? Then writing there
    could overwrite the very files we want back. (Another partition of the same disk is fine.)"""
    try:
        out_dev = os.stat(outdir).st_dev
    except OSError:
        return True   # unknown: better not
    for m in mounts_of_source:
        try:
            if os.stat(m).st_dev == out_dev:
                return True
        except OSError:
            continue
    return False
