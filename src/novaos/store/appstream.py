"""Parser for AppStream collection metadata (as published by Flathub).

Flatpak downloads the catalog of every remote to
``<installation>/appstream/<remote>/<arch>/active/appstream.xml[.gz]``
together with cached icons. This module turns that XML into ``AppInfo``
objects and offers search and category filtering. It deliberately uses only
the Python standard library so it can be unit tested without Flatpak.
"""

from __future__ import annotations

import gzip
import html
import os
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"

# NovaOS store categories -> freedesktop main/additional categories
CATEGORIES: dict[str, tuple[str, ...]] = {
    "games": ("Game",),
    "office": ("Office",),
    "internet": ("Network", "WebBrowser", "Email", "Chat", "InstantMessaging"),
    "music": ("Audio", "Music"),
    "video": ("Video", "AudioVideo"),
    "graphics": ("Graphics", "Photography"),
    "education": ("Education", "Science"),
    "development": ("Development", "IDE"),
    "system": ("System", "Utility", "Settings"),
}

# Extra search words per category, so that e.g. "büro" finds office apps even
# when an app has no German translation.
CATEGORY_SEARCH_WORDS: dict[str, str] = {
    "games": "spiele spiel games game gaming",
    "office": "büro buero office dokumente documents",
    "internet": "internet netzwerk network web browser chat",
    "music": "musik music audio",
    "video": "video film filme movie",
    "graphics": "grafik graphics bild bilder foto fotos photo bildbearbeitung",
    "education": "bildung education lernen schule wissenschaft science",
    "development": "entwicklung programmieren development programming code",
    "system": "system werkzeuge tools utility",
}

# Curated "recommended" apps for the store front page. Only apps that exist in
# the downloaded catalog are shown, nothing here is hard-coded app data.
RECOMMENDED = (
    "org.mozilla.firefox",
    "org.chromium.Chromium",
    "org.videolan.VLC",
    "org.libreoffice.LibreOffice",
    "org.gimp.GIMP",
    "org.blender.Blender",
    "com.obsproject.Studio",
    "com.valvesoftware.Steam",
    "com.discordapp.Discord",
    "org.mozilla.Thunderbird",
    "org.inkscape.Inkscape",
    "org.kde.krita",
    "org.kde.kdenlive",
    "org.audacityteam.Audacity",
    "org.telegram.desktop",
    "com.github.tchx84.Flatseal",
)


@dataclass
class Screenshot:
    thumbnail: str
    full: str
    caption: str = ""


@dataclass
class AppInfo:
    id: str
    name: str
    summary: str = ""
    description: str = ""          # simple HTML (p, ul, ol, li, em, code)
    developer: str = ""
    license: str = ""
    categories: list[str] = field(default_factory=list)
    keywords: list[str] = field(default_factory=list)
    icon_path: str | None = None
    icon_url: str | None = None
    screenshots: list[Screenshot] = field(default_factory=list)
    version: str = ""
    release_timestamp: int = 0
    release_notes: str = ""
    homepage: str = ""
    bugtracker: str = ""
    donation: str = ""
    verified: bool = False
    kind: str = "desktop-application"
    ref: str = ""                  # e.g. app/org.mozilla.firefox/x86_64/stable
    runtime: str = ""
    origin: str = "flathub"
    content_rating: dict[str, str] = field(default_factory=dict)
    brand_color: str = ""

    @property
    def flatpak_name(self) -> str:
        parts = self.ref.split("/")
        return parts[1] if len(parts) == 4 else self.id

    @property
    def branch(self) -> str:
        parts = self.ref.split("/")
        return parts[3] if len(parts) == 4 else "stable"

    def search_blob(self) -> str:
        if not hasattr(self, "_blob"):
            cats = " ".join(w for c, w in CATEGORY_SEARCH_WORDS.items() if self.in_category(c))
            self._blob = " ".join([self.name, self.id, self.summary, self.developer, " ".join(self.keywords), cats]).lower()
        return self._blob

    def in_category(self, cat: str) -> bool:
        wanted = CATEGORIES.get(cat, ())
        return any(c in wanted for c in self.categories)


def _localized(elems: list[ET.Element], langs: list[str]) -> ET.Element | None:
    by_lang: dict[str, ET.Element] = {}
    for e in elems:
        by_lang.setdefault(e.get(XML_LANG, ""), e)
    for lang in langs:
        if lang in by_lang:
            return by_lang[lang]
    # NB: an Element without children is falsy, so compare against None.
    untranslated = by_lang.get("")
    if untranslated is not None:
        return untranslated
    return by_lang.get("en", elems[0] if elems else None)


