"""Read-only APT queries for Nova Updates (no root needed)."""

from __future__ import annotations

import os
import re
import subprocess
from dataclasses import dataclass

HELPER = "/usr/libexec/novaos/nova-apt-helper"
PERIODIC = "/etc/apt/apt.conf.d/20novaos-periodic"
REBOOT_FLAG = "/run/reboot-required"

LINE_RE = re.compile(r"^(?P<name>[^/\s]+)/(?P<suites>\S+)\s+(?P<new>\S+)\s+(?P<arch>\S+)\s+\[upgradable from: (?P<old>[^\]]+)\]")


@dataclass
class PackageUpdate:
    name: str
    old: str
    new: str
    suites: str
    arch: str

    @property
    def security(self) -> bool:
        return "-security" in self.suites


def parse_upgradable(text: str) -> list[PackageUpdate]:
    out = []
    for line in text.splitlines():
        m = LINE_RE.match(line.strip())
        if m:
            out.append(PackageUpdate(m["name"], m["old"], m["new"], m["suites"], m["arch"]))
    out.sort(key=lambda p: (not p.security, p.name))
    return out


def list_upgradable() -> list[PackageUpdate]:
    env = dict(os.environ, LC_ALL="C.UTF-8")
    res = subprocess.run(["apt", "list", "--upgradable"], capture_output=True, text=True, env=env, timeout=120)
    return parse_upgradable(res.stdout)


def lists_age_seconds() -> float | None:
    """Age of the newest apt list file (when apt-get update last ran)."""
    import time

    path = "/var/lib/apt/lists"
    try:
        newest = max(os.path.getmtime(os.path.join(path, f)) for f in os.listdir(path) if f.endswith(("Release", "InRelease")))
    except (OSError, ValueError):
        return None
    return time.time() - newest


def auto_updates_enabled() -> bool:
    try:
        with open(PERIODIC, encoding="utf-8") as fh:
            return 'Unattended-Upgrade "1"' in fh.read()
    except OSError:
        return False


def reboot_required() -> bool:
    return os.path.exists(REBOOT_FLAG)


def parse_helper_line(line: str) -> tuple[str, int, str]:
    """('progress'|'log'|'error'|'done', percent, text)"""
    line = line.rstrip("\n")
    if line.startswith("PROGRESS "):
        parts = line.split(" ", 2)
        try:
            pct = int(parts[1])
        except (IndexError, ValueError):
            pct = 0
        return "progress", pct, parts[2] if len(parts) > 2 else ""
    if line.startswith("ERROR "):
        return "error", -1, line[6:]
    if line == "DONE":
        return "done", 100, ""
    if line.startswith("LOG "):
        return "log", -1, line[4:]
    return "log", -1, line
