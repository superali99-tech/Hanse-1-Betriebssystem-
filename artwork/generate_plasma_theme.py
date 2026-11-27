#!/usr/bin/env python3
"""NovaOS Plasma desktop theme "novaos": a round, glassy dock.

Only two images differ from Breeze (everything else falls back to Breeze, the theme
"default"):

* widgets/panel-background - the taskbar: large rounded corners (a pill for the
  floating dock), glass fill, a fine light edge, a soft shadow and a blur mask of the
  same shape; solid/widgets/panel-background is the opaque variant
* widgets/tasks - behind the app icons: rounded "pills" with a gap to the edge
  instead of Breeze's square blocks (active app: accent colour, running: faint,
  hover: lighter)

Element names and their roles follow libplasma 6.3 (Breeze panel-background.svg,
tasks.svg), plasma-desktop 6.3 (Panel.qml, taskmanager Task.qml/tools.js) and
plasma-workspace 6.3 (panelshadows.cpp: the padding of the shadow is the size of
shadow-hint-*-margin, each tile is an element shadow-*).

Colours come from the colour scheme (classes ColorScheme-*), so light and dark
mode work like Breeze. Run: python3 artwork/generate_plasma_theme.py
"""

from __future__ import annotations

import json
import math
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "packages", "novaos-desktop", "root", "usr", "share", "plasma", "desktoptheme", "novaos")

STYLE = """<style type="text/css" id="current-color-scheme">
  .ColorScheme-Text { color:#232629; stop-color:#232629; }
  .ColorScheme-Background { color:#eff0f1; stop-color:#eff0f1; }
  .ColorScheme-Highlight { color:#3daee9; stop-color:#3daee9; }
  .ColorScheme-NeutralText { color:#f67400; stop-color:#f67400; }
</style>"""

PIN = 'fill="#000000" fill-opacity="0.004" opacity="0.004"'   # invisible, fixes the element's bounds


def fmt(v: float) -> str:
    return f"{v:.3f}".rstrip("0").rstrip(".")


class Sheet:
    """Places frame elements next to each other on one SVG canvas."""

    def __init__(self) -> None:
        self.parts: list[str] = []
        self.defs: list[str] = []
        self.x = 0.0
        self.y = 0.0
        self.row_h = 0.0
        self.width = 0.0

    def place(self, w: float, h: float) -> tuple[float, float]:
        if self.x + w > 600:
            self.x = 0.0
            self.y += self.row_h + 4
            self.row_h = 0.0
        pos = (self.x, self.y)
        self.x += w + 4
        self.row_h = max(self.row_h, h)
        self.width = max(self.width, self.x)
        return pos

    def group(self, eid: str, w: float, h: float, body: str) -> None:
        x, y = self.place(w, h)
        self.parts.append(f'<g id="{eid}" transform="translate({fmt(x)},{fmt(y)})">'
                          f'<rect width="{fmt(w)}" height="{fmt(h)}" {PIN}/>{body}</g>')

    def rect(self, eid: str, w: float, h: float, style: str = 'fill="#ff00ff"') -> None:
        x, y = self.place(max(w, 1), max(h, 1))
        self.parts.append(f'<rect id="{eid}" x="{fmt(x)}" y="{fmt(y)}" width="{w}" height="{h}" {style}/>')

    def svg(self) -> str:
        h = self.y + self.row_h
        return (f'<?xml version="1.0" encoding="UTF-8"?>\n'
                f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
                f'version="1.1" width="{fmt(self.width)}" height="{fmt(h)}" viewBox="0 0 {fmt(self.width)} {fmt(h)}">\n'
                f'<defs>{STYLE}{"".join(self.defs)}</defs>\n' + "\n".join(self.parts) + "\n</svg>\n")