def _text(elem: ET.Element | None) -> str:
    return (elem.text or "").strip() if elem is not None else ""


def _description_html(desc: ET.Element | None, langs: list[str]) -> str:
    """Render an AppStream <description> in the preferred language as HTML."""
    if desc is None:
        return ""
    # Group block children by language. Untranslated blocks have no xml:lang.
    blocks_by_lang: dict[str, list[ET.Element]] = {}
    for child in desc:
        blocks_by_lang.setdefault(child.get(XML_LANG, ""), []).append(child)
    blocks = None
    for lang in langs:
        if lang in blocks_by_lang:
            blocks = blocks_by_lang[lang]
            break
    if blocks is None:
        blocks = blocks_by_lang.get("", [])
    out: list[str] = []
    for b in blocks:
        out.append(_render_block(b))
    return "\n".join(o for o in out if o)


def _inline(elem: ET.Element) -> str:
    parts = [html.escape(elem.text or "")]
    for child in elem:
        inner = _inline(child)
        if child.tag in ("em", "code"):
            parts.append(f"<{child.tag}>{inner}</{child.tag}>")
        else:
            parts.append(inner)
        parts.append(html.escape(child.tail or ""))
    return re.sub(r"\s+", " ", "".join(parts)).strip()


def _render_block(b: ET.Element) -> str:
    if b.tag == "p":
        return f"<p>{_inline(b)}</p>"
    if b.tag in ("ul", "ol"):
        items = "".join(f"<li>{_inline(li)}</li>" for li in b if li.tag == "li")
        return f"<{b.tag}>{items}</{b.tag}>"
    return ""


def preferred_languages(lang: str | None = None) -> list[str]:
    """['de_DE', 'de'] style list from a locale like 'de_DE.UTF-8'."""
    lang = lang or os.environ.get("LANGUAGE") or os.environ.get("LC_ALL") or os.environ.get("LANG") or "en"
    lang = lang.split(":")[0].split(".")[0]
    out = [lang]
    if "_" in lang:
        out.append(lang.split("_")[0])
    return [l for l in out if l and l not in ("C", "POSIX")]


def parse_component(c: ET.Element, langs: list[str], icon_dirs: list[str], origin: str) -> AppInfo | None:
    kind = c.get("type", "")
    if kind not in ("desktop-application", "desktop", "console-application"):
        return None
    app_id = _text(c.find("id"))
    if not app_id:
        return None
    bundle = c.find("bundle")
    ref = _text(bundle) if bundle is not None and bundle.get("type") == "flatpak" else ""
    if not ref.startswith("app/"):
        return None

    name = _text(_localized(c.findall("name"), langs)) or app_id
    summary = _text(_localized(c.findall("summary"), langs))
    developer = ""
    dev = c.find("developer")
    if dev is not None:
        developer = _text(_localized(dev.findall("name"), langs))
    if not developer:
        developer = _text(_localized(c.findall("developer_name"), langs))

    info = AppInfo(
        id=app_id,
        name=name,
        summary=summary,
        description=_description_html(_localized(c.findall("description"), langs), langs),
        developer=developer,
        license=_text(c.find("project_license")),
        categories=[_text(x) for x in c.iterfind("categories/category")],
        keywords=[_text(k) for k in c.iterfind("keywords/keyword") if k.get(XML_LANG) in (None, *langs)],
        kind="desktop-application" if kind == "desktop" else kind,
        ref=ref,
        runtime=bundle.get("runtime", "") if bundle is not None else "",
        origin=origin,
    )

    # Icons: prefer the largest cached icon available locally
    best = None
    for icon in c.findall("icon"):
        itype = icon.get("type")
        if itype == "cached":
            size = int(icon.get("width") or 64)
            for d in icon_dirs:
                path = os.path.join(d, f"{size}x{size}", _text(icon))
                if os.path.exists(path) and (best is None or size > best[0]):
                    best = (size, path)
        elif itype == "remote" and not info.icon_url:
            info.icon_url = _text(icon)
    if best:
        info.icon_path = best[1]

    # Screenshots: thumbnail ~624px wide for the gallery, biggest for zoom
    for shot in c.iterfind("screenshots/screenshot"):
        images = shot.findall("image")
        thumbs = [(int(i.get("width") or 0), _text(i)) for i in images if i.get("type") == "thumbnail"]
        source = next((_text(i) for i in images if i.get("type") == "source"), "")
        if not thumbs and not source:
            continue
        thumbs.sort()
        thumb = next((u for w, u in thumbs if w >= 600), thumbs[-1][1] if thumbs else source)
        full = thumbs[-1][1] if thumbs else source
        caption = _text(_localized(shot.findall("caption"), langs))
        if shot.get("type") == "default":
            info.screenshots.insert(0, Screenshot(thumb, full or source, caption))
        else:
            info.screenshots.append(Screenshot(thumb, full or source, caption))

    # Latest release
    releases = c.findall("releases/release")
    if releases:
        latest = max(releases, key=lambda r: int(r.get("timestamp") or 0))
        info.version = latest.get("version", "")
        info.release_timestamp = int(latest.get("timestamp") or 0)
        info.release_notes = _description_html(_localized(latest.findall("description"), langs), langs)

    for url in c.findall("url"):
        t = url.get("type")
        if t == "homepage":
            info.homepage = _text(url)
        elif t == "bugtracker":
            info.bugtracker = _text(url)
        elif t == "donation":
            info.donation = _text(url)

    for value in c.iterfind("custom/value"):
        if value.get("key") == "flathub::verification::verified" and _text(value) == "true":
            info.verified = True

    rating = c.find("content_rating")
    if rating is not None:
        info.content_rating = {a.get("id", ""): _text(a) for a in rating.findall("content_attribute")}

    for color in c.iterfind("branding/color"):
        if color.get("scheme_preference", "light") == "light" and color.get("type") == "primary":
            info.brand_color = _text(color)
            break
    return info


