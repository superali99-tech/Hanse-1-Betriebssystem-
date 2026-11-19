"""Generates the NovaOS Mobile icons (vector drawables) and an SVG preview sheet.

Usage: python3 mobile/tools/make_icons.py mobile/res preview.svg
"""
import math, os, sys
RES = sys.argv[1]
PREVIEW = sys.argv[2]
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'

def gear(cx=12, cy=12, r=6.2, teeth=8, tooth=2.6):
    d = [f"M{cx-r:.2f},{cy:.2f}a{r},{r} 0 1,0 {2*r:.2f},0a{r},{r} 0 1,0 {-2*r:.2f},0"]
    for i in range(teeth):
        a = 2 * math.pi * i / teeth
        x1, y1 = cx + r * math.cos(a), cy + r * math.sin(a)
        x2, y2 = cx + (r + tooth) * math.cos(a), cy + (r + tooth) * math.sin(a)
        d.append(f"M{x1:.2f},{y1:.2f}L{x2:.2f},{y2:.2f}")
    d.append(f"M{cx-2.4:.2f},{cy:.2f}a2.4,2.4 0 1,0 4.8,0a2.4,2.4 0 1,0 -4.8,0")
    return " ".join(d)

def circle(cx, cy, r):
    return f"M{cx-r},{cy}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0"

# small UI icons: (name, [(kind, path)]) kind s = stroke, f = fill; 24x24, white
UI = {
    "ic_share": [("s", circle(18, 5.5, 2.5)), ("s", circle(6, 12, 2.5)), ("s", circle(18, 18.5, 2.5)),
                 ("s", "M8.2,10.8L15.8,6.7 M8.2,13.2L15.8,17.3")],
    "ic_delete": [("s", "M4,6.5h16 M9.5,6.5V4h5v2.5 M6.5,6.5l1,13.5h9l1,-13.5 M10.2,10v6.5 M13.8,10v6.5")],
    "ic_folder": [("f", "M3,6.5a1.5,1.5 0 0,1 1.5,-1.5h5l2,2h8a1.5,1.5 0 0,1 1.5,1.5v9.5a1.5,1.5 0 0,1 -1.5,1.5h-15a1.5,1.5 0 0,1 -1.5,-1.5z", "#FFC857")],
    "ic_file": [("s", "M6.5,3h7.5l4.5,4.5v13.5h-12z M14,3v4.5h4.5", "#B8BEDD")],
    "ic_camera_switch": [("s", "M4,8h3l2,-3h6l2,3h3v11H4z"), ("s", "M9,12.5a3,3 0 0,1 5.6,-1.5 M15,13.5a3,3 0 0,1 -5.6,1.5"),
                         ("s", "M14.6,9.2v1.8h-1.8 M9.4,16.8v-1.8h1.8")],
    "ic_flash_on": [("f", "M13,2L5,13.5h6L10,22l8,-11.5h-6z")],
    "ic_flash_off": [("s", "M13,2L5,13.5h6L10,22l8,-11.5h-6z"), ("s", "M3.5,3.5l17,17")],
    "ic_flash_auto": [("f", "M11,2L4,12.5h5L8,21l7,-10.5h-5z"), ("s", "M15.5,21l2.5,-7l2.5,7 M16.4,18.6h3.2", "#FFFFFF", 1.6)],
}

# Nova app icons: name -> (gradient start, end, glyph parts in 24-grid)
APPS = {
    "ic_app_store": ("#8E6CFF", "#3D7BFF", [("s", "M5.5,8.5h13l-1.2,11.5H6.7z"), ("s", "M9,10.5V7.5a3,3 0 0,1 6,0v3")]),
    "ic_app_camera": ("#FF5A7A", "#C2185B", [("s", "M3.5,8h3.5l2,-3h6l2,3h3.5v11.5H3.5z"), ("s", circle(12, 13.5, 3.5))]),
    "ic_app_gallery": ("#FF8A3D", "#F0446B", [("s", "M4,5h16v14H4z"), ("s", "M4,16.5l5,-5l4,4l2.5,-2.5l4.5,4.5"), ("f", circle(15.5, 8.8, 1.6))]),
    "ic_app_files": ("#22C1E8", "#1565C0", [("s", "M3.5,6.5h6l2,2h9v11h-17z")]),
    "ic_app_notes": ("#FFB238", "#FF7A1A", [("s", "M6,3h8.5l4,4v14H6z"), ("s", "M9,11h6.5 M9,14.2h6.5 M9,17.4h4")]),
    "ic_app_settings": ("#6B7AA1", "#2E3756", [("s", gear())]),
}

