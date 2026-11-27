"""NovaOS apps that come as system packages (Nova Browser) in the Nova Store "Updates" page.

An update of such an app appears there like a store app: icon, name, what is new, and
"Aktualisieren" (installed through Nova Updates' helper, pkexec nova-apt-helper install).
What is new comes from the package itself (fields Nova-News / Nova-News-En of the update).
"""

from __future__ import annotations

import os
import subprocess
from dataclasses import dataclass, field

from novaos.updates import apt


@dataclass(frozen=True)
class SystemApp:
    key: str
    name: str
    icon: str
    packages: tuple[str, ...]   # the first one carries the NovaOS news


SYSTEM_APPS = (
    SystemApp("browser", "Nova Browser", "nova-browser", ("novaos-browser", "firefox-esr")),
)


@dataclass
class Pending:
    app: SystemApp
    packages: list[str] = field(default_factory=list)
    version: str = ""
    security: bool = False


def parse_fields(text: str) -> dict[str, str]:
    """The fields of the first stanza of apt-cache show / dpkg control output."""
    out: dict[str, str] = {}
    key = None
    for line in text.splitlines():
        if not line.strip():
            if out:
                break
            continue
        if line[0] in " \t" and key:
            out[key] += "\n" + line.strip()
            continue
        k, sep, v = line.partition(":")
        if sep:
            key = k.strip()
            out[key] = v.strip()
    return out


def pending(upgradable: list[apt.PackageUpdate]) -> list[Pending]:
    """NovaOS apps with an update waiting, from the list of upgradable packages."""
    by_name = {u.name: u for u in upgradable}
    result = []
    for app in SYSTEM_APPS:
        ups = [by_name[p] for p in app.packages if p in by_name]
        if ups:
            result.append(Pending(app, [u.name for u in ups], ups[0].new, any(u.security for u in ups)))
    return result


def parse_policy(text: str) -> tuple[str, str]:
    """(installed, candidate) from `apt-cache policy PKG` (LC_ALL=C); "" for "(none)"."""
    inst = cand = ""
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("Installed:"):
            inst = line.split(":", 1)[1].strip()
        elif line.startswith("Candidate:"):
            cand = line.split(":", 1)[1].strip()
    return ("" if inst == "(none)" else inst), ("" if cand == "(none)" else cand)


def policy(pkg: str) -> tuple[str, str]:
    try:
        res = subprocess.run(["apt-cache", "policy", pkg], capture_output=True, text=True, timeout=30,
                             env=dict(os.environ, LC_ALL="C.UTF-8"))
        return parse_policy(res.stdout)
    except (OSError, subprocess.SubprocessError):
        return "", ""


def with_new_packages(found: list[Pending], policy_of=policy) -> list[Pending]:
    """A NovaOS app whose own package is not installed yet but available (e.g. novaos-browser,
    new in 0.1.1.92) is an update too: installing it brings the new version."""
    have = {p.app.key for p in found}
    for app in SYSTEM_APPS:
        main = app.packages[0]
        installed, candidate = policy_of(main)
        if candidate and not installed:
            if app.key in have:
                p = next(x for x in found if x.app.key == app.key)
                if main not in p.packages:
                    p.packages.insert(0, main)
                    p.version = candidate
            else:
                found.append(Pending(app, [main], candidate, False))
    return found


def news(p: Pending, german: bool) -> str:
    """What is new: from the NovaOS package of the update, otherwise a short default text."""
    main = p.app.packages[0]
    if main in p.packages:
        try:
            res = subprocess.run(["apt-cache", "show", "--no-all-versions", main], capture_output=True, text=True,
                                 timeout=30, env=dict(os.environ, LC_ALL="C.UTF-8"))
            f = parse_fields(res.stdout)
            text = f.get("Nova-News" if german else "Nova-News-En") or f.get("Nova-News", "")
            if text:
                return text
        except (OSError, subprocess.SubprocessError):
            pass
    if p.security:
        return "Sicherheitsupdate" if german else "Security update"
    return "Neue Version" if german else "New version"


def load(german: bool) -> list[tuple[Pending, str]]:
    """Pending NovaOS app updates with their news (runs apt; call it in a worker thread)."""
    try:
        ups = apt.list_upgradable()
    except (OSError, subprocess.SubprocessError):
        return []
    found = with_new_packages(pending(ups))
    # a new NovaOS package may replace files of another one (novaos-browser takes over the browser
    # settings from novaos-desktop): update the other NovaOS packages in the same step
    novaos = [u.name for u in ups if u.name.startswith("novaos-")]
    for p in found:
        p.packages += [n for n in novaos if n not in p.packages]
    return [(p, news(p, german)) for p in found]
