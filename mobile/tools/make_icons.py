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
    "ic_mic": [("s", "M12,3.5a3,3 0 0,0 -3,3v5a3,3 0 0,0 6,0v-5a3,3 0 0,0 -3,-3z M5.5,11a6.5,6.5 0 0,0 13,0 M12,17.5v3 M8.5,20.5h7")],
    "ic_mic_off": [("s", "M12,3.5a3,3 0 0,0 -3,3v5a3,3 0 0,0 6,0v-5a3,3 0 0,0 -3,-3z M5.5,11a6.5,6.5 0 0,0 13,0 M12,17.5v3 M8.5,20.5h7"),
                   ("s", "M4,4l16,16")],
    "ic_send": [("f", "M3.4,20.4l17.45,-7.48a1,1 0 0,0 0,-1.84L3.4,3.6a1,1 0 0,0 -1.39,0.91L2,9.12c0,0.5 0.37,0.93 0.87,0.99L17,12 2.87,13.88c-0.5,0.07 -0.87,0.5 -0.87,1l0.01,4.61c0,0.71 0.73,1.2 1.39,0.91z")],
    "ic_call": [("f", "M20.01,15.38c-1.23,0 -2.42,-0.2 -3.53,-0.56c-0.35,-0.12 -0.74,-0.03 -1.01,0.24l-1.57,1.97c-2.83,-1.35 -5.48,-3.9 -6.89,-6.83l1.95,-1.66c0.27,-0.28 0.35,-0.67 0.24,-1.02c-0.37,-1.11 -0.56,-2.3 -0.56,-3.53c0,-0.54 -0.45,-0.99 -0.99,-0.99H4.19C3.65,3 3,3.24 3,3.99C3,13.28 10.73,21 20.01,21c0.71,0 0.99,-0.63 0.99,-1.18v-3.45c0,-0.54 -0.45,-0.99 -0.99,-0.99z")],
    "ic_video": [("s", "M3.5,7h11v10h-11z M14.5,10.5l6,-3.5v10l-6,-3.5")],
    "ic_person_add": [("s", circle(9, 7.5, 3.5)), ("s", "M2.5,20a6.5,6.5 0 0,1 13,0 M19,8v6 M16,11h6")],
    "ic_chat": [("s", "M4,5h16v11H9l-5,4z")],
    "ic_speaker": [("s", "M4,9.5h3.5l5,-4v13l-5,-4H4z M16,9a4,4 0 0,1 0,6 M18.5,6.5a7.5,7.5 0 0,1 0,11")],
}

# Nova app icons: name -> (gradient start, end, glyph parts in 24-grid)
APPS = {
    "ic_app_store": ("#8E6CFF", "#3D7BFF", [("s", "M5.5,8.5h13l-1.2,11.5H6.7z"), ("s", "M9,10.5V7.5a3,3 0 0,1 6,0v3")]),
    "ic_app_camera": ("#FF5A7A", "#C2185B", [("s", "M3.5,8h3.5l2,-3h6l2,3h3.5v11.5H3.5z"), ("s", circle(12, 13.5, 3.5))]),
    "ic_app_gallery": ("#FF8A3D", "#F0446B", [("s", "M4,5h16v14H4z"), ("s", "M4,16.5l5,-5l4,4l2.5,-2.5l4.5,4.5"), ("f", circle(15.5, 8.8, 1.6))]),
    "ic_app_files": ("#22C1E8", "#1565C0", [("s", "M3.5,6.5h6l2,2h9v11h-17z")]),
    "ic_app_notes": ("#FFB238", "#FF7A1A", [("s", "M6,3h8.5l4,4v14H6z"), ("s", "M9,11h6.5 M9,14.2h6.5 M9,17.4h4")]),
    "ic_app_settings": ("#6B7AA1", "#2E3756", [("s", gear())]),
    "ic_app_nova": ("#9B6CFF", "#22C1E8", [("f", "M11,3c0.6,5 3,7.4 8,8c-5,0.6 -7.4,3 -8,8c-0.6,-5 -3,-7.4 -8,-8c5,-0.6 7.4,-3 8,-8z"),
                                           ("f", "M18.5,2.5c0.25,1.6 0.9,2.25 2.5,2.5c-1.6,0.25 -2.25,0.9 -2.5,2.5c-0.25,-1.6 -0.9,-2.25 -2.5,-2.5c1.6,-0.25 2.25,-0.9 2.5,-2.5z")]),
    "ic_app_chat": ("#3DDC97", "#0E9F6E", [("s", "M4,5h16v11H9l-5,4z"), ("f", circle(8.5, 10.5, 1.2)), ("f", circle(12, 10.5, 1.2)), ("f", circle(15.5, 10.5, 1.2))]),
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
svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="560" style="background:#0B0E1F">', '<defs>']
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
