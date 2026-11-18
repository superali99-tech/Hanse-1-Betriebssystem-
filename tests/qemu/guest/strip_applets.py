"""Guest-side debugging helper: remove applets from the Plasma config.

Usage (inside the VM, as the desktop user):
    python3 strip_applets.py PLUGIN [PLUGIN...]
Removes all applets with the given plugin ids from
~/.config/plasma-org.kde.plasma.desktop-appletsrc (backup: *.bak).
"""
import os
import re
import shutil
import sys

path = os.path.expanduser("~/.config/plasma-org.kde.plasma.desktop-appletsrc")
shutil.copy(path, path + ".bak")
plugins = set(sys.argv[1:])
text = open(path, encoding="utf-8").read()
blocks = re.split(r"(?m)^(?=\[)", text)
applet_re = re.compile(r"^\[Containments\]\[(\d+)\]\[Applets\]\[(\d+)\]")
remove = set()
for b in blocks:
    m = applet_re.match(b)
    if m and re.search(r"(?m)^plugin=(.+)$", b) and re.search(r"(?m)^plugin=(.+)$", b).group(1).strip() in plugins:
        if b.startswith(f"[Containments][{m.group(1)}][Applets][{m.group(2)}]\n"):
            remove.add((m.group(1), m.group(2)))
out = [b for b in blocks if not (applet_re.match(b) and applet_re.match(b).groups() in remove)]
open(path, "w", encoding="utf-8").write("".join(out))
print("removed applets:", sorted(remove))