# ------------------------------------------------------------------ shapes
def quarter(corner: str, r: float, size: float, ox: float = 0.0, oy: float = 0.0) -> tuple[float, float, str]:
    """Centre of the arc and the sweep for a corner of a size x size tile whose arc has radius r
    and whose outer edges are ox (horizontal) / oy (vertical) away from the tile border."""
    cx = ox + r if "left" in corner else size - ox - r
    cy = oy + r if "top" in corner else size - oy - r
    return cx, cy, corner


def disk_path(cx: float, cy: float, r: float, corner: str) -> str:
    """The filled quarter disk of a rounded corner (inside of the shape)."""
    sx = -1 if "left" in corner else 1
    sy = -1 if "top" in corner else 1
    x1, y1 = cx + sx * r, cy          # on the horizontal radius
    x2, y2 = cx, cy + sy * r          # on the vertical radius
    sweep = 1 if (sx * sy) > 0 else 0
    return f"M{fmt(cx)},{fmt(cy)} L{fmt(x1)},{fmt(y1)} A{fmt(r)},{fmt(r)} 0 0 {sweep} {fmt(x2)},{fmt(y2)} Z"


def ring_path(cx: float, cy: float, r_out: float, r_in: float, corner: str) -> str:
    """A quarter ring (the edge line of a rounded corner)."""
    sx = -1 if "left" in corner else 1
    sy = -1 if "top" in corner else 1
    sweep = 1 if (sx * sy) > 0 else 0
    a = (cx + sx * r_out, cy)
    b = (cx, cy + sy * r_out)
    c = (cx, cy + sy * r_in)
    d = (cx + sx * r_in, cy)
    return (f"M{fmt(a[0])},{fmt(a[1])} A{fmt(r_out)},{fmt(r_out)} 0 0 {sweep} {fmt(b[0])},{fmt(b[1])} "
            f"L{fmt(c[0])},{fmt(c[1])} A{fmt(r_in)},{fmt(r_in)} 0 0 {1 - sweep} {fmt(d[0])},{fmt(d[1])} Z")


CORNERS = ("topleft", "topright", "bottomleft", "bottomright")
SIDES = ("top", "bottom", "left", "right")


def frame(sheet: Sheet, prefix: str, r: float, gap: float, fill: str, edge: str | None, edge_w: float = 1.0,
          side_len: float = 24.0) -> None:
    """A rounded frame: corner tiles of (gap + r), side tiles of the same thickness, a centre.
    fill / edge: SVG attributes (class + opacity) for the area and the 1 px edge line."""
    p = prefix + "-" if prefix else ""
    t = gap + r
    for c in CORNERS:
        cx, cy, _ = quarter(c, r, t, gap, gap)
        body = f'<path d="{disk_path(cx, cy, r, c)}" {fill}/>'
        if edge:
            body += f'<path d="{ring_path(cx, cy, r, r - edge_w, c)}" {edge}/>'
        sheet.group(p + c, t, t, body)
    for s in SIDES:
        horiz = s in ("top", "bottom")
        w, h = (side_len, t) if horiz else (t, side_len)
        if s == "top":
            area, line = (0, gap, w, r), (0, gap, w, edge_w)
        elif s == "bottom":
            area, line = (0, 0, w, r), (0, r - edge_w, w, edge_w)
        elif s == "left":
            area, line = (gap, 0, r, h), (gap, 0, edge_w, h)
        else:
            area, line = (0, 0, r, h), (r - edge_w, 0, edge_w, h)
        body = (f'<rect x="{fmt(area[0])}" y="{fmt(area[1])}" width="{fmt(area[2])}" height="{fmt(area[3])}" '
                f'{fill}/>')
        if edge:
            body += (f'<rect x="{fmt(line[0])}" y="{fmt(line[1])}" width="{fmt(line[2])}" '
                     f'height="{fmt(line[3])}" {edge}/>')
        sheet.group(p + s, w, h, body)
    sheet.group(p + "center", side_len, side_len, f'<rect width="{side_len}" height="{side_len}" {fill}/>')


