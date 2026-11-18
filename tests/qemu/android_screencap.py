#!/usr/bin/env python3
"""Take a screenshot inside an Android VM (screencap) and copy it out over the serial console.

The QEMU screendump of Android-x86 on std VGA shows red and blue swapped;
`screencap` reads SurfaceFlinger's buffer and gives the true colours. The PNG
is sent base64 encoded over the serial console (the VM has no network).

    python3 tests/qemu/android_screencap.py OUTDIR NAME
"""
import base64
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))


def main() -> int:
    out, name = sys.argv[1], sys.argv[2]
    tag = f"SC{int(time.time())}"
    cmd = (f"screencap -p /data/local/tmp/sc.png && echo {tag}_B && base64 /data/local/tmp/sc.png "
           f"&& echo {tag}_E")
    env = dict(os.environ, NOVA_TIMEOUT="240")
    # Parse what novaqemu read from the serial socket, not QEMU's chardev log
    # file: the log file repeats characters when QEMU retries a partial write.
    res = subprocess.run([sys.executable, os.path.join(HERE, "novaqemu.py"), "run", out, cmd],
                         env=env, capture_output=True, text=True, errors="replace", timeout=300)
    text = res.stdout.replace("\r", "")
    m = re.search(rf"^{tag}_B\n(.*?)^{tag}_E$", text, re.S | re.M)
    if not m:
        print("screencap: markers not found", file=sys.stderr)
        return 1
    data = base64.b64decode("".join(m.group(1).split()))
    path = os.path.join(out, name + ".png")
    with open(path, "wb") as fh:
        fh.write(data)
    print(path, len(data), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
