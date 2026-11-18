#!/usr/bin/env python3
"""Generate the NovaOS artwork (SVG sources) and render PNGs.

Everything visual in NovaOS is produced by this script, so the artwork is
reproducible and can be changed in one place:

* artwork/svg/logo/*.svg             NovaOS logo (mark + wordmark)
* artwork/svg/icons/nova-*.svg       app icons (hicolor/scalable)
* artwork/svg/wallpaper-*.svg        "Aurora" wallpaper (light + dark)
* artwork/svg/boot-splash.svg        GRUB / installer background

Rendering needs rsvg-convert (librsvg2-bin). Run:  python3 artwork/generate.py
Output PNGs are written into the package source trees under packages/.
"""

from __future__ import annotations

import math
import os
import random
import shutil
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SVG = os.path.join(HERE, "svg")
PKG = os.path.join(ROOT, "packages")

VIOLET = "#7C5CFF"
BLUE = "#3D7BFF"
CYAN = "#22D3EE"
PINK = "#F472B6"

# --------------------------------------------------------------------- logo
STAR = (
    "M 50 6 C 53 32 68 47 94 50 C 68 53 53 68 50 94 C 47 68 32 53 6 50 "
    "C 32 47 47 32 50 6 Z"
)


def logo_mark(size: int = 256, with_bg: bool = True) -> str:
    bg = (
        f'<rect x="0" y="0" width="100" height="100" rx="26" fill="url(#lg)"/>'
        if with_bg else ""
    )
    star_fill = "#ffffff" if with_bg else "url(#lg)"
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 100 100">
  <defs>
    <linearGradient id="lg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{VIOLET}"/><stop offset="1" stop-color="{BLUE}"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#ffffff" stop-opacity="0.55"/>
      <stop offset="1" stop-color="#ffffff" stop-opacity="0"/>
    </radialGradient>
  </defs>
  {bg}
  {'<circle cx="50" cy="50" r="34" fill="url(#glow)"/>' if with_bg else ''}
  <g transform="translate(50 50) scale(0.72) translate(-50 -50)">
    <path d="{STAR}" fill="{star_fill}"/>
  </g>
  <circle cx="74" cy="27" r="4.2" fill="{star_fill}" opacity="0.9"/>
</svg>
"""


def logo_wordmark(dark_text: bool) -> str:
    color = "#1A1B2E" if dark_text else "#FFFFFF"
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="560" height="140" viewBox="0 0 560 140">
  <defs>
    <linearGradient id="lg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{VIOLET}"/><stop offset="1" stop-color="{BLUE}"/>
    </linearGradient>
  </defs>
  <rect x="10" y="10" width="120" height="120" rx="31" fill="url(#lg)"/>
  <g transform="translate(70 70) scale(0.864) translate(-50 -50)">
    <path d="{STAR}" fill="#fff"/>
  </g>
  <circle cx="98.8" cy="42.4" r="5" fill="#fff"/>
  <text x="160" y="97" font-family="Inter, 'Inter Display', sans-serif" font-size="78" font-weight="700" fill="{color}">Nova<tspan font-weight="300">OS</tspan></text>
</svg>
"""


