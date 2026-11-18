"""Human readable view of a Flatpak app's sandbox permissions.

The permissions come from the app's ``metadata`` file (the ``[Context]``
group written from the manifest's ``finish-args``). Nova Store fetches that
file from the remote *before* installation and shows the result to the user.
"""

from __future__ import annotations

import configparser
from dataclasses import dataclass

from novaos.common.i18n import _

LOW, MEDIUM, HIGH = 0, 1, 2


@dataclass(frozen=True)
class Permission:
    level: int
    title: str
    detail: str
    icon: str = "security-medium"


def parse_metadata(text: str) -> configparser.ConfigParser:
    cp = configparser.ConfigParser(interpolation=None, strict=False, delimiters=("=",))
    cp.optionxform = str  # keep case of D-Bus names
    cp.read_string(text)
    return cp


def _split(value: str) -> list[str]:
    return [v.strip() for v in value.split(";") if v.strip()]


def _filesystem_permission(entry: str) -> Permission | None:
    ro = entry.endswith(":ro")
    create = entry.endswith(":create")
    path = entry.rsplit(":", 1)[0] if (ro or create or entry.endswith(":rw")) else entry
    if path.startswith("!"):
        return None  # explicit denial
    mode = _("read-only") if ro else _("read/write")
    if path in ("host", "host-os", "host-etc", "/"):
        names = {
            "host": _("all files on the computer"),
            "host-os": _("operating system files"),
            "host-etc": _("system configuration (/etc)"),
            "/": _("all files on the computer"),
        }
        return Permission(HIGH if not ro else MEDIUM, _("File system access"), f"{names[path]} ({mode})", "drive-harddisk")
    if path in ("home", "~"):
        return Permission(HIGH if not ro else MEDIUM, _("Home folder"), _("all your personal files") + f" ({mode})", "user-home")
    if path.startswith("xdg-"):
        folder = path.split("/")[0].removeprefix("xdg-")
        names = {
            "download": _("Downloads"), "documents": _("Documents"), "music": _("Music"),
            "pictures": _("Pictures"), "videos": _("Videos"), "desktop": _("Desktop"),
            "config": _("app configuration"), "data": _("app data"), "cache": _("app cache"),
            "run": _("runtime directory"), "public-share": _("Public folder"), "templates": _("Templates"),
        }
        return Permission(LOW if ro else MEDIUM, _("Folder access"), f"{names.get(folder, path)} ({mode})", "folder")
    if path.startswith(("/media", "/run/media", "/mnt")):
        return Permission(MEDIUM, _("Removable drives"), f"{path} ({mode})", "drive-removable-media")
    return Permission(MEDIUM if not ro else LOW, _("Folder access"), f"{path} ({mode})", "folder")


def analyze(metadata_text: str) -> list[Permission]:
    """Return permissions sorted from most to least sensitive."""
    if not metadata_text.strip():
        return []
    cp = parse_metadata(metadata_text)
    perms: list[Permission] = []
    if cp.has_section("Context"):
        ctx = cp["Context"]
        shared = _split(ctx.get("shared", ""))
        sockets = _split(ctx.get("sockets", ""))
        devices = _split(ctx.get("devices", ""))
        features = _split(ctx.get("features", ""))
        filesystems = _split(ctx.get("filesystems", ""))

        if "network" in shared:
            perms.append(Permission(MEDIUM, _("Network"), _("can access the internet and the local network"), "network-wired"))
        if "x11" in sockets and "wayland" not in sockets:
            perms.append(Permission(HIGH, _("Legacy display system (X11)"),
                                    _("can read keyboard input and screen content of other X11 apps"), "video-display"))
        elif "x11" in sockets or "fallback-x11" in sockets:
            perms.append(Permission(LOW, _("Display"), _("uses Wayland, X11 only as fallback"), "video-display"))
        elif "wayland" in sockets:
            perms.append(Permission(LOW, _("Display"), _("uses Wayland (isolated windows)"), "video-display"))
        if "pulseaudio" in sockets:
            perms.append(Permission(MEDIUM, _("Sound and microphone"), _("can play sound and record from the microphone"), "audio-input-microphone"))
        if "system-bus" in sockets:
            perms.append(Permission(HIGH, _("System services"), _("unrestricted access to system D-Bus services"), "preferences-system"))
        if "session-bus" in sockets:
            perms.append(Permission(HIGH, _("Session services"), _("unrestricted access to all desktop services"), "preferences-desktop"))
        if "ssh-auth" in sockets:
            perms.append(Permission(MEDIUM, _("SSH keys"), _("can use your SSH agent"), "dialog-password"))
        if "gpg-agent" in sockets:
            perms.append(Permission(MEDIUM, _("GPG keys"), _("can use your GPG agent"), "dialog-password"))
        if "pcsc" in sockets:
            perms.append(Permission(MEDIUM, _("Smart cards"), _("can use smart card readers"), "auth-smartcard"))
        if "cups" in sockets:
            perms.append(Permission(LOW, _("Printing"), _("can print directly"), "printer"))
        if "all" in devices:
            perms.append(Permission(HIGH, _("All devices"), _("e.g. webcams, USB devices, game controllers"), "camera-web"))
        else:
            if "dri" in devices:
                perms.append(Permission(LOW, _("Graphics acceleration"), _("uses the GPU (OpenGL/Vulkan)"), "video-display"))
            if "kvm" in devices:
                perms.append(Permission(MEDIUM, _("Virtualisation"), _("can run virtual machines (KVM)"), "computer"))
            if "input" in devices:
                perms.append(Permission(MEDIUM, _("Input devices"), _("e.g. game controllers"), "input-gaming"))
            if "usb" in devices:
                perms.append(Permission(MEDIUM, _("USB devices"), _("direct access to USB devices"), "drive-removable-media"))
        if "bluetooth" in features:
            perms.append(Permission(MEDIUM, _("Bluetooth"), _("can use Bluetooth devices"), "network-bluetooth"))
        if "devel" in features:
            perms.append(Permission(HIGH, _("Developer features"), _("can debug other processes (ptrace)"), "applications-development"))
        for fs in filesystems:
            p = _filesystem_permission(fs)
            if p:
                perms.append(p)

    for section, label in (("Session Bus Policy", _("Desktop service")), ("System Bus Policy", _("System service"))):
        if cp.has_section(section):
            for name, policy in cp[section].items():
                if policy == "own":
                    continue
                if name.startswith("org.freedesktop.Flatpak") or name == "org.freedesktop.secrets":
                    level = HIGH if name == "org.freedesktop.Flatpak" else MEDIUM
                else:
                    level = LOW
                if name in ("org.freedesktop.Notifications", "org.kde.StatusNotifierWatcher", "com.canonical.AppMenu.Registrar"):
                    level = LOW
                perms.append(Permission(level, label, f"{name} ({policy})", "preferences-system"))

    perms.sort(key=lambda p: -p.level)
    return perms


def summary(perms: list[Permission]) -> tuple[int, str]:
    """Overall rating used for the badge on the details page."""
    if not perms:
        return LOW, _("Sandboxed: no special permissions")
    worst = max(p.level for p in perms)
    if worst == HIGH:
        return HIGH, _("Extended permissions: this app can access sensitive data")
    if worst == MEDIUM:
        return MEDIUM, _("Some permissions: network, folders or devices")
    return LOW, _("Well sandboxed")
