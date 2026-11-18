#!/usr/bin/env python3
"""Drive a NovaOS ISO in QEMU for automated boot tests.

Features: BIOS or UEFI (OVMF, optionally Secure Boot), screenshots through
the QEMU monitor, keyboard input, serial console access (login + commands)
and an optional virtual hard disk for installation/persistence tests.

Example:
    python3 tests/qemu/novaqemu.py build/out/novaos-0.1.0-amd64.iso \
        --out tests/qemu/out/bios --screens 30,90,180,300

Works without KVM (TCG), but then everything is ~5-10x slower.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time

OVMF_CANDIDATES = [
    ("/usr/share/OVMF/OVMF_CODE_4M.fd", "/usr/share/OVMF/OVMF_VARS_4M.fd"),
    ("/usr/share/OVMF/OVMF_CODE.fd", "/usr/share/OVMF/OVMF_VARS.fd"),
    ("/usr/share/ovmf/OVMF.fd", None),
]
OVMF_SECBOOT = ("/usr/share/OVMF/OVMF_CODE_4M.secboot.fd", "/usr/share/OVMF/OVMF_VARS_4M.ms.fd")

KEYMAP = {
    " ": "spc", "\n": "ret", "-": "minus", "=": "equal", ".": "dot", ",": "comma", "/": "slash",
    ";": "semicolon", "'": "apostrophe", "\\": "backslash", "[": "bracket_left", "]": "bracket_right",
    "\t": "tab", "`": "grave_accent",
}
SHIFTED = {
    "_": "minus", "+": "equal", ">": "dot", "<": "comma", "?": "slash", ":": "semicolon", '"': "apostrophe",
    "|": "backslash", "{": "bracket_left", "}": "bracket_right", "!": "1", "@": "2", "#": "3", "$": "4",
    "%": "5", "^": "6", "&": "7", "*": "8", "(": "9", ")": "0", "~": "grave_accent",
}


class Machine:
    def __init__(self, iso: str, workdir: str, uefi: bool = False, secure_boot: bool = False,
                 memory: int = 4096, cpus: int = 4, disk: str | None = None, disk_size: str = "30G",
                 kernel_args: str | None = None, boot_iso: bool = True):
        self.iso, self.workdir = iso, workdir
        os.makedirs(workdir, exist_ok=True)
        self.mon_path = os.path.join(workdir, "monitor.sock")
        self.qmp_path = os.path.join(workdir, "qmp.sock")
        self.screen_size = (1280, 800)
        self.serial_path = os.path.join(workdir, "serial.sock")
        self.serial_log = os.path.join(workdir, "serial.log")
        self.proc: subprocess.Popen | None = None
        self.uefi, self.secure_boot = uefi or secure_boot, secure_boot
        self.memory, self.cpus = memory, cpus
        self.disk, self.disk_size = disk, disk_size
        self.kernel_args = kernel_args
        self.boot_iso = boot_iso
        self._serial: socket.socket | None = None
        self._serial_buf = b""

    # ------------------------------------------------------------------ start
    def _firmware_args(self) -> list[str]:
        if not self.uefi:
            return []
        if self.secure_boot:
            code, vars_tpl = OVMF_SECBOOT
        else:
            code, vars_tpl = next(((c, v) for c, v in OVMF_CANDIDATES if os.path.exists(c)), (None, None))
        if not code or not os.path.exists(code):
            raise SystemExit("OVMF firmware not found (apt install ovmf)")
        args = ["-drive", f"if=pflash,format=raw,readonly=on,file={code}"]
        if vars_tpl:
            vars_copy = os.path.join(self.workdir, "OVMF_VARS.fd")
            if not os.path.exists(vars_copy):
                shutil.copy(vars_tpl, vars_copy)
            args += ["-drive", f"if=pflash,format=raw,file={vars_copy}"]
        if self.secure_boot:
            args = ["-machine", "q35,smm=on", "-global", "driver=cfi.pflash01,property=secure,value=on"] + args
        return args

    def start(self) -> None:
        for p in (self.mon_path, self.serial_path, self.qmp_path):
            if os.path.exists(p):
                os.unlink(p)
        accel = "kvm" if os.path.exists("/dev/kvm") and os.access("/dev/kvm", os.W_OK) else "tcg,thread=multi"
        # Without KVM, QEMU emulates the CPU (TCG). Its AVX/AVX2 emulation is a
        # known source of crashes in SIMD heavy code (llvmpipe JIT, Qt image
        # blending), so by default a CPU model without AVX is used for TCG.
        cpu = os.environ.get("NOVA_QEMU_CPU") or ("host" if accel == "kvm" else "max,avx=off,avx2=off,fma=off,f16c=off,avx512f=off")
        args = ["qemu-system-x86_64", "-accel", accel, "-m", str(self.memory), "-smp", str(self.cpus), "-cpu", cpu]
        if not self.secure_boot:
            args += ["-machine", "q35"]
        args += self._firmware_args()
        args += [
            "-vga", "std", "-display", "none",
            "-monitor", f"unix:{self.mon_path},server,nowait",
            "-qmp", f"unix:{self.qmp_path},server,nowait",
            "-chardev", f"socket,id=ser0,path={self.serial_path},server=on,wait=off,logfile={self.serial_log}",
            "-serial", "chardev:ser0",
            "-netdev", "user,id=n0", "-device", "virtio-net-pci,netdev=n0",
            "-device", "qemu-xhci", "-device", "usb-tablet",
            "-audiodev", "none,id=snd0", "-device", "intel-hda", "-device", "hda-duplex,audiodev=snd0",
        ]
        if self.disk:
            if not os.path.exists(self.disk):
                subprocess.run(["qemu-img", "create", "-f", "qcow2", self.disk, self.disk_size], check=True,
                               stdout=subprocess.DEVNULL)
            args += ["-drive", f"file={self.disk},if=virtio,format=qcow2"]
        if self.iso:
            args += ["-drive", f"file={self.iso},media=cdrom,readonly=on,if=none,id=cd0",
                     "-device", "ide-cd,drive=cd0" + (",bootindex=1" if self.boot_iso else "")]
        if self.kernel_args is not None:
            kdir = self._extract_kernel()
            args += ["-kernel", f"{kdir}/vmlinuz", "-initrd", f"{kdir}/initrd.img", "-append", self.kernel_args]
        self.cmdline = args
        self.proc = subprocess.Popen(args, stdout=open(os.path.join(self.workdir, "qemu.log"), "w"),
                                     stderr=subprocess.STDOUT)
        for _ in range(100):
            if os.path.exists(self.mon_path) and os.path.exists(self.serial_path):
                break
            time.sleep(0.1)
        # The serial port is only connected on demand (_read_serial/send_serial):
        # a connected client that does not read would block the guest's console.
        self._serial = None
        self.t0 = time.time()

    def _connect_serial(self) -> None:
        self._serial = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self._serial.connect(self.serial_path)
        self._serial.setblocking(False)

    @classmethod
    def attach(cls, workdir: str) -> "Machine":
        """Control an already running VM (started with the 'start' command)."""
        m = cls("", workdir)
        m._serial = None
        m.t0 = time.time()
        m.alive = lambda: True  # type: ignore[method-assign]
        return m

    def _extract_kernel(self) -> str:
        kdir = os.path.join(self.workdir, "kernel")
        os.makedirs(kdir, exist_ok=True)
        if not os.path.exists(f"{kdir}/vmlinuz"):
            listing = subprocess.run(["xorriso", "-indev", self.iso, "-find", "/live", "-name", "vmlinuz*"],
                                     capture_output=True, text=True).stdout
            vml = [l.strip().strip("'") for l in listing.splitlines() if "vmlinuz" in l][0]
            ini = vml.replace("vmlinuz", "initrd.img")
            subprocess.run(["xorriso", "-osirrox", "on", "-indev", self.iso, "-extract", vml, f"{kdir}/vmlinuz",
                            "-extract", ini, f"{kdir}/initrd.img"], check=True, capture_output=True)
        return kdir

    def stop(self) -> None:
        if self.proc and self.proc.poll() is None:
            try:
                self.hmp("quit")
            except OSError:
                pass
            try:
                self.proc.wait(10)
            except subprocess.TimeoutExpired:
                self.proc.kill()

    def alive(self) -> bool:
        return self.proc is not None and self.proc.poll() is None

    # ---------------------------------------------------------------- monitor
    def hmp(self, cmd: str) -> str:
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.connect(self.mon_path)
        s.settimeout(5)
        buf = b""
        try:
            while b"(qemu)" not in buf:
                buf += s.recv(4096)
            s.sendall(cmd.encode() + b"\n")
            buf = b""
            end = time.time() + 5
            while time.time() < end:
                try:
                    chunk = s.recv(4096)
                except socket.timeout:
                    break
                if not chunk:
                    break
                buf += chunk
                if buf.rstrip().endswith(b"(qemu)"):
                    break
        finally:
            s.close()
        return buf.decode(errors="replace")

    def screenshot(self, name: str) -> str:
        ppm = os.path.join(self.workdir, name + ".ppm")
        png = os.path.join(self.workdir, name + ".png")
        self.hmp(f"screendump {ppm}")
        for _ in range(50):
            if os.path.exists(ppm) and os.path.getsize(ppm) > 0:
                break
            time.sleep(0.1)
        time.sleep(0.3)
        subprocess.run(["convert", ppm, png], check=False)
        try:
            out = subprocess.run(["identify", "-format", "%w %h", png], capture_output=True, text=True).stdout.split()
            self.screen_size = (int(out[0]), int(out[1]))
        except (IndexError, ValueError):
            pass
        if os.path.exists(ppm):
            os.unlink(ppm)
        return png

    def qmp(self, command: str, arguments: dict | None = None) -> dict:
        import json

        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.connect(self.qmp_path)
        s.settimeout(10)
        f = s.makefile("rwb")
        f.readline()  # greeting
        f.write(json.dumps({"execute": "qmp_capabilities"}).encode() + b"\n")
        f.flush()
        f.readline()
        msg = {"execute": command}
        if arguments:
            msg["arguments"] = arguments
        f.write(json.dumps(msg).encode() + b"\n")
        f.flush()
        while True:
            reply = json.loads(f.readline())
            if "return" in reply or "error" in reply:
                break
        s.close()
        return reply

    def _abs(self, x: int, y: int) -> list:
        w, h = self.screen_size
        return [{"type": "abs", "data": {"axis": "x", "value": int(x * 32767 / (w - 1))}},
                {"type": "abs", "data": {"axis": "y", "value": int(y * 32767 / (h - 1))}}]

    def move(self, x: int, y: int) -> None:
        self.qmp("input-send-event", {"events": self._abs(x, y)})

    def click(self, x: int, y: int, button: str = "left", double: bool = False) -> None:
        """Click at screen coordinates (pixels of the last screenshot)."""
        self.move(x, y)
        time.sleep(0.2)
        for _ in range(2 if double else 1):
            self.qmp("input-send-event", {"events": [{"type": "btn", "data": {"down": True, "button": button}}]})
            time.sleep(0.08)
            self.qmp("input-send-event", {"events": [{"type": "btn", "data": {"down": False, "button": button}}]})
            time.sleep(0.12)

    def key(self, combo: str) -> None:
        self.hmp(f"sendkey {combo} 30")
        time.sleep(0.15)

    # Characters on a German (de) keyboard layout -> QEMU key (US scancode names)
    DE_KEYS = {
        "y": "z", "z": "y", "Y": "shift-z", "Z": "shift-y",
        "-": "slash", "_": "shift-slash", "/": "shift-7", ":": "shift-dot", ";": "shift-comma",
        "=": "shift-0", "?": "shift-minus", "+": "bracket_right", "*": "shift-bracket_right",
        "#": "backslash", "'": "shift-backslash", '"': "shift-2", "$": "shift-4", "%": "shift-5",
        "&": "shift-6", "(": "shift-8", ")": "shift-9", "!": "shift-1", "<": "less", ">": "shift-less",
        "|": "alt_r-less", "~": "alt_r-bracket_right", "\\": "alt_r-minus", "@": "alt_r-q",
        "{": "alt_r-7", "[": "alt_r-8", "]": "alt_r-9", "}": "alt_r-0", " ": "spc", "\n": "ret",
        ".": "dot", ",": "comma",
    }

    def type_de(self, text: str) -> None:
        """Type text into a guest that uses the German keyboard layout."""
        for ch in text:
            if ch in self.DE_KEYS:
                self.key(self.DE_KEYS[ch])
            elif ch.isalpha() and ch.isupper():
                self.key(f"shift-{ch.lower()}")
            elif ch.isalnum():
                self.key(ch)

    def type(self, text: str) -> None:
        for ch in text:
            if ch.isalpha() and ch.isupper():
                self.key(f"shift-{ch.lower()}")
            elif ch.isalnum():
                self.key(ch)
            elif ch in KEYMAP:
                self.key(KEYMAP[ch])
            elif ch in SHIFTED:
                self.key(f"shift-{SHIFTED[ch]}")

    # ----------------------------------------------------------------- serial
    def _read_serial(self) -> None:
        if self._serial is None:
            self._connect_serial()
        while True:
            try:
                chunk = self._serial.recv(65536)
            except BlockingIOError:
                break
            if not chunk:
                break
            self._serial_buf += chunk  # QEMU itself writes serial.log (chardev logfile)

    def serial_text(self) -> str:
        self._read_serial()
        return self._serial_buf.decode(errors="replace")

    def wait_serial(self, pattern: str, timeout: float) -> re.Match | None:
        end = time.time() + timeout
        rx = re.compile(pattern)
        start = len(self._serial_buf)
        while time.time() < end and self.alive():
            self._read_serial()
            m = rx.search(self._serial_buf[start:].decode(errors="replace"))
            if m:
                return m
            time.sleep(0.5)
        return None

    def send_serial(self, text: str) -> None:
        if self._serial is None:
            self._connect_serial()
        self._serial.setblocking(True)
        self._serial.sendall(text.encode())
        self._serial.setblocking(False)

    def run(self, command: str, timeout: float = 120) -> tuple[int, str]:
        """Run a shell command on the logged-in serial console."""
        marker = f"__NOVA_{int(time.time() * 1000) % 10**8}__"
        self._read_serial()
        start = len(self._serial_buf)
        self.send_serial(f"{command}; echo {marker}rc=$?\n")
        end = time.time() + timeout
        rx = re.compile(re.escape(marker) + r"rc=(\d+)")
        while time.time() < end and self.alive():
            self._read_serial()
            text = self._serial_buf[start:].decode(errors="replace")
            matches = list(rx.finditer(text))
            # first match is the echo of the typed command line itself
            real = [m for m in matches if not text[max(0, m.start() - 6):m.start()].endswith("echo ")]
            if real:
                m = real[-1]
                out = text[:m.start()]
                first, _, rest = out.partition("\n")
                if marker in first:  # echoed command line (terminal echo still on)
                    out = rest
                return int(m.group(1)), out.strip()
            time.sleep(0.5)
        return -1, self._serial_buf[start:].decode(errors="replace")

    def login(self, user: str = "nova", password: str = "live", timeout: float = 900) -> bool:
        end = time.time() + timeout
        while time.time() < end and self.alive():
            self.send_serial("\n")
            if self.wait_serial(r"login: ?$|login:\s*$", 20):
                self.send_serial(user + "\n")
                if self.wait_serial(r"[Pp]ass(word|wort): ?", 30):  # en/de
                    self.send_serial(password + "\n")
                if self.wait_serial(r"\$ $|\$\s*$|# $", 60):
                    self.send_serial("export TERM=dumb PS1='$ '; stty -echo cols 200\n")
                    time.sleep(1)
                    return True
        return False


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("screens", help="boot, take screenshots at the given seconds, stop")
    p.add_argument("iso")
    p.add_argument("--out", default="tests/qemu/out/run")
    p.add_argument("--uefi", action="store_true")
    p.add_argument("--secure-boot", action="store_true")
    p.add_argument("--screens", default="20,60,120,240", help="comma separated seconds")
    p.add_argument("--memory", type=int, default=4096)
    p = sub.add_parser("start", help="start a VM and keep it running (run in background)")
    p.add_argument("iso")
    p.add_argument("--out", required=True)
    p.add_argument("--uefi", action="store_true")
    p.add_argument("--secure-boot", action="store_true")
    p.add_argument("--disk", help="qcow2 disk image (created if missing)")
    p.add_argument("--no-cdrom-boot", action="store_true", help="boot from the disk first")
    p.add_argument("--kernel-args", help="boot the ISO kernel directly with these arguments")
    p.add_argument("--memory", type=int, default=4096)
    for name in ("shot", "click", "dclick", "rclick", "type", "typede", "key", "run", "login", "serial", "stop"):
        q = sub.add_parser(name, help="control a VM started with 'start'")
        q.add_argument("out")
        q.add_argument("args", nargs="*")
    args = ap.parse_args()

    if args.cmd == "screens":
        m = Machine(args.iso, args.out, uefi=args.uefi, secure_boot=args.secure_boot, memory=args.memory)
        m.start()
        try:
            for t in [int(x) for x in args.screens.split(",")]:
                while time.time() - m.t0 < t and m.alive():
                    time.sleep(1)
                if not m.alive():
                    print("QEMU exited")
                    break
                print("screenshot", m.screenshot(f"t{t:04d}"), flush=True)
        finally:
            m.stop()
        return 0
    if args.cmd == "start":
        m = Machine(args.iso, args.out, uefi=args.uefi, secure_boot=args.secure_boot, memory=args.memory,
                    disk=args.disk, kernel_args=args.kernel_args, boot_iso=not args.no_cdrom_boot)
        m.start()
        print("started:", " ".join(m.cmdline), flush=True)
        m.proc.wait()
        return 0

    m = Machine.attach(args.out)
    a = args.args
    if args.cmd == "shot":
        # shot OUT [NAME] [DELAY_SECONDS]: optionally wait before taking the screenshot
        if len(a) > 1:
            time.sleep(float(a[1]))
        print(m.screenshot(a[0] if a else time.strftime("s%H%M%S")))
    elif args.cmd in ("click", "dclick", "rclick"):
        m.screenshot("_size")
        m.click(int(a[0]), int(a[1]), "right" if args.cmd == "rclick" else "left", double=args.cmd == "dclick")
    elif args.cmd == "type":
        m.type(" ".join(a))
    elif args.cmd == "typede":
        m.type_de(" ".join(a))
    elif args.cmd == "key":
        for combo in a:
            m.key(combo)
    elif args.cmd == "login":
        print("login ok" if m.login(*a) else "login FAILED")
    elif args.cmd == "run":
        rc, out = m.run(" ".join(a), timeout=float(os.environ.get("NOVA_TIMEOUT", "300")))
        print(out)
        print(f"[exit {rc}]")
    elif args.cmd == "serial":
        with open(m.serial_log, errors="replace") as fh:
            print(fh.read()[-(int(a[0]) if a else 4000):])
    elif args.cmd == "stop":
        m.hmp("quit")
    return 0


if __name__ == "__main__":
    sys.exit(main())
