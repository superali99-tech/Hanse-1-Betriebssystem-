#!/usr/bin/env python3
"""Swap red and blue in QEMU screendumps of Android-x86 (std VGA shows BGR).

Verified against Android's own framebuffer (screencap raw): the search tile of
NovaOS TV is (110, 99, 255) in Android and (255, 99, 110) in the QEMU dump.
    python3 tests/qemu/fix_android_colors.py IN.png [OUT.png]
"""
import sys

from PIL import Image

src = sys.argv[1]
dst = sys.argv[2] if len(sys.argv) > 2 else src.replace(".png", "_fix.png")
r, g, b = Image.open(src).convert("RGB").split()
Image.merge("RGB", (b, g, r)).save(dst)
print(dst)
