"""Scheduled updates: check every N days, install at the next restart.

Flow (root parts are started by systemd, see src/data/systemd-system/):

* ``novaos-update-check.timer`` (daily) runs ``nova-scheduled-update check``.
  If the last successful check is older than ``INTERVAL_DAYS`` (default 14,
  ``/etc/novaos/updates.conf``) it refreshes the package lists and downloads
  all updates - Debian packages and system-wide Flatpak apps - without
  installing them. It then writes ``STATE_DIR/prepared`` and points
  ``/system-update`` at it.
* At the next boot systemd sees ``/system-update`` and starts
  ``system-update.target`` instead of the desktop.
  ``novaos-offline-update.service`` installs the downloaded updates (no
  network needed, nothing else running), shows the progress on the boot
  splash and reboots into the updated system.
* Every user session runs ``nova-updates --notify`` (systemd user timer and
  path unit). It reads the world-readable state files and asks the user to
  restart ("Update available").

Nothing here sends data anywhere except the normal package downloads from
the configured Debian/Flathub sources.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import time

from novaos.common.i18n import _

CONF = "/etc/novaos/updates.conf"
STATE_DIR = "/var/lib/novaos/updates"
PREPARED = STATE_DIR + "/prepared"
LAST_CHECK = STATE_DIR + "/last-check"
LAST_RESULT = STATE_DIR + "/last-result"
SYSTEM_UPDATE = "/system-update"
DEFAULTS = {"ENABLED": "yes", "INTERVAL_DAYS": "14", "FLATPAK": "yes"}
DAY = 86400
# The daily timer fires at a random time; without some tolerance a check
# would slip to day 15 whenever it ran a little earlier than two weeks ago.
TOLERANCE = 6 * 3600

APT_OPTS = [
    "-y", "-q",
    "-o", "Dpkg::Options::=--force-confdef",
    "-o", "Dpkg::Options::=--force-confold",
    "-o", "Dpkg::Use-Pty=0",
    "-o", "DPkg::Lock::Timeout=600",
]

# apt-get -s full-upgrade:
#   Inst libc6 [2.41-12+deb13u4] (2.41-12+deb13u5 Debian:13.3/stable [amd64])
#   Inst linux-image-6.12.60+deb13-amd64 (6.12.60-1 Debian-Security:13/stable-security [amd64])
INST_RE = re.compile(r"^Inst (?P<name>\S+)(?: \[(?P<old>[^\]]*)\])? \((?P<new>\S+) (?P<origin>[^\[)]*)")


# ------------------------------------------------------------------ helpers
def read_config(path: str | None = None) -> dict[str, str]:
    cfg = dict(DEFAULTS)
    try:
        with open(path or CONF, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, _sep, value = line.partition("=")
                cfg[key.strip().upper()] = value.strip().strip("\"'")
    except OSError:
        pass
    return cfg


def is_yes(value: str) -> bool:
    return value.strip().lower() in ("yes", "true", "on", "1")


def interval_days(cfg: dict[str, str]) -> int:
    try:
        return max(1, min(365, int(cfg.get("INTERVAL_DAYS", "14"))))
    except ValueError:
        return 14


def is_due(last_check: float | None, now: float, days: int) -> bool:
    if last_check is None or last_check > now + DAY:  # never checked / clock was wrong
        return True
    return now - last_check >= days * DAY - TOLERANCE


def parse_simulation(text: str) -> list[dict]:
    out = []
    for line in text.splitlines():
        m = INST_RE.match(line.strip())
        if m:
            origin = m["origin"].strip()
            out.append({"name": m["name"], "old": m["old"] or "", "new": m["new"], "origin": origin,
                        "security": "security" in origin.lower()})
    return out


def read_json(path: str) -> dict | None:
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        return data if isinstance(data, dict) else None
    except (OSError, ValueError):
        return None


def write_json(path: str, data: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    os.chmod(tmp, 0o644)  # every user's notifier reads it
    os.replace(tmp, path)


def read_timestamp(path: str) -> float | None:
    try:
        with open(path, encoding="utf-8") as fh:
            return float(fh.read().strip())
    except (OSError, ValueError):
        return None


def write_timestamp(path: str, value: float) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        fh.write(f"{value:.0f}\n")
    os.chmod(tmp, 0o644)
    os.replace(tmp, path)


# ------------------------------------------------ read-only API (any user)
def pending() -> dict | None:
    """Updates that are downloaded and waiting for a restart."""
    return read_json(PREPARED)


def last_result() -> dict | None:
    return read_json(LAST_RESULT)


def last_check() -> float | None:
    return read_timestamp(LAST_CHECK)


def next_check() -> float | None:
    cfg = read_config()
    if not is_yes(cfg["ENABLED"]):
        return None
    last = last_check()
    return time.time() if last is None else last + interval_days(cfg) * DAY


# ----------------------------------------------------------- root actions
def _env() -> dict[str, str]:
    return dict(os.environ, DEBIAN_FRONTEND="noninteractive", LC_ALL="C.UTF-8",
                PATH="/usr/sbin:/usr/bin:/sbin:/bin")


def _run(cmd: list[str]) -> int:
    print("+", " ".join(cmd), flush=True)
    return subprocess.run(cmd, env=_env()).returncode


def link_system_update() -> None:
    """Make the next boot install the prepared updates."""
    if os.path.lexists(SYSTEM_UPDATE):
        return  # already requested (by us or e.g. fwupd) - our unit runs either way
    os.symlink(PREPARED, SYSTEM_UPDATE)


def clear_prepared() -> None:
    """Forget prepared updates (installed now, or installed by other means)."""
    try:
        if os.path.islink(SYSTEM_UPDATE) and os.readlink(SYSTEM_UPDATE) == PREPARED:
            os.unlink(SYSTEM_UPDATE)
    except OSError:
        pass
    try:
        os.unlink(PREPARED)
    except FileNotFoundError:
        pass


def _system_flatpak_updates() -> list[str]:
    try:
        from novaos.store.flatpak_backend import FlatpakBackend

        return sorted({u.name for u in FlatpakBackend().list_updates() if u.scope == "system"})
    except Exception as exc:  # noqa: BLE001 - no flatpak, offline, ...
        print("flatpak: cannot list updates:", exc, flush=True)
        return []


def check(force: bool = False, install: list[str] | None = None, title: str = "") -> int:
    """Search, download, prepare for the next restart. install: extra packages to put on the
    system at that restart (e.g. ["novaos-2"] for "NovaOS 2 holen"); title for the boot screen."""
    install = list(install or [])
    cfg = read_config()
    if not is_yes(cfg["ENABLED"]) and not force and not install:
        print("automatic update check is disabled in", CONF)
        return 0
    if os.path.exists("/run/live/medium"):
        print("live system - not checking for updates")
        return 0
    os.makedirs(STATE_DIR, mode=0o755, exist_ok=True)
    if os.path.exists(PREPARED) and not install:
        link_system_update()
        print("updates are already downloaded and wait for a restart")
        return 0
    now = time.time()
    days = interval_days(cfg)
    if not force and not is_due(read_timestamp(LAST_CHECK), now, days):
        nxt = time.strftime("%Y-%m-%d", time.localtime(read_timestamp(LAST_CHECK) + days * DAY))
        print(f"next check on {nxt} (every {days} days)")
        return 0

    if _run(["apt-get", "-q", "-o", "DPkg::Lock::Timeout=600", "update"]) != 0:
        print("apt-get update failed (offline?) - trying again tomorrow", flush=True)
        return 1
    sim = subprocess.run(["apt-get", "-s", *APT_OPTS, "full-upgrade"], env=_env(),
                         capture_output=True, text=True)
    if sim.returncode != 0:
        print(sim.stdout, sim.stderr, flush=True)
        return 1
    packages = parse_simulation(sim.stdout)
    if packages and _run(["apt-get", *APT_OPTS, "--download-only", "full-upgrade"]) != 0:
        print("downloading the updates failed - trying again tomorrow", flush=True)
        return 1
    if install:
        sim2 = subprocess.run(["apt-get", "-s", *APT_OPTS, "install", *install], env=_env(), capture_output=True, text=True)
        if sim2.returncode != 0:
            print(sim2.stdout, sim2.stderr, flush=True)
            return 1
        extra = [p for p in parse_simulation(sim2.stdout) if p["name"] not in {q["name"] for q in packages}]
        packages += extra
        if _run(["apt-get", *APT_OPTS, "--download-only", "install", *install]) != 0:
            print("downloading failed", flush=True)
            return 1
    apps: list[str] = []
    if is_yes(cfg["FLATPAK"]) and shutil.which("flatpak"):
        apps = _system_flatpak_updates()
        if apps and _run(["flatpak", "update", "--system", "--no-deploy", "--noninteractive", "-y"]) != 0:
            print("flatpak: download failed - apps are updated next time", flush=True)
            apps = []
    write_timestamp(LAST_CHECK, now)
    if not packages and not apps and not install:
        print("the system is up to date")
        return 0
    write_json(PREPARED, {
        "time": now,
        "count": len(packages) + len(apps),
        "security": sum(1 for p in packages if p["security"]),
        "packages": packages,
        "apps": apps,
        "install": install,
        "title": title,
    })
    link_system_update()
    print(f"{len(packages)} package and {len(apps)} app updates downloaded - installed at the next restart")
    return 0


class Splash:
    """Progress on the Plymouth boot splash (silently does nothing without it)."""

    def __init__(self, title: str = "") -> None:
        self.ok = bool(shutil.which("plymouth")) and \
            subprocess.run(["plymouth", "--ping"], capture_output=True).returncode == 0
        self.last = -1
        self.title = title

    def _cmd(self, *args: str) -> None:
        if self.ok:
            subprocess.run(["plymouth", *args], capture_output=True)

    def message(self, text: str) -> None:
        print(text, flush=True)
        self._cmd("display-message", "--text=" + text)

    def progress(self, pct: int) -> None:
        pct = max(0, min(100, int(pct)))
        if pct == self.last:
            return
        self.last = pct
        self._cmd("system-update", f"--progress={pct}")
        if self.title:
            self.message(_("{title} is being installed – {p} % – please do not turn off the computer").format(
                title=self.title, p=pct))
        else:
            self.message(_("Installing updates – {p} % – please do not turn off the computer").format(p=pct))


def _apt_with_progress(args: list[str], splash: Splash, lo: int, hi: int) -> int:
    r, w = os.pipe()
    proc = subprocess.Popen(["apt-get", *APT_OPTS, "-o", f"APT::Status-Fd={w}", *args], env=_env(), pass_fds=(w,))
    os.close(w)
    with os.fdopen(r, "r", errors="replace") as status:
        for line in status:
            parts = line.rstrip("\n").split(":", 3)
            if len(parts) == 4 and parts[0] == "pmstatus":
                try:
                    splash.progress(lo + (hi - lo) * float(parts[2]) / 100)
                except ValueError:
                    pass
    return proc.wait()


def install_offline() -> int:
    """Runs in system-update.target: install what check() downloaded."""
    # Remove the trigger first: whatever happens, the next boot is a normal one.
    try:
        if os.readlink(SYSTEM_UPDATE) == PREPARED:
            os.unlink(SYSTEM_UPDATE)
    except OSError:
        pass
    info = pending() or {}
    splash = Splash(info.get("title", ""))
    splash.progress(0)
    errors: list[str] = []
    end = 90 if info.get("apps") else 100
    mid = 0
    if info.get("install"):   # e.g. NovaOS 2: the new packages first, then the other updates
        mid = 70 if end == 100 else 60
        rc = _apt_with_progress(["--no-download", "install", *info["install"]], splash, 0, mid)
        if rc != 0:
            errors.append(f"apt-get install {' '.join(info['install'])}: exit code {rc}")
            _run(["dpkg", "--configure", "-a", "--force-confdef", "--force-confold"])
    rc = _apt_with_progress(["--no-download", "--ignore-missing", "full-upgrade"], splash, mid, end)
    if rc != 0:
        errors.append(f"apt-get full-upgrade: exit code {rc}")
        # leave dpkg in a consistent state in any case
        _run(["dpkg", "--configure", "-a", "--force-confdef", "--force-confold"])
    if info.get("apps") and shutil.which("flatpak"):
        if _run(["flatpak", "update", "--system", "--no-pull", "--noninteractive", "-y"]) != 0:
            errors.append("flatpak update failed")
    splash.progress(100)
    write_json(LAST_RESULT, {"time": time.time(), "ok": not errors, "count": info.get("count", 0),
                             "errors": errors})
    try:
        os.unlink(PREPARED)
    except FileNotFoundError:
        pass
    if info.get("title") and not errors:
        splash.message(_("{title} is installed – restarting …").format(title=info["title"]))
    else:
        splash.message(_("Updates installed – restarting …") if not errors else
                       _("Some updates could not be installed – restarting …"))
    time.sleep(3)
    os.sync()
    subprocess.run(["systemctl", "reboot", "--no-block"])
    return 0 if not errors else 1


def status() -> int:
    cfg = read_config()
    fmt = lambda t: time.strftime("%Y-%m-%d %H:%M", time.localtime(t)) if t else "-"  # noqa: E731
    print("enabled:      ", cfg["ENABLED"], f"(every {interval_days(cfg)} days)")
    print("last check:   ", fmt(last_check()))
    print("next check:   ", fmt(next_check()))
    p = pending()
    print("prepared:     ", f"{p['count']} updates, waiting for a restart" if p else "none")
    r = last_result()
    print("last install: ", f"{fmt(r.get('time'))} {'ok' if r.get('ok') else 'FAILED'} {r.get('errors') or ''}"
          if r else "-")
    return 0


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    cmd = args[0] if args else "status"
    if cmd == "status":
        return status()
    if os.geteuid() != 0:
        print("must run as root", file=sys.stderr)
        return 1
    if cmd == "check":
        return check(force="--force" in args)
    if cmd == "offline":
        return install_offline()
    if cmd == "novaos2":
        # "NovaOS 2 holen": download NovaOS 2 and everything else, install it at the next restart
        return check(force=True, install=["novaos-2"], title="NovaOS 2")
    print("usage: nova-scheduled-update [status|check [--force]|offline|novaos2]", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
