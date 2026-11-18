"""System information helpers (os-release, hardware, live session)."""

from __future__ import annotations

import os
import platform
import shutil
import socket
import subprocess


def os_release(path: str = "/etc/os-release") -> dict[str, str]:
    data: dict[str, str] = {}
    for p in (path, "/usr/lib/os-release"):
        try:
            with open(p, encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if "=" in line and not line.startswith("#"):
                        k, v = line.split("=", 1)
                        data[k] = v.strip().strip('"')
            break
        except OSError:
            continue
    return data


def is_live_session() -> bool:
    """True when running from the NovaOS live medium."""
    try:
        with open("/proc/cmdline", encoding="utf-8") as fh:
            cmdline = fh.read()
    except OSError:
        cmdline = ""
    return "boot=live" in cmdline or os.path.isdir("/run/live/medium")


def cpu_model() -> str:
    try:
        with open("/proc/cpuinfo", encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("model name"):
                    return line.split(":", 1)[1].strip()
    except OSError:
        pass
    return platform.processor() or platform.machine()


def memory_total() -> int:
    try:
        with open("/proc/meminfo", encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("MemTotal:"):
                    return int(line.split()[1]) * 1024
    except OSError:
        pass
    return 0


def gpu_names() -> list[str]:
    if not shutil.which("lspci"):
        return []
    try:
        out = subprocess.run(["lspci"], capture_output=True, text=True, timeout=5).stdout
    except (OSError, subprocess.TimeoutExpired):
        return []
    gpus = []
    for line in out.splitlines():
        if " VGA " in line or "3D controller" in line or "Display controller" in line:
            gpus.append(line.split(": ", 1)[-1].strip())
    return gpus


def plasma_version() -> str:
    for tool in ("plasmashell",):
        if shutil.which(tool):
            try:
                out = subprocess.run([tool, "--version"], capture_output=True, text=True, timeout=5).stdout
                return out.strip().split()[-1]
            except (OSError, subprocess.TimeoutExpired, IndexError):
                pass
    return ""


def disk_root() -> tuple[int, int]:
    try:
        u = shutil.disk_usage("/")
        return u.total, u.free
    except OSError:
        return 0, 0


def summary() -> dict[str, str]:
    from novaos.common.widgets import human_size

    rel = os_release()
    total, free = disk_root()
    return {
        "os": rel.get("PRETTY_NAME", "NovaOS"),
        "base": "Debian " + rel.get("DEBIAN_BASE", "13 (trixie)"),
        "kernel": platform.release(),
        "desktop": ("KDE Plasma " + plasma_version()).strip(),
        "session": os.environ.get("XDG_SESSION_TYPE", "").capitalize() or "–",
        "cpu": cpu_model(),
        "memory": human_size(memory_total()),
        "gpu": ", ".join(gpu_names()) or "–",
        "disk": f"{human_size(free)} / {human_size(total)}",
        "hostname": socket.gethostname(),
    }