def ui_xml(parts, size=48):
    out = [f'<?xml version="1.0" encoding="utf-8"?>\n<vector {NS}\n    android:width="{size}dp" android:height="{size}dp" android:viewportWidth="24" android:viewportHeight="24">']
    for p in parts:
        kind, d = p[0], p[1]
        color = p[2] if len(p) > 2 else "#FFFFFF"
        width = p[3] if len(p) > 3 else 2
        if kind == "s":
            out.append(f'    <path android:strokeColor="{color}" android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="{d}"/>')
        else:
            out.append(f'    <path android:fillColor="{color}" android:pathData="{d}"/>')
    out.append("</vector>\n")
    return "\n".join(out)

def glyph_group(parts, scale, offset, indent="    "):
    out = [f'{indent}<group android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{offset}" android:translateY="{offset}">']
    for p in parts:
        kind, d = p[0], p[1]
        if kind == "s":
            out.append(f'{indent}    <path android:strokeColor="#FFFFFF" android:strokeWidth="1.9" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="{d}"/>')
        else:
            out.append(f'{indent}    <path android:fillColor="#FFFFFF" android:pathData="{d}"/>')
    out.append(f"{indent}</group>")
    return "\n".join(out)

def gradient(a, b, x1, y1, x2, y2, indent="        "):
    return (f'{indent}<aapt:attr name="android:fillColor">\n'
            f'{indent}    <gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}" android:startColor="{a}" android:endColor="{b}"/>\n'
            f'{indent}</aapt:attr>')

AAPT = 'xmlns:aapt="http://schemas.android.com/aapt"'

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(text)

for name, parts in UI.items():
    write(f"{RES}/drawable/{name}.xml", ui_xml(parts))

for name, (a, b, parts) in APPS.items():
    base = name.replace("ic_app_", "ic_nova_")
    # adaptive background (108 grid) with gradient
    write(f"{RES}/drawable/{base}_bg.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {NS} {AAPT}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
{gradient(a, b, 0, 0, 108, 108)}
    </path>
</vector>
''')
    # adaptive foreground: glyph 24-grid scaled x1.75 in the 66dp safe zone
    write(f"{RES}/drawable/{base}_fg.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {NS}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
{glyph_group(parts, 1.9, 54 - 12 * 1.9)}
</vector>
''')
    write(f"{RES}/mipmap-anydpi-v26/{base}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon {NS}>
    <background android:drawable="@drawable/{base}_bg" />
    <foreground android:drawable="@drawable/{base}_fg" />
</adaptive-icon>
''')
    # Android 7: round icon with the same glyph
    write(f"{RES}/drawable/{base}_legacy.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {NS} {AAPT}
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:pathData="{circle(24, 24, 22)}">
{gradient(a, b, 2, 2, 46, 46)}
    </path>
{glyph_group(parts, 1.15, 24 - 12 * 1.15)}
</vector>
''')
    write(f"{RES}/mipmap/{base}.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Android 7 (no adaptive icons): round icon -->
<layer-list {NS}>
    <item android:drawable="@drawable/{base}_legacy" />
</layer-list>
''')

# preview sheet
svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="420" style="background:#0B0E1F">', '<defs>']
for i, (name, (a, b, parts)) in enumerate(APPS.items()):
    svg.append(f'<linearGradient id="g{i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient>')
svg.append('</defs>')
def svg_parts(parts, default_w=1.9):
    o = []
    for p in parts:
        kind, d = p[0], p[1]
        color = p[2] if len(p) > 2 else "#FFFFFF"
        width = p[3] if len(p) > 3 else default_w
        if kind == "s":
            o.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>')
        else:
            o.append(f'<path d="{d}" fill="{color}"/>')
    return "".join(o)
for i, (name, (a, b, parts)) in enumerate(APPS.items()):
    x = 30 + i * 160
    svg.append(f'<g transform="translate({x},30)"><circle cx="54" cy="54" r="54" fill="url(#g{i})"/>'
               f'<g transform="translate({54-12*1.75},{54-12*1.75}) scale(1.75)">{svg_parts(parts)}</g></g>')
for i, (name, parts) in enumerate(UI.items()):
    x = 30 + i * 115
    svg.append(f'<g transform="translate({x},230)"><rect width="72" height="72" rx="36" fill="#26FFFFFF"/>'
               f'<g transform="translate(12,12) scale(2)">{svg_parts(parts, 2)}</g></g>')
svg.append('</svg>')
write(PREVIEW, "\n".join(svg))
print("ok")
