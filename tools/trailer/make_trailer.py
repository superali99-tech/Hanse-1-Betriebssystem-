#!/usr/bin/env python3
"""Cut the NovaOS trailer from real screen recordings.

Input: a scene list (JSON) that refers to screenshots / frame sequences
recorded from NovaOS (tests/qemu/novaqemu.py), the soundtrack from music.py
and the NovaOS artwork. Output: H.264/AAC MP4, 1920x1080, 30 fps.

Every picture in the trailer comes from a recording of the real system;
the script only adds camera moves (zoom/pan), cross fades, captions and the
animated title/outro cards (NovaOS logo and wallpaper from artwork/).

    python3 make_trailer.py scenes.json music.wav out.mp4   (needs numpy, Pillow, ffmpeg)
"""
from __future__ import annotations

import json
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 30
XFADE = 0.5
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT_DIR = "/usr/share/fonts/opentype/inter"
VIOLET = (124, 92, 255)
BLUE = (61, 123, 255)
NIGHT = (11, 14, 31)


def font(weight: str, size: int) -> ImageFont.FreeTypeFont:
    for name in (f"Inter-{weight}.otf", f"InterDisplay-{weight}.otf"):
        p = os.path.join(FONT_DIR, name)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.truetype("DejaVuSans-Bold.ttf" if weight in ("Bold", "ExtraBold") else "DejaVuSans.ttf", size)


def ease(x: float) -> float:
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def ease_out(x: float) -> float:
    x = min(1.0, max(0.0, x))
    return 1 - (1 - x) ** 3


_cache: dict[str, Image.Image] = {}


