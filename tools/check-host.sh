#!/bin/bash
# Check that the build host has everything needed to build the NovaOS ISO.
set -u

ok=1
need() {
	if command -v "$1" >/dev/null 2>&1; then
		printf '  [ok]   %s\n' "$1"
	else
		printf '  [miss] %s  (%s)\n' "$1" "$2"; ok=0
	fi
}

echo "NovaOS build host check:"
need lb           "apt install live-build (Debian 13 version 1:20250505 or newer)"
need debootstrap  "apt install debootstrap (>= 1.0.141 for trixie)"
need dpkg-deb     "apt install dpkg"
need rsync        "apt install rsync"
need xz           "apt install xz-utils"
need cpio         "apt install cpio"

if command -v lb >/dev/null 2>&1; then
	v=$(lb --version 2>/dev/null | head -n1)
	case "$v" in
		2025*|2026*|2027*|1:2025*|1:2026*) printf '  [ok]   live-build version %s\n' "$v" ;;
		*) printf '  [warn] live-build version %s - Debian trixie version (20250505+) recommended\n' "$v" ;;
	esac
fi

if [ -e /usr/share/debootstrap/scripts/trixie ]; then
	printf '  [ok]   debootstrap knows trixie\n'
else
	printf '  [miss] debootstrap script for trixie\n'; ok=0
fi

if [ -e /usr/share/keyrings/debian-archive-trixie-stable.gpg ] || \
   [ -e /usr/share/keyrings/debian-archive-trixie-automatic.gpg ]; then
	printf '  [ok]   Debian trixie archive keys\n'
else
	printf '  [warn] debian-archive-keyring without trixie keys (install version 2025.1+)\n'
fi

avail_kb=$(df -Pk "$(dirname "$0")/.." | awk 'NR==2 {print $4}')
if [ "${avail_kb:-0}" -lt 20000000 ]; then
	printf '  [warn] less than 20 GB free disk space (%s GB)\n' "$((avail_kb/1024/1024))"
else
	printf '  [ok]   free disk space: %s GB\n' "$((avail_kb/1024/1024))"
fi

[ "$ok" -eq 1 ]