# -------------------------------------------------------------------- icons
# Glyphs are drawn in a 100x100 box, white on a gradient squircle.
GLYPHS: dict[str, tuple[str, str, str]] = {
    # name: (gradient start, gradient end, svg glyph markup)
    "nova-store": ("#8B5CF6", "#3D7BFF",
                   '<path d="M30 40 h40 l-3 34 a6 6 0 0 1 -6 5 h-22 a6 6 0 0 1 -6 -5 z" fill="#fff"/>'
                   '<path d="M40 42 v-6 a10 10 0 0 1 20 0 v6" fill="none" stroke="#fff" stroke-width="5" stroke-linecap="round"/>'
                   '<path d="M50 52 l2.6 5.4 5.9 0.8 -4.3 4.1 1 5.8 -5.2 -2.8 -5.2 2.8 1 -5.8 -4.3 -4.1 5.9 -0.8z" fill="#7C5CFF"/>'),
    "nova-browser": ("#22D3EE", "#3D7BFF",
                     '<circle cx="50" cy="50" r="27" fill="none" stroke="#fff" stroke-width="5"/>'
                     '<ellipse cx="50" cy="50" rx="11" ry="27" fill="none" stroke="#fff" stroke-width="4"/>'
                     '<path d="M24 50 h52 M28 37 h44 M28 63 h44" stroke="#fff" stroke-width="4"/>'),
    "nova-files": ("#60A5FA", "#2563EB",
                   '<path d="M22 34 a5 5 0 0 1 5 -5 h15 l6 6 h25 a5 5 0 0 1 5 5 v29 a5 5 0 0 1 -5 5 h-46 a5 5 0 0 1 -5 -5 z" fill="#fff"/>'
                   '<path d="M22 44 h56" stroke="#2563EB" stroke-width="3" opacity="0.35"/>'),
    "nova-settings": ("#94A3B8", "#475569",
                      '<g transform="translate(50 50)">'
                      + "".join(f'<rect x="-5.5" y="-30" width="11" height="14" rx="3" fill="#fff" transform="rotate({a})"/>' for a in range(0, 360, 45))
                      + '<circle r="20" fill="#fff"/><circle r="8.5" fill="#64748B"/></g>'),
    "nova-terminal": ("#334155", "#0F172A",
                      '<rect x="20" y="26" width="60" height="48" rx="7" fill="none" stroke="#fff" stroke-width="4" opacity="0.35"/>'
                      '<path d="M31 41 l11 9 -11 9" fill="none" stroke="#4ADE80" stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round"/>'
                      '<path d="M48 60 h20" stroke="#fff" stroke-width="5.5" stroke-linecap="round"/>'),
    "nova-notes": ("#FBBF24", "#F59E0B",
                   '<rect x="27" y="22" width="46" height="56" rx="6" fill="#fff"/>'
                   '<path d="M35 38 h30 M35 48 h30 M35 58 h20" stroke="#F59E0B" stroke-width="4" stroke-linecap="round"/>'),
    "nova-calculator": ("#FB923C", "#EA580C",
                        '<rect x="28" y="20" width="44" height="60" rx="7" fill="#fff"/>'
                        '<rect x="34" y="27" width="32" height="12" rx="3" fill="#EA580C" opacity="0.85"/>'
                        + "".join(f'<circle cx="{38 + 12 * (i % 3)}" cy="{49 + 11 * (i // 3)}" r="3.6" fill="#EA580C"/>' for i in range(9))),
    "nova-media": ("#F472B6", "#DB2777",
                   '<circle cx="50" cy="50" r="27" fill="#fff" opacity="0.25"/>'
                   '<path d="M42 35 l22 15 -22 15 z" fill="#fff" stroke="#fff" stroke-width="4" stroke-linejoin="round"/>'),
    "nova-photos": ("#34D399", "#0EA5E9",
                    '<rect x="21" y="27" width="58" height="46" rx="7" fill="#fff"/>'
                    '<path d="M27 67 l15 -17 10 11 7 -7 14 13 z" fill="#0EA5E9"/>'
                    '<circle cx="61" cy="41" r="5" fill="#F59E0B"/>'),
    "nova-calendar": ("#F87171", "#DC2626",
                      '<rect x="24" y="26" width="52" height="50" rx="7" fill="#fff"/>'
                      '<path d="M24 33 a7 7 0 0 1 7 -7 h38 a7 7 0 0 1 7 7 v7 h-52 z" fill="#B91C1C" opacity="0.9"/>'
                      '<path d="M36 22 v9 M64 22 v9" stroke="#fff" stroke-width="4.5" stroke-linecap="round"/>'
                      '<text x="50" y="67" text-anchor="middle" font-family="Inter, sans-serif" font-weight="700" font-size="22" fill="#DC2626">17</text>'),
    "nova-taskmanager": ("#2DD4BF", "#0D9488",
                         '<rect x="21" y="25" width="58" height="50" rx="7" fill="#fff" opacity="0.22"/>'
                         '<path d="M25 55 h12 l6 -15 9 26 7 -18 5 7 h11" fill="none" stroke="#fff" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>'),
    "nova-updates": ("#A78BFA", "#7C3AED",
                     '<path d="M70 44 a21 21 0 0 0 -38 -8" fill="none" stroke="#fff" stroke-width="5.5" stroke-linecap="round"/>'
                     '<path d="M30 56 a21 21 0 0 0 38 8" fill="none" stroke="#fff" stroke-width="5.5" stroke-linecap="round"/>'
                     '<path d="M28 26 l3 12 12 -3" fill="none" stroke="#fff" stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round"/>'
                     '<path d="M72 74 l-3 -12 -12 3" fill="none" stroke="#fff" stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round"/>'),
    "nova-welcome": ("#7C5CFF", "#22D3EE",
                     f'<g transform="translate(50 50) scale(0.55) translate(-50 -50)"><path d="{STAR}" fill="#fff"/></g>'
                     '<circle cx="70" cy="30" r="3.5" fill="#fff"/><circle cx="30" cy="70" r="2.5" fill="#fff" opacity="0.8"/>'),
    "nova-help": ("#38BDF8", "#0284C7",
                  '<circle cx="50" cy="50" r="27" fill="#fff"/>'
                  '<path d="M41 42 a9 9 0 1 1 13 8 c-3 2 -4 4 -4 7" fill="none" stroke="#0284C7" stroke-width="6" stroke-linecap="round"/>'
                  '<circle cx="50" cy="66" r="3.8" fill="#0284C7"/>'),
    "nova-about": ("#7C5CFF", "#3D7BFF",
                   '<circle cx="50" cy="50" r="27" fill="#fff"/>'
                   '<circle cx="50" cy="37" r="4.2" fill="#5B5CFF"/>'
                   '<path d="M50 47 v18" stroke="#5B5CFF" stroke-width="7" stroke-linecap="round"/>'),
    "novaos-install": ("#7C5CFF", "#3D7BFF",
                       '<rect x="24" y="56" width="52" height="18" rx="5" fill="#fff"/>'
                       '<circle cx="66" cy="65" r="3" fill="#5B5CFF"/>'
                       '<path d="M50 22 v26 M39 38 l11 11 11 -11" fill="none" stroke="#fff" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>'),
}


