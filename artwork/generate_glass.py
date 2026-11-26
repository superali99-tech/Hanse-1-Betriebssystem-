#!/usr/bin/env python3
"""Glass design artwork of NovaOS (PC): login background and the glow of the boot screen.

Derived from the NovaAurora wallpaper; run from the repository root:
    python3 artwork/generate_glass.py
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESK = os.path.join(ROOT, "packages", "novaos-desktop", "root", "usr", "share")
WALL = os.path.join(DESK, "wallpapers", "NovaAurora", "contents", "images_dark", "3840x2160.png")


def login_background() -> None:
    """The wallpaper, softly blurred and a little darker towards the edges: a calm
    background for the glass login card (SDDM) - 2560x1440 JPEG."""
    im = Image.open(WALL).convert("RGB").resize((2560, 1440), Image.LANCZOS)
    im = im.filter(ImageFilter.GaussianBlur(26))
    w, h = im.size
    shade = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(shade)
    # vignette: darker at the edges, untouched in the middle
    for i in range(60):
        a = int(150 * (i / 60) ** 2)
        inset_x, inset_y = int(w * 0.5 * (1 - i / 60)), int(h * 0.5 * (1 - i / 60))
        d.rectangle((0, 0, w, h), outline=None)
        d.ellipse((w / 2 - inset_x * 1.6, h / 2 - inset_y * 1.6, w / 2 + inset_x * 1.6, h / 2 + inset_y * 1.6),
                  outline=a, width=max(1, int(w / 90)))
    shade = shade.filter(ImageFilter.GaussianBlur(60))
    dark = Image.new("RGB", (w, h), (6, 8, 22))
    im = Image.composite(dark, im, shade.point(lambda v: min(170, v)))
    out = os.path.join(DESK, "novaos", "artwork", "login-background.jpg")
    im.save(out, quality=88, optimize=True, progressive=True)
    print(out, os.path.getsize(out) // 1024, "KiB")


def glow() -> None:
    """Soft violet-blue light behind the logo on the boot screen (Plymouth)."""
    size = 420
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = im.load()
    c = size / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / c
            if r >= 1:
                continue
            a = (1 - r) ** 2.2
            # violet in the middle, blue towards the edge
            col = (int(124 * (1 - r) + 61 * r), int(92 * (1 - r) + 123 * r), 255)
            px[x, y] = (*col, int(150 * a))
    out = os.path.join(DESK, "plymouth", "themes", "novaos", "glow.png")
    im.save(out, optimize=True)
    print(out, os.path.getsize(out) // 1024, "KiB")


if __name__ == "__main__":
    login_background()
    glow()