def margins(sheet: Sheet, prefix: str, top: float, bottom: float, left: float, right: float) -> None:
    p = prefix + "-" if prefix else ""
    sheet.rect(p + "hint-top-margin", 4, top)
    sheet.rect(p + "hint-bottom-margin", 4, bottom)
    sheet.rect(p + "hint-left-margin", left, 4)
    sheet.rect(p + "hint-right-margin", right, 4)


# ------------------------------------------------------------------ panel
PANEL_R = 22          # corner radius of the taskbar (nearly a pill at the NovaOS dock height of 58 px)
SHADOW = 16           # how far the soft shadow reaches
SHADOW_STOPS = ((0.0, 0.30), (0.2, 0.19), (0.45, 0.09), (0.7, 0.03), (1.0, 0.0))


def shadow(sheet: Sheet) -> None:
    r, s = PANEL_R, SHADOW
    t = r + s
    stops = "".join(f'<stop offset="{fmt(o)}" stop-color="#000000" stop-opacity="{fmt(a)}"/>' for o, a in SHADOW_STOPS)
    # corners: radial gradient from the arc of the panel outwards
    for c in CORNERS:
        cx = t if "left" in c else 0.0
        cy = t if "top" in c else 0.0
        gid = "sg-" + c
        rstops = "".join(f'<stop offset="{fmt((r + o * s) / t)}" stop-color="#000000" stop-opacity="{fmt(a)}"/>'
                         for o, a in SHADOW_STOPS)
        sheet.defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{fmt(cx)}" cy="{fmt(cy)}" '
                          f'r="{fmt(t)}"><stop offset="0" stop-color="#000000" stop-opacity="0"/>'
                          f'<stop offset="{fmt(r / t - 0.0001)}" stop-color="#000000" stop-opacity="0"/>{rstops}'
                          f'</radialGradient>')
        sheet.group("shadow-" + c, t, t, f'<path d="{ring_path(cx, cy, t, r, c)}" fill="url(#{gid})"/>')
    # sides: linear gradient over the outer SHADOW px, transparent where the panel is
    for side in SIDES:
        horiz = side in ("top", "bottom")
        w, h = (24, t) if horiz else (t, 24)
        gid = "sg-" + side
        if side == "top":
            x1, y1, x2, y2, area = 0, s, 0, 0, (0, 0, w, s)
        elif side == "bottom":
            x1, y1, x2, y2, area = 0, r, 0, t, (0, r, w, s)
        elif side == "left":
            x1, y1, x2, y2, area = s, 0, 0, 0, (0, 0, s, h)
        else:
            x1, y1, x2, y2, area = r, 0, t, 0, (r, 0, s, h)
        sheet.defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" '
                          f'x2="{x2}" y2="{y2}">{stops}</linearGradient>')
        sheet.group("shadow-" + side, w, h, f'<rect x="{area[0]}" y="{area[1]}" width="{area[2]}" '
                                            f'height="{area[3]}" fill="url(#{gid})"/>')
    sheet.group("shadow-center", 24, 24, "")
    for side in SIDES:
        horiz = side in ("top", "bottom")
        sheet.rect(f"shadow-hint-{side}-margin", 2 if horiz else s, s if horiz else 2)


def panel(opaque: bool) -> str:
    sh = Sheet()
    fill = 'class="ColorScheme-Background" fill="currentColor" ' + ('' if opaque else 'fill-opacity="0.78"')
    edge = 'class="ColorScheme-Text" fill="currentColor" fill-opacity="0.12"'
    frame(sh, "", PANEL_R, 0, fill, edge)
    margins(sh, "", 4, 4, 12, 12)
    for side in SIDES:   # no inset (like Breeze)
        horiz = side in ("top", "bottom")
        sh.rect(f"hint-{side}-inset", 4 if horiz else 1e-8, 1e-8 if horiz else 4, 'fill="#00ff00"')
    # blur / contrast region: the same rounded shape
    frame(sh, "mask", PANEL_R, 0, 'fill="#000000"', None)
    shadow(sh)
    # thick panels (Panel.qml asks for "thick" margins), as Breeze
    sh.group("thick-center", 32, 32, '<rect width="32" height="32" class="ColorScheme-Background" fill="currentColor"/>')
    margins(sh, "thick", 8, 8, 8, 8)
    sh.rect("hint-tile-center", 5, 5, 'fill="#ff6600"')
    return sh.svg()