def app_icon(name: str) -> str:
    c1, c2, glyph = GLYPHS[name]
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 100 100">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/>
    </linearGradient>
    <linearGradient id="shine" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#fff" stop-opacity="0.22"/><stop offset="0.5" stop-color="#fff" stop-opacity="0"/>
    </linearGradient>
  </defs>
  <rect x="6" y="6" width="88" height="88" rx="22" fill="url(#bg)"/>
  <rect x="6" y="6" width="88" height="88" rx="22" fill="url(#shine)"/>
  {glyph}
</svg>
"""


# ---------------------------------------------------------------- wallpaper
def wallpaper(dark: bool, w: int = 3840, h: int = 2160, seed: int = 7) -> str:
    rnd = random.Random(seed)
    if dark:
        bg1, bg2, bg3 = "#070A1A", "#11143A", "#24125A"
        bands = [(VIOLET, 0.55), (BLUE, 0.45), (CYAN, 0.28), (PINK, 0.22)]
    else:
        bg1, bg2, bg3 = "#EEF1FF", "#E3E8FF", "#F3E8FF"
        bands = [(VIOLET, 0.42), (BLUE, 0.34), (CYAN, 0.25), (PINK, 0.20)]
    paths = []
    for i, (color, op) in enumerate(bands):
        y0 = h * (0.30 + 0.13 * i)
        amp = h * (0.10 + 0.04 * i)
        pts = []
        for k in range(7):
            x = w * k / 6
            y = y0 + math.sin(k * 1.15 + i * 0.9) * amp
            pts.append((x, y))
        d = f"M {-w*0.1:.0f} {pts[0][1]:.0f} "
        for k in range(1, len(pts)):
            px, py = pts[k - 1]
            x, y = pts[k]
            d += f"C {px + w/12:.0f} {py:.0f} {x - w/12:.0f} {y:.0f} {x:.0f} {y:.0f} "
        d += f"L {w*1.1:.0f} {h*1.1:.0f} L {-w*0.1:.0f} {h*1.1:.0f} Z"
        paths.append(f'<path d="{d}" fill="url(#band{i})" opacity="{op}" filter="url(#soft)"/>')
    grads = "".join(
        f'<linearGradient id="band{i}" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{c}" stop-opacity="1"/><stop offset="0.55" stop-color="{c}" stop-opacity="0"/></linearGradient>'
        for i, (c, _o) in enumerate(bands)
    )
    stars = ""
    if dark:
        for _ in range(260):
            x, y = rnd.uniform(0, w), rnd.uniform(0, h * 0.75)
            r = rnd.choice([1.2, 1.6, 2.0, 2.6, 3.4])
            stars += f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{r}" fill="#fff" opacity="{rnd.uniform(0.25, 0.9):.2f}"/>'
    glow_col = VIOLET if dark else "#B9A8FF"
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{bg1}"/><stop offset="0.55" stop-color="{bg2}"/><stop offset="1" stop-color="{bg3}"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.72" cy="0.30" r="0.45">
      <stop offset="0" stop-color="{glow_col}" stop-opacity="{0.55 if dark else 0.45}"/>
      <stop offset="1" stop-color="{glow_col}" stop-opacity="0"/>
    </radialGradient>
    {grads}
    <filter id="soft" x="-10%" y="-10%" width="120%" height="120%"><feGaussianBlur stdDeviation="{w/160:.0f}"/></filter>
  </defs>
  <rect width="{w}" height="{h}" fill="url(#sky)"/>
  <rect width="{w}" height="{h}" fill="url(#glow)"/>
  {stars}
  {''.join(paths)}
</svg>
"""