def load(path: str) -> Image.Image:
    """Screenshot as 1920x1080 RGB; other sizes are fitted onto a blurred backdrop."""
    if path in _cache:
        return _cache[path]
    im = Image.open(path).convert("RGB")
    if im.size != (W, H):
        if abs(im.width / im.height - W / H) < 0.01:
            im = im.resize((W, H), Image.LANCZOS)
        else:
            bg = im.resize((W, int(W * im.height / im.width)), Image.BILINEAR)
            top = (bg.height - H) // 2
            bg = bg.crop((0, max(0, top), W, max(0, top) + H)).filter(ImageFilter.GaussianBlur(40))
            bg = Image.blend(bg, Image.new("RGB", (W, H), NIGHT), 0.45)
            fg = im.resize((int(H * im.width / im.height), H), Image.LANCZOS)
            bg.paste(fg, ((W - fg.width) // 2, 0))
            im = bg
    _cache[path] = im
    return im


def kenburns(im: Image.Image, p: float, zoom=(1.0, 1.06), focus=((0.5, 0.5), (0.5, 0.5))) -> Image.Image:
    e = ease(p)
    z = zoom[0] + (zoom[1] - zoom[0]) * e
    fx = focus[0][0] + (focus[1][0] - focus[0][0]) * e
    fy = focus[0][1] + (focus[1][1] - focus[0][1]) * e
    cw, ch = W / z, H / z
    left = min(max(fx * W - cw / 2, 0), W - cw)
    top = min(max(fy * H - ch / 2, 0), H - ch)
    return im.resize((W, H), Image.BILINEAR, box=(left, top, left + cw, top + ch))


# ------------------------------------------------------------------ captions
def caption_layer(title: str, sub: str | None) -> Image.Image:
    ft, fs = font("Bold", 50), font("Regular", 30)
    d = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    tw = d.textbbox((0, 0), title, font=ft)[2]
    sw = d.textbbox((0, 0), sub, font=fs)[2] if sub else 0
    pw = max(tw, sw) + 92
    ph = 128 if sub else 92
    layer = Image.new("RGBA", (pw + 40, ph + 40), (0, 0, 0, 0))
    shadow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((20, 24, 20 + pw, 24 + ph), 24, fill=(0, 0, 0, 110))
    layer = Image.alpha_composite(layer, shadow.filter(ImageFilter.GaussianBlur(14)))
    dr = ImageDraw.Draw(layer)
    dr.rounded_rectangle((20, 20, 20 + pw, 20 + ph), 24, fill=NIGHT + (205,), outline=(255, 255, 255, 40), width=2)
    dr.rounded_rectangle((44, 46, 52, 20 + ph - 26), 4, fill=VIOLET + (255,))
    dr.text((72, 34), title, font=ft, fill=(255, 255, 255, 255))
    if sub:
        dr.text((74, 96), sub, font=fs, fill=(200, 206, 240, 255))
    return layer


def overlay(frame: np.ndarray, layer: Image.Image, x: int, y: int, alpha: float) -> None:
    if alpha <= 0:
        return
    arr = np.asarray(layer, dtype=np.float32)
    h, w = arr.shape[:2]
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + w), min(H, y + h)
    if x0 >= x1 or y0 >= y1:
        return
    sub = arr[y0 - y:y1 - y, x0 - x:x1 - x]
    a = sub[..., 3:4] / 255.0 * alpha
    region = frame[y0:y1, x0:x1]
    region[:] = region * (1 - a) + sub[..., :3] * a


# --------------------------------------------------------------- title cards
_assets: dict[str, Image.Image] = {}


def asset(name: str) -> Image.Image:
    if name in _assets:
        return _assets[name]
    if name == "wallpaper":
        p = os.path.join(ROOT, "packages/novaos-desktop/root/usr/share/wallpapers/NovaAurora/contents/images_dark/3840x2160.png")
        im = Image.open(p).convert("RGB").resize((W, H), Image.LANCZOS)
    elif name == "logo":
        out = os.path.join(ROOT, "build/trailer/logo-512.png")
        if not os.path.exists(out):
            subprocess.run(["rsvg-convert", os.path.join(ROOT, "artwork/svg/logo/novaos-logo.svg"), "-w", "512",
                            "-o", out], check=True)
        im = Image.open(out).convert("RGBA")
    else:
        raise KeyError(name)
    _assets[name] = im
    return im


def centered_text(dr: ImageDraw.ImageDraw, y: int, text: str, f, fill) -> None:
    w = dr.textbbox((0, 0), text, font=f)[2]
    dr.text(((W - w) // 2, y), text, font=f, fill=fill)


def wallpaper_frame(t: float, drift: float = 0.04) -> Image.Image:
    return kenburns(asset("wallpaper"), (t % 30) / 30, zoom=(1.0 + drift, 1.0), focus=((0.6, 0.4), (0.45, 0.55)))


def intro(t: float, dur: float, sc: dict) -> Image.Image:
    bg = wallpaper_frame(t)
    img = bg.convert("RGBA")
    logo = asset("logo")
    s = 0.6 + 0.4 * ease_out(t / 1.6)
    size = int(300 * s)
    lg = logo.resize((size, size), Image.LANCZOS)
    glow = Image.new("RGBA", lg.size, VIOLET + (255,))
    glow.putalpha(lg.getchannel("A").filter(ImageFilter.GaussianBlur(30)).point(lambda v: int(v * 0.55)))
    a = ease(t / 1.0)
    for layer, off in ((glow, 0), (lg, 0)):
        tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        tmp.paste(layer, ((W - size) // 2, 300 - size // 2 + 70 + off), layer)
        img = Image.blend(img, Image.alpha_composite(img, tmp), a)
    dr = ImageDraw.Draw(img)
    ta = ease((t - 1.0) / 0.8)
    if ta > 0:
        f = font("Bold", 120)
        word = "NovaOS"
        w = dr.textbbox((0, 0), word, font=f)[2]
        y = 560 + int(20 * (1 - ta))
        dr.text(((W - w) // 2, y), "Nova", font=f, fill=(255, 255, 255, int(255 * ta)))
        nw = dr.textbbox((0, 0), "Nova", font=f)[2]
        dr.text(((W - w) // 2 + nw, y), "OS", font=font("Light", 120), fill=(255, 255, 255, int(255 * ta)))
    sa = ease((t - 2.2) / 0.8)
    if sa > 0:
        centered_text(dr, 730, sc.get("tagline", ""), font("Medium", 44), (210, 214, 255, int(255 * sa)))
    ea = 1 - ease((t - (dur - 0.6)) / 0.6) if t > dur - 0.6 else 1
    out = img.convert("RGB")
    if ea < 1:
        out = Image.blend(Image.new("RGB", (W, H), (0, 0, 0)), out, ea)
    return out


def features(t: float, dur: float, sc: dict) -> Image.Image:
    base = load(sc["bg"]).filter(ImageFilter.GaussianBlur(18)) if "bg" in sc else wallpaper_frame(t)
    base = Image.blend(base, Image.new("RGB", (W, H), NIGHT), 0.72).convert("RGBA")
    dr = ImageDraw.Draw(base)
    ha = ease(t / 0.6)
    centered_text(dr, 150, sc["title"], font("Bold", 72), (255, 255, 255, int(255 * ha)))
    if sc.get("sub"):
        centered_text(dr, 245, sc["sub"], font("Regular", 36), (200, 206, 240, int(255 * ha)))
    items = sc["items"]
    step = min(0.9, (dur - 2.5) / max(1, len(items)))
    f = font("SemiBold", 46)
    for i, text in enumerate(items):
        a = ease((t - 0.8 - i * step) / 0.5)
        if a <= 0:
            continue
        y = 360 + i * 108
        x = 560 + int(40 * (1 - a))
        al = int(255 * a)
        dr.ellipse((x, y + 4, x + 56, y + 60), fill=VIOLET + (al,))
        dr.line((x + 15, y + 33, x + 25, y + 44, x + 42, y + 20), fill=(255, 255, 255, al), width=6, joint="curve")
        dr.text((x + 84, y + 4), text, font=f, fill=(255, 255, 255, al))
    return base.convert("RGB")


def outro(t: float, dur: float, sc: dict) -> Image.Image:
    img = wallpaper_frame(t + 10).convert("RGBA")
    logo = asset("logo").resize((220, 220), Image.LANCZOS)
    a = ease(t / 0.8)
    tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tmp.paste(logo, ((W - 220) // 2, 170), logo)
    img = Image.blend(img, Image.alpha_composite(img, tmp), a)
    dr = ImageDraw.Draw(img)
    lines = sc["lines"]
    y = 440
    for i, (text, style, size, color) in enumerate(lines):
        la = ease((t - 0.5 - i * 0.35) / 0.6)
        if la > 0:
            centered_text(dr, y, text, font(style, size), tuple(color) + (int(255 * la),))
        y += int(size * 1.45)
    out = img.convert("RGB")
    fade = ease((t - (dur - 1.2)) / 1.2)
    if fade > 0:
        out = Image.blend(out, Image.new("RGB", (W, H), (0, 0, 0)), fade)
    return out


CARDS = {"intro": intro, "features": features, "outro": outro}


# ------------------------------------------------------------------- render
def clip_frame(sc: dict, t: float) -> Image.Image:
    dur = sc["dur"]
    p = t / dur
    if sc["type"] in CARDS:
        return CARDS[sc["type"]](t, dur, sc)
    if sc["type"] == "image":
        return kenburns(load(sc["src"]), p, tuple(sc.get("zoom", (1.0, 1.06))),
                        tuple(tuple(f) for f in sc.get("focus", ((0.5, 0.5), (0.5, 0.5)))))
    if sc["type"] == "seq":
        srcs = sc["srcs"]
        i = min(len(srcs) - 1, int(t * sc.get("fps", 4)))
        return kenburns(load(srcs[i]), p, tuple(sc.get("zoom", (1.0, 1.0))),
                        tuple(tuple(f) for f in sc.get("focus", ((0.5, 0.5), (0.5, 0.5)))))
    raise ValueError(sc["type"])


def main() -> None:
    scenes_path, music, out = sys.argv[1:4]
    base = os.path.dirname(os.path.abspath(scenes_path))
    with open(scenes_path, encoding="utf-8") as fh:
        scenes = json.load(fh)
    for sc in scenes:
        for k in ("src", "bg"):
            if k in sc and not os.path.isabs(sc[k]):
                sc[k] = os.path.join(base, sc[k])
        if "srcs" in sc:
            sc["srcs"] = [s if os.path.isabs(s) else os.path.join(base, s) for s in sc["srcs"]]
    # timeline: clips overlap by XFADE (the new clip fades in over the old one)
    t = 0.0
    for i, sc in enumerate(scenes):
        sc["start"] = t
        t += sc["dur"] - (XFADE if i < len(scenes) - 1 else 0)
        if sc.get("caption"):
            sc["_cap"] = caption_layer(*(sc["caption"] + [None])[:2])
    total = scenes[-1]["start"] + scenes[-1]["dur"]
    nframes = int(round(total * FPS))
    print(f"{len(scenes)} scenes, {total:.2f} s, {nframes} frames", flush=True)

    ff = subprocess.Popen([
        "ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
        "-r", str(FPS), "-i", "-", "-i", music, "-map", "0:v", "-map", "1:a",
        "-c:v", "libx264", "-preset", "slow", "-crf", "19", "-pix_fmt", "yuv420p", "-profile:v", "high",
        "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart",
        "-metadata", "title=NovaOS – Trailer", out], stdin=subprocess.PIPE)
    for n in range(nframes):
        tt = n / FPS
        frame = np.zeros((H, W, 3), dtype=np.float32)
        for i, sc in enumerate(scenes):
            lt = tt - sc["start"]
            if lt < 0 or lt >= sc["dur"]:
                continue
            img = np.asarray(clip_frame(sc, lt), dtype=np.float32)
            a = 1.0 if i == 0 else ease(lt / XFADE)
            frame = img if a >= 1 else frame * (1 - a) + img * a
            if "_cap" in sc:
                ca = min(ease((lt - 0.35) / 0.45), 1 - ease((lt - (sc["dur"] - 0.75)) / 0.4))
                slide = int(36 * (1 - ease((lt - 0.35) / 0.45)))
                cap = sc["_cap"]
                overlay(frame, cap, 70 - slide, H - cap.height - 70, ca)
        ff.stdin.write(np.clip(frame, 0, 255).astype(np.uint8).tobytes())
        if n % 150 == 0:
            print(f"frame {n}/{nframes}", flush=True)
    ff.stdin.close()
    ff.wait()
    print("wrote", out, flush=True)


if __name__ == "__main__":
    main()
