#!/bin/bash
# Android-x86 9 in QEMU for testing NovaOS TV (TV-sized screen, remote-control keys, adb).
#
#   tests/qemu/android_vm.sh start  ISO WORKDIR    boot the live system (TCG works, slowly)
#   tests/qemu/android_vm.sh setup  WORKDIR        wait for Android, enable adb over TCP and Wi-Fi
#   tests/qemu/android_vm.sh wifi   WORKDIR        connect to the QEMU network ("VirtWifi")
#   tests/qemu/android_vm.sh proxy  WORKDIR PORT CA.pem
#                                                  route the guest's HTTPS through a proxy on
#                                                  the host (10.0.2.2:PORT) that re-terminates
#                                                  TLS with CA.pem (only for such sandboxes)
#   tests/qemu/android_vm.sh stop   WORKDIR
#
# Afterwards: adb connect 127.0.0.1:5555; adb -s 127.0.0.1:5555 install/shell/exec-out screencap -p ...
# The screen is 1280x720 at 213 dpi = 960x540 dp, the layout size of every Android TV.
# QEMU's screendump of Android-x86 swaps red and blue - use "adb exec-out screencap -p".
set -euo pipefail
cmd="${1:-}"
case "$cmd" in
start)
	iso="$2"; work="$3"
	mkdir -p "$work/kernel"
	if [ ! -f "$work/kernel/kernel" ]; then
		if command -v xorriso >/dev/null; then
			xorriso -osirrox on -indev "$iso" -extract /kernel "$work/kernel/kernel" \
				-extract /initrd.img "$work/kernel/initrd.img" >/dev/null 2>&1
		else  # pip install pycdlib
			python3 -c 'import pycdlib, sys
i = pycdlib.PyCdlib(); i.open(sys.argv[1])
for n in ("kernel", "initrd.img"): i.get_file_from_iso(sys.argv[2] + "/" + n, rr_path="/" + n)' "$iso" "$work/kernel"
		fi
	fi
	# Conservative CPU model for TCG. The graphics run in VESA mode (nomodeset): with the
	# bochs DRM driver the 4.19 kernel panicked in ttm_bo_vm_open whenever surfaceflinger forked.
	accel="tcg,thread=multi"; cpu="Nehalem"
	if [ -w /dev/kvm ]; then accel=kvm; cpu=host; fi
	qemu-system-x86_64 -accel "$accel" -cpu "$cpu" -m 3072 -smp 4 -machine q35 \
		-kernel "$work/kernel/kernel" -initrd "$work/kernel/initrd.img" \
		-append "root=/dev/ram0 androidboot.selinux=permissive console=ttyS0 SRC= DATA= SETUPWIZARD=0 nomodeset UVESA_MODE=1280x720 DPI=213" \
		-drive "file=$iso,media=cdrom,readonly=on,if=none,id=cd0" -device ide-cd,drive=cd0 \
		-device VGA,edid=on,xres=1280,yres=720,vgamem_mb=64 -display none \
		-netdev user,id=n0,hostfwd=tcp:127.0.0.1:5555-:5555 -device e1000,netdev=n0 \
		-monitor "unix:$work/monitor.sock,server,nowait" \
		-serial "unix:$work/serial.sock,server,nowait" \
		-daemonize -pidfile "$work/qemu.pid"
	echo "started (pid $(cat "$work/qemu.pid"))"
	;;
setup)
	work="$2"
	# Root shell on the serial console: adb over TCP (port forwarded to the host).
	python3 - "$work/serial.sock" <<'EOF'
import socket, sys, time
s = socket.socket(socket.AF_UNIX); s.connect(sys.argv[1]); s.settimeout(2)
# Under TCG the guest reset itself while dex2oat compiled the apps: "extract" (no
# compilation) avoids that. Set again on every poll, so it also applies after a reset.
EXTRACT = (b"for p in first-boot boot install bg-dexopt inactive shared; do setprop pm.dexopt.$p extract; done;"
           b" setprop dalvik.vm.dex2oat-filter extract\n")
deadline = time.time() + 2400
while time.time() < deadline:
    s.sendall(b"\n" + EXTRACT + b"echo BC=$(getprop sys.boot_completed)\n")
    buf = b""
    end = time.time() + 5
    while time.time() < end:
        try:
            buf += s.recv(65536)
        except socket.timeout:
            pass
    if b"BC=1" in buf:
        break
    time.sleep(10)
else:
    sys.exit("Android did not finish booting")
s.sendall(b"setprop service.adb.tcp.port 5555; stop adbd; start adbd; settings put global stay_on_while_plugged_in 7;"
          b" svc wifi enable\n")
time.sleep(3)
print("boot completed, adb on port 5555")
EOF
	;;
proxy)
	work="$2"; port="$3"; ca="$4"
	adb=("${ADB:-adb}" -s 127.0.0.1:5555)
	# Android looks up system CAs as <subject_hash_old>.0; the system partition of the live
	# image is read-only, so the CA directory is copied into a tmpfs with the extra CA.
	name="$(openssl x509 -in "$ca" -noout -subject_hash_old).0"
	"${adb[@]}" push "$ca" "/data/local/tmp/$name" >/dev/null
	"${adb[@]}" shell "su 0 sh -c 'mkdir -p /data/local/tmp/ca && cp /system/etc/security/cacerts/* /data/local/tmp/ca/ \
		&& cp /data/local/tmp/$name /data/local/tmp/ca/ && mount -t tmpfs tmpfs /system/etc/security/cacerts \
		&& cp /data/local/tmp/ca/* /system/etc/security/cacerts/ && chmod 644 /system/etc/security/cacerts/* \
		&& chcon u:object_r:system_file:s0 /system/etc/security/cacerts/*'" || true
	"${adb[@]}" shell settings put global http_proxy "10.0.2.2:$port"
	echo "proxy 10.0.2.2:$port, CA $name"
	;;
wifi)
	# The QEMU network appears as Wi-Fi "VirtWifi"; the framework only connects (and runs
	# DHCP) when the network is chosen in the settings - adb over TCP needs this, too.
	work="$2"
	python3 -c 'import socket, sys, time
s = socket.socket(socket.AF_UNIX); s.connect(sys.argv[1])
s.sendall(b"\nam start -a android.settings.WIFI_SETTINGS\n"); time.sleep(15)
s.sendall(b"input tap 130 200\n"); time.sleep(30)  # first entry of the list: VirtWifi
' "$work/serial.sock"
	;;
stop)
	work="$2"
	[ -f "$work/qemu.pid" ] && kill "$(cat "$work/qemu.pid")" 2>/dev/null || true
	;;
*)
	sed -n '2,15p' "$0"; exit 1 ;;
esac