def boot_splash(w: int, h: int) -> str:
    """Background for GRUB and the installer: dark aurora + centred logo."""
    wp = wallpaper(True, w, h, seed=3)
    logo = (
        f'<g transform="translate({w/2 - 60} {h*0.16})">'
        f'<rect width="120" height="120" rx="31" fill="url(#lgo)"/>'
        f'<g transform="translate(60 60) scale(0.864) translate(-50 -50)"><path d="{STAR}" fill="#fff"/></g>'
        f'<circle cx="88.8" cy="32.4" r="5" fill="#fff"/></g>'
        f'<text x="{w/2}" y="{h*0.16 + 175}" text-anchor="middle" font-family="Inter, sans-serif" '
        f'font-size="46" font-weight="700" fill="#fff">Nova<tspan font-weight="300">OS</tspan></text>'
    )
    defs = (f'<linearGradient id="lgo" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{VIOLET}"/>'
            f'<stop offset="1" stop-color="{BLUE}"/></linearGradient>')
    return wp.replace("<defs>", "<defs>" + defs, 1).replace("</svg>", logo + "\n</svg>")


# ------------------------------------------------------------------ helpers
def write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def render(svg: str, png: str, w: int | None = None, h: int | None = None) -> None:
    os.makedirs(os.path.dirname(png), exist_ok=True)
    cmd = ["rsvg-convert", svg, "-o", png]
    if w:
        cmd += ["-w", str(w)]
    if h:
        cmd += ["-h", str(h)]
    subprocess.run(cmd, check=True)


def main() -> None:
    have_rsvg = shutil.which("rsvg-convert") is not None
    # Sources
    write(f"{SVG}/logo/novaos-logo.svg", logo_mark(256))
    write(f"{SVG}/logo/novaos-logo-flat.svg", logo_mark(256, with_bg=False))
    write(f"{SVG}/logo/novaos-wordmark.svg", logo_wordmark(True))
    write(f"{SVG}/logo/novaos-wordmark-light.svg", logo_wordmark(False))
    for name in GLYPHS:
        write(f"{SVG}/icons/{name}.svg", app_icon(name))
    write(f"{SVG}/wallpaper-light.svg", wallpaper(False))
    write(f"{SVG}/wallpaper-dark.svg", wallpaper(True))
    write(f"{SVG}/boot-splash-1024.svg", boot_splash(1024, 768))
    write(f"{SVG}/boot-splash-1920.svg", boot_splash(1920, 1080))

    # Install SVG icons into the desktop package (no rendering needed)
    icon_dir = f"{PKG}/novaos-desktop/root/usr/share/icons/hicolor/scalable/apps"
    os.makedirs(icon_dir, exist_ok=True)
    for name in GLYPHS:
        shutil.copy(f"{SVG}/icons/{name}.svg", f"{icon_dir}/{name}.svg")
    shutil.copy(f"{SVG}/logo/novaos-logo.svg", f"{icon_dir}/novaos.svg")
    shutil.copy(f"{SVG}/logo/novaos-logo.svg", f"{icon_dir}/start-here-novaos.svg")
    # Also make the icons available to the apps when run from the source tree
    src_icons = f"{ROOT}/src/data/icons/hicolor/scalable/apps"
    os.makedirs(src_icons, exist_ok=True)
    for name in GLYPHS:
        shutil.copy(f"{SVG}/icons/{name}.svg", f"{src_icons}/{name}.svg")

    if not have_rsvg:
        print("rsvg-convert not found - SVG sources written, PNG rendering skipped")
        return

    desk = f"{PKG}/novaos-desktop/root/usr/share"
    wp = f"{desk}/wallpapers/NovaAurora/contents"
    render(f"{SVG}/wallpaper-light.svg", f"{wp}/images/3840x2160.png")
    render(f"{SVG}/wallpaper-dark.svg", f"{wp}/images_dark/3840x2160.png")
    render(f"{SVG}/wallpaper-light.svg", f"{wp}/screenshot.png", 400, 225)
    render(f"{SVG}/logo/novaos-logo.svg", f"{desk}/pixmaps/novaos-logo.png", 256, 256)
    render(f"{SVG}/logo/novaos-wordmark-light.svg", f"{desk}/novaos/artwork/novaos-wordmark-light.png", 560, 140)
    render(f"{SVG}/logo/novaos-wordmark.svg", f"{desk}/novaos/artwork/novaos-wordmark.png", 560, 140)
    # Plymouth
    ply = f"{desk}/plymouth/themes/novaos"
    render(f"{SVG}/logo/novaos-wordmark-light.svg", f"{ply}/wordmark.png", 280, 70)
    render(f"{SVG}/logo/novaos-logo.svg", f"{ply}/logo.png", 128, 128)
    # GRUB (installed system) theme background
    render(f"{SVG}/boot-splash-1920.svg", f"{PKG}/novaos-base/root/usr/share/grub/themes/novaos/background.png", 1920, 1080)
    # Live medium boot menu background
    render(f"{SVG}/boot-splash-1024.svg", f"{ROOT}/live-build/config/bootloaders/grub-pc/splash.png", 1024, 768)
    # Installer branding
    cal = f"{PKG}/novaos-installer/root/etc/calamares/branding/novaos"
    render(f"{SVG}/logo/novaos-logo.svg", f"{cal}/logo.png", 128, 128)
    render(f"{SVG}/boot-splash-1024.svg", f"{cal}/welcome.png", 800, 600)
    print("artwork generated")