# ------------------------------------------------------------------ tasks
TASK_R = 13           # radius of the pill behind an app icon
TASK_GAP = 2          # space between the pill and the edge of the task


def tasks() -> str:
    sh = Sheet()
    looks = {
        "normal": ("ColorScheme-Text", 0.07, None),
        "hover": ("ColorScheme-Text", 0.15, ("ColorScheme-Text", 0.10)),
        "focus": ("ColorScheme-Highlight", 0.30, ("ColorScheme-Highlight", 0.65)),
        "focus-hover": ("ColorScheme-Highlight", 0.40, ("ColorScheme-Highlight", 0.80)),
        "minimized": ("ColorScheme-Text", 0.04, None),
        "attention": ("ColorScheme-NeutralText", 0.32, ("ColorScheme-NeutralText", 0.70)),
        "progress": ("ColorScheme-Highlight", 0.28, None),
    }
    for name, (cls, alpha, edge) in looks.items():
        fill = f'class="{cls}" fill="currentColor" fill-opacity="{alpha}"'
        e = f'class="{edge[0]}" fill="currentColor" fill-opacity="{edge[1]}"' if edge else None
        frame(sh, name, TASK_R, TASK_GAP, fill, e, side_len=20)
    # icon padding inside a task (the task manager reads it from "normal"): the pill is
    # 4 px larger than the icon on every side
    margins(sh, "normal", 6, 6, 6, 6)
    # badge on grouped tasks: a small accent circle with a plus
    badge = ('<circle cx="5.5" cy="5.5" r="5.5" fill="#000000" fill-opacity="0.25"/>'
             '<circle cx="5.5" cy="5.5" r="4.6" class="ColorScheme-Highlight" fill="currentColor"/>'
             '<path d="M5,3 h1 v2 h2 v1 h-2 v2 h-1 v-2 h-2 v-1 h2 z" fill="#ffffff"/>')
    for side in SIDES:
        sh.group("group-expander-" + side, 11, 11, badge)
    return sh.svg()


# ------------------------------------------------------------------ files
METADATA = {
    "KPlugin": {
        "Authors": [{"Name": "NovaOS Project"}],
        "Category": "",
        "Description": "Round glass dock for NovaOS (based on Breeze)",
        "Description[de]": "Runde Glas-Taskleiste für NovaOS (auf Basis von Breeze)",
        "EnabledByDefault": True,
        "Id": "novaos",
        "License": "LGPL",
        "Name": "NovaOS",
        "Version": "2.0",
        "Website": "https://github.com/superali99-tech/Hanse-1-Betriebssystem-",
    },
    "X-Plasma-API": "5.0",
}

PLASMARC = """[Settings]
FallbackTheme=default
FallbackImageSet=default

[ContrastEffect]
enabled=true
contrast=0.2
saturation=10

[AdaptiveTransparency]
enabled=true
"""


def write(rel: str, text: str) -> None:
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    print("wrote", os.path.relpath(path, ROOT))


def main() -> None:
    write("metadata.json", json.dumps(METADATA, indent=4, ensure_ascii=False) + "\n")
    write("plasmarc", PLASMARC)
    write("widgets/panel-background.svg", panel(opaque=False))
    write("solid/widgets/panel-background.svg", panel(opaque=True))
    write("widgets/tasks.svg", tasks())


if __name__ == "__main__":
    main()
