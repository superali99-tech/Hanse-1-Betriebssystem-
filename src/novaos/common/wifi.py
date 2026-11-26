"""WLAN for Nova WLAN and Nova Welcome: list the networks, connect with just the password.

Uses NetworkManager's command line tool ``nmcli`` (installed with NovaOS). A user of the
active local session may connect without an administrator password. The parsing is
Android/Qt-independent and tested (tests/unit/test_wifi.py).
"""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass

NMCLI = "nmcli"
ENV = {"LC_ALL": "C", "PATH": "/usr/sbin:/usr/bin:/sbin:/bin"}


@dataclass
class Network:
    ssid: str
    signal: int          # 0..100
    secured: bool
    in_use: bool
    security: str = ""

    @property
    def bars(self) -> int:
        """0..4 bars like on a phone."""
        return 0 if self.signal <= 0 else min(4, 1 + self.signal // 25)


def split_terse(line: str) -> list[str]:
    r"""One line of ``nmcli -t``: fields separated by ':', a ':' inside a field is '\:'."""
    fields, cur, esc = [], [], False
    for ch in line:
        if esc:
            cur.append(ch)
            esc = False
        elif ch == "\\":
            esc = True
        elif ch == ":":
            fields.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    fields.append("".join(cur))
    return fields


def parse_networks(text: str) -> list[Network]:
    """``nmcli -t -f IN-USE,SSID,SIGNAL,SECURITY device wifi list`` -> networks.

    Hidden networks (no name) are left out; a name seen on several access points
    appears once with its strongest signal. Connected first, then the strongest.
    """
    best: dict[str, Network] = {}
    for line in text.splitlines():
        if not line.strip():
            continue
        f = split_terse(line)
        if len(f) < 4:
            continue
        in_use, ssid, signal, security = f[0].strip() == "*", f[1], f[2], f[3].strip()
        if not ssid.strip():
            continue
        try:
            sig = max(0, min(100, int(signal)))
        except ValueError:
            sig = 0
        sec = security not in ("", "--")
        net = Network(ssid, sig, sec, in_use, security if sec else "")
        old = best.get(ssid)
        if old is None:
            best[ssid] = net
        else:
            old.in_use = old.in_use or in_use
            if sig > old.signal:
                old.signal, old.secured, old.security = sig, sec, net.security
    return sorted(best.values(), key=lambda n: (not n.in_use, -n.signal, n.ssid.lower()))


def parse_connectivity(text: str) -> str:
    """``nmcli -t -f CONNECTIVITY general`` -> full / limited / portal / none / unknown."""
    value = text.strip().splitlines()[0].strip() if text.strip() else ""
    return value if value in ("full", "limited", "portal", "none") else "unknown"


def parse_wifi_devices(text: str) -> list[str]:
    """``nmcli -t -f DEVICE,TYPE device`` -> names of the WLAN devices."""
    out = []
    for line in text.splitlines():
        f = split_terse(line)
        if len(f) >= 2 and f[1] == "wifi":
            out.append(f[0])
    return out


def error_message(stderr: str) -> str:
    """nmcli's error as a short reason: 'password' (wrong/missing), 'not_found', or the text."""
    s = stderr.lower()
    if any(k in s for k in ("secrets were required", "psk: property is invalid", "no secrets",
                            "invalid password", "wrong password")):
        return "password"
    if "no network with ssid" in s:
        return "not_found"
    return stderr.strip().replace("Error: ", "")


# ------------------------------------------------------------------ nmcli calls
def available() -> bool:
    return shutil.which(NMCLI) is not None


def _run(args: list[str], timeout: int = 40) -> subprocess.CompletedProcess:
    return subprocess.run([NMCLI, *args], capture_output=True, text=True, timeout=timeout, env=ENV)


def wifi_devices() -> list[str]:
    try:
        return parse_wifi_devices(_run(["-t", "-f", "DEVICE,TYPE", "device"]).stdout)
    except (OSError, subprocess.SubprocessError):
        return []


def connectivity() -> str:
    try:
        return parse_connectivity(_run(["-t", "-f", "CONNECTIVITY", "general"]).stdout)
    except (OSError, subprocess.SubprocessError):
        return "unknown"


def scan(rescan: bool = True) -> list[Network]:
    try:
        _run(["radio", "wifi", "on"])
        res = _run(["-t", "-f", "IN-USE,SSID,SIGNAL,SECURITY", "device", "wifi", "list",
                    "--rescan", "yes" if rescan else "auto"])
        return parse_networks(res.stdout)
    except (OSError, subprocess.SubprocessError):
        return []


def connect(ssid: str, password: str | None) -> tuple[bool, str]:
    """Connects (NetworkManager saves the network and reconnects by itself next time)."""
    args = ["device", "wifi", "connect", ssid]
    if password:
        args += ["password", password]
    try:
        res = _run(args, timeout=90)
    except subprocess.TimeoutExpired:
        return False, "timeout"
    except OSError as exc:
        return False, str(exc)
    if res.returncode == 0:
        return True, ""
    return False, error_message(res.stderr or res.stdout)
