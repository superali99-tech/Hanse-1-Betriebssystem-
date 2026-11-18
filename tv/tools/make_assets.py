#!/usr/bin/env python3
"""Render the NovaOS TV graphics from the shared NovaOS artwork.

Uses artwork/generate.py (same logo and "Aurora" wallpaper as the desktop),
rsvg-convert and Pillow; output goes to tv/res/.
Run after changing the artwork:  python3 tv/tools/make_assets.py
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TV = os.path.dirname(HERE)
ROOT = os.path.dirname(TV)
RES = os.path.join(TV, "res")
sys.path.insert(0, os.path.join(ROOT, "artwork"))
import generate as art  # noqa: E402

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def render(svg_text: str, out_png: str, w: int, h: int | None = None) -> None:
    with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as fh:
        fh.write(svg_text)
        src = fh.name
    try:
        cmd = ["rsvg-convert", src, "-o", out_png, "-w", str(w)]
        if h:
            cmd += ["-h", str(h)]
        subprocess.run(cmd, check=True)
    finally:
        os.unlink(src)


def foreground(size: int) -> str:
    """Adaptive icon foreground: white star inside the 66/108 safe zone."""
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">
  <g transform="translate(54 54) scale(0.50) translate(-50 -50)"><path d="{art.STAR}" fill="#ffffff"/></g>
  <circle cx="70" cy="37" r="3.2" fill="#ffffff" opacity="0.9"/>
</svg>"""


def banner() -> str:
    """TV banner (Google/Android TV app row): aurora, logo, "NovaOS TV".

    Drawn on a 1280x720 canvas (so the wallpaper's stars stay small) and
    scaled down when rendering."""
    wp = art.wallpaper(True, 1280, 720, seed=5)
    s = 720 / 180
    logo = (
        f'<g transform="translate({28*s:.1f} {50*s:.1f}) scale({0.80*s:.3f})">'
        f'<rect width="100" height="100" rx="26" fill="url(#lgo)"/>'
        f'<g transform="translate(50 50) scale(0.72) translate(-50 -50)"><path d="{art.STAR}" fill="#fff"/></g>'
        f'<circle cx="74" cy="27" r="4.2" fill="#fff"/></g>'
        f'<text x="{120*s:.1f}" y="{104*s:.1f}" font-family="Inter, DejaVu Sans, sans-serif" font-size="{40*s:.1f}" '
        f'font-weight="700" fill="#fff">Nova<tspan font-weight="300">OS</tspan></text>'
        f'<text x="{122*s:.1f}" y="{136*s:.1f}" font-family="Inter, DejaVu Sans, sans-serif" font-size="{22*s:.1f}" '
        f'font-weight="600" letter-spacing="{4*s:.1f}" fill="#C9D2FF">TV</text>'
    )
    defs = (f'<linearGradient id="lgo" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{art.VIOLET}"/>'
            f'<stop offset="1" stop-color="{art.BLUE}"/></linearGradient>')
    return wp.replace("<defs>", "<defs>" + defs, 1).replace("</svg>", logo + "\n</svg>")


def main() -> None:
    for name, scale in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        render(art.logo_mark(256, True), os.path.join(d, "ic_launcher.png"), int(48 * scale))
        render(foreground(432), os.path.join(d, "ic_launcher_foreground.png"), int(108 * scale))
    for name, scale in (("xhdpi", 1), ("xxhdpi", 1.5)):
        d = os.path.join(RES, f"drawable-{name}")
        os.makedirs(d, exist_ok=True)
        render(banner(), os.path.join(d, "banner.png"), int(320 * scale), int(180 * scale))
    nodpi = os.path.join(RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    render(art.logo_mark(256, True), os.path.join(nodpi, "nova_logo.png"), 192)
    tmp = os.path.join(tempfile.gettempdir(), "novaos-tv-wallpaper.png")
    render(art.wallpaper(True, 1920, 1080, seed=7), tmp, 1920, 1080)
    Image.open(tmp).convert("RGB").save(os.path.join(nodpi, "wallpaper.jpg"), quality=86, optimize=True)
    os.unlink(tmp)
    print("assets written to", RES)


if __name__ == "__main__":
    main()
