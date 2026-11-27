#!/usr/bin/env python3
"""NovaOS 2 artwork: wallpaper "Nebula" (light + dark), login background and boot glow.

Own design, generated: soft nebula clouds in violet, indigo and cyan, a field of stars and
a bright core. Run from the repository root:  python3 artwork/generate_nova2.py
"""
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "packages", "novaos-2", "root", "usr", "share")
W, H = 3840, 2160


def nebula(dark: bool, seed: int = 2) -> Image.Image:
    rnd = random.Random(seed)
    w, h = W // 4, H // 4          # paint small, scale up: smooth clouds
    base = (8, 9, 28) if dark else (34, 30, 92)
    img = Image.new("RGB", (w, h), base)
    # vertical sky gradient
    grad = Image.new("RGB", (1, h))
    top, bottom = ((10, 10, 34), (26, 12, 58)) if dark else ((44, 36, 120), (96, 56, 168))
    for y in range(h):
        t = y / (h - 1)
        grad.putpixel((0, y), tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)))
    img = grad.resize((w, h))
    clouds = [((124, 92, 255), 0.55), ((61, 123, 255), 0.45), ((47, 211, 198), 0.30), ((236, 72, 153), 0.28),
              ((139, 92, 246), 0.5)]
    for col, strength in clouds:
        layer = Image.new("RGB", (w, h), (0, 0, 0))
        d = ImageDraw.Draw(layer)
        cx, cy = rnd.uniform(0.25, 0.8) * w, rnd.uniform(0.3, 0.75) * h
        for _ in range(26):
            r = rnd.uniform(0.06, 0.22) * w
            x = cx + rnd.gauss(0, 0.17) * w
            y = cy + rnd.gauss(0, 0.11) * h
            k = rnd.uniform(0.4, 1.0) * strength * (0.75 if dark else 0.42)
            d.ellipse((x - r, y - r * 0.6, x + r, y + r * 0.6), fill=tuple(int(c * k) for c in col))
        layer = layer.filter(ImageFilter.GaussianBlur(w / 22))
        img = ImageChops.add(img, layer)
    # bright core
    core = Image.new("RGB", (w, h), (0, 0, 0))
    d = ImageDraw.Draw(core)
    cx, cy = 0.62 * w, 0.46 * h
    for i in range(30, 0, -1):
        r = i * w / 140
        v = int(10 * (1 - i / 30) ** 1.5 * (0.8 if dark else 0.45))
        d.ellipse((cx - r * 1.6, cy - r, cx + r * 1.6, cy + r), fill=(v * 2, v * 2, min(255, v * 3)))
    img = ImageChops.add(img, core.filter(ImageFilter.GaussianBlur(w / 40)))
    img = img.resize((W, H), Image.LANCZOS)
    # stars, sharp at full resolution
    d = ImageDraw.Draw(img)
    for _ in range(2600 if dark else 1400):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H * 0.92)
        b = rnd.random() ** 3
        r = 0.6 + b * 2.2
        a = int(120 + 135 * b)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(a, a, min(255, a + 20)))
    for _ in range(12):   # a few bright stars with a soft halo
        x, y = rnd.uniform(0.05, 0.95) * W, rnd.uniform(0.05, 0.7) * H
        halo = Image.new("L", (80, 80), 0)
        ImageDraw.Draw(halo).ellipse((30, 30, 50, 50), fill=255)
        halo = halo.filter(ImageFilter.GaussianBlur(9))
        img.paste((255, 255, 255), (int(x) - 40, int(y) - 40), halo.point(lambda v: int(v * 0.6)))
        d.ellipse((x - 2.5, y - 2.5, x + 2.5, y + 2.5), fill=(255, 255, 255))
    return img


def main() -> None:
    wall = os.path.join(OUT, "wallpapers", "NovaNebula", "contents")
    for dark, sub in ((False, "images"), (True, "images_dark")):
        os.makedirs(os.path.join(wall, sub), exist_ok=True)
        img = nebula(dark)
        p = os.path.join(wall, sub, "3840x2160.jpg")
        img.save(p, quality=90, optimize=True, progressive=True)
        print(p, os.path.getsize(p) // 1024, "KiB")
        if dark:
            login = img.resize((2560, 1440), Image.LANCZOS).filter(ImageFilter.GaussianBlur(24))
            lp = os.path.join(OUT, "novaos", "artwork", "login-background-2.jpg")
            os.makedirs(os.path.dirname(lp), exist_ok=True)
            login.save(lp, quality=88, optimize=True, progressive=True)
            print(lp, os.path.getsize(lp) // 1024, "KiB")
            prev = img.resize((960, 540), Image.LANCZOS)
            prev.save(os.path.join(wall, "screenshot.jpg"), quality=85)


if __name__ == "__main__":
    main()