class Catalog:
    """All applications of one or more AppStream collections."""

    def __init__(self) -> None:
        self.apps: dict[str, AppInfo] = {}

    def __len__(self) -> int:
        return len(self.apps)

    def get(self, app_id: str) -> AppInfo | None:
        return self.apps.get(app_id) or self.apps.get(app_id.removesuffix(".desktop"))

    def find_by_flatpak_name(self, name: str) -> AppInfo | None:
        app = self.apps.get(name)
        if app:
            return app
        for a in self.apps.values():
            if a.flatpak_name == name:
                return a
        return None

    def load_file(self, path: str, origin: str = "flathub", langs: list[str] | None = None) -> int:
        """Load an appstream.xml or appstream.xml.gz file. Returns #apps added."""
        langs = langs or preferred_languages()
        base = os.path.dirname(path)
        icon_dirs = [os.path.join(base, "icons")]
        opener = gzip.open if path.endswith(".gz") else open
        added = 0
        with opener(path, "rb") as fh:
            for _event, elem in ET.iterparse(fh, events=("end",)):
                if elem.tag != "component":
                    continue
                info = parse_component(elem, langs, icon_dirs, origin)
                if info and info.id not in self.apps:
                    self.apps[info.id] = info
                    added += 1
                elem.clear()
        return added

    def load_dir(self, active_dir: str, origin: str) -> int:
        for name in ("appstream.xml", "appstream.xml.gz"):
            path = os.path.join(active_dir, name)
            if os.path.exists(path):
                return self.load_file(path, origin)
        return 0

    # ------------------------------------------------------------------ queries
    def search(self, query: str, limit: int = 200) -> list[AppInfo]:
        terms = [t for t in re.split(r"\s+", query.lower().strip()) if t]
        if not terms:
            return []
        scored: list[tuple[float, AppInfo]] = []
        for app in self.apps.values():
            blob = app.search_blob()
            if not all(t in blob for t in terms):
                continue
            name = app.name.lower()
            score = 0.0
            for t in terms:
                if name == t:
                    score += 100
                elif name.startswith(t):
                    score += 60
                elif t in name:
                    score += 35
                if t in app.id.lower():
                    score += 15
                if any(t == k.lower() for k in app.keywords):
                    score += 20
                if t in app.summary.lower():
                    score += 8
            if app.verified:
                score += 5
            if app.kind == "desktop-application":
                score += 2
            scored.append((score, app))
        scored.sort(key=lambda s: (-s[0], s[1].name.lower()))
        return [a for _, a in scored[:limit]]

    def by_category(self, cat: str) -> list[AppInfo]:
        apps = [a for a in self.apps.values() if a.in_category(cat) and a.kind == "desktop-application"]
        apps.sort(key=lambda a: (not a.verified, a.name.lower()))
        return apps

    def recommended(self) -> list[AppInfo]:
        return [self.apps[i] for i in RECOMMENDED if i in self.apps]

    def recently_updated(self, limit: int = 12) -> list[AppInfo]:
        apps = [a for a in self.apps.values() if a.verified and a.screenshots and a.kind == "desktop-application"]
        apps.sort(key=lambda a: -a.release_timestamp)
        return apps[:limit]