SLIDES = [
    ("Willkommen bei NovaOS", "Welcome to NovaOS",
     "Schnell, modern und privat – basierend auf Debian 13 und KDE Plasma 6.", "nova-welcome"),
    ("Tausende Apps im Nova Store", "Thousands of apps in Nova Store",
     "LibreOffice, GIMP, Steam, OBS Studio und vieles mehr – sicher in der Sandbox.", "nova-store"),
    ("Immer aktuell und sicher", "Always up to date and secure",
     "Automatische Sicherheitsupdates, Firewall und optional verschlüsselte Festplatte.", "nova-updates"),
    ("Keine Telemetrie", "No telemetry",
     "NovaOS sammelt keine Daten. Ihre Dateien gehören Ihnen.", "nova-about"),
]


def slide(title_de: str, title_en: str, text: str, icon: str, w: int = 800, h: int = 440) -> str:
    c1, c2, glyph = GLYPHS[icon]
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#151a3a"/><stop offset="1" stop-color="#2a1a5e"/>
    </linearGradient>
    <linearGradient id="ib" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/>
    </linearGradient>
  </defs>
  <rect width="{w}" height="{h}" fill="url(#bg)"/>
  <circle cx="{w*0.82}" cy="{h*0.2}" r="{h*0.55}" fill="{VIOLET}" opacity="0.18"/>
  <g transform="translate({w/2-60} 60) scale(1.2)">
    <rect x="6" y="6" width="88" height="88" rx="22" fill="url(#ib)"/>{glyph}
  </g>
  <text x="{w/2}" y="250" text-anchor="middle" font-family="Inter, sans-serif" font-size="34" font-weight="700" fill="#fff">{title_de}</text>
  <text x="{w/2}" y="285" text-anchor="middle" font-family="Inter, sans-serif" font-size="18" fill="#b9b3ff">{title_en}</text>
  <text x="{w/2}" y="340" text-anchor="middle" font-family="Inter, sans-serif" font-size="17" fill="#e6e8f5">{text}</text>
</svg>
"""


def render_extra() -> None:
    cal = f"{PKG}/novaos-installer/root/etc/calamares/branding/novaos"
    for i, (tde, ten, txt, icon) in enumerate(SLIDES, 1):
        svg = f"{SVG}/slide{i}.svg"
        write(svg, slide(tde, ten, txt, icon))
        if shutil.which("rsvg-convert"):
            render(svg, f"{cal}/slide{i}.png")
    # Install icon for the live desktop
    shutil.copy(f"{SVG}/icons/novaos-install.svg", f"{PKG}/novaos-desktop/root/usr/share/icons/hicolor/scalable/apps/novaos-install.svg")


if __name__ == "__main__":
    main()
    render_extra()
