#!/bin/bash
# Build the NovaOS live/installer ISO with Debian live-build.
#
# Usage: sudo ./tools/build-iso.sh [--clean] [--purge] [--skip-debs]
#
#   --clean      run "lb clean" before building (keeps the package cache)
#   --purge      remove the whole work directory incl. package cache first
#   --skip-debs  do not rebuild the novaos-* packages (use build/debs as is)
#
# Environment:
#   NOVAOS_WORKDIR               work directory   (default: build/live)
#   NOVAOS_OUTDIR                output directory (default: build/out)
#   NOVAOS_MIRROR                Debian mirror    (default: http://deb.debian.org/debian)
#   NOVAOS_SQUASHFS_COMPRESSION  xz|zstd|lz4|gzip (default: xz)
#
# Requirements (Debian 13 / Ubuntu 24.04 host): live-build >= 1:20250505,
# debootstrap >= 1.0.141, debian-archive-keyring >= 2025.1, dpkg-dev, root.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/VERSION.sh"
export NOVAOS_VERSION NOVAOS_VERSION_ISO NOVAOS_CODENAME

WORKDIR="${NOVAOS_WORKDIR:-$ROOT/build/live}"
OUTDIR="${NOVAOS_OUTDIR:-$ROOT/build/out}"
DEBDIR="$ROOT/build/debs"

CLEAN=0; PURGE=0; SKIP_DEBS=0
for arg in "$@"; do
	case "$arg" in
		--clean) CLEAN=1 ;;
		--purge) PURGE=1 ;;
		--skip-debs) SKIP_DEBS=1 ;;
		-h|--help) sed -n '2,20p' "$0"; exit 0 ;;
		*) echo "Unknown option: $arg" >&2; exit 2 ;;
	esac
done

log() { printf '\033[1;35m[novaos]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[novaos] ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "live-build needs root. Run: sudo $0 $*"
"$ROOT/tools/check-host.sh" || die "host check failed (see above)"

if [ "$SKIP_DEBS" -eq 0 ]; then
	log "Building NovaOS packages (novaos-*.deb)"
	"$ROOT/tools/build-debs.sh" "$DEBDIR"
fi

if [ "$PURGE" -eq 1 ] && [ -d "$WORKDIR" ]; then
	log "Purging $WORKDIR"
	( cd "$WORKDIR" && lb clean --purge ) || true
	rm -rf "$WORKDIR"
fi

log "Preparing work directory $WORKDIR"
mkdir -p "$WORKDIR" "$OUTDIR"
# Sync the live-build tree, but keep the package cache and build state.
rsync -a --delete \
	--exclude '/cache/' --exclude '/chroot/' --exclude '/binary/' \
	--exclude '/.build/' --exclude '/build.log' --exclude '*.iso' \
	--exclude '/config/packages.chroot/*.deb' \
	--exclude '/config/binary' --exclude '/config/bootstrap' \
	--exclude '/config/chroot' --exclude '/config/common' --exclude '/config/source' \
	"$ROOT/live-build/" "$WORKDIR/"

# Empty directories are not stored in git (fresh checkouts lack them).
mkdir -p "$WORKDIR/config/packages.chroot" "$WORKDIR/config/archives" \
	"$WORKDIR/config/includes.chroot_after_packages"
rm -f "$WORKDIR"/config/packages.chroot/*.deb
if compgen -G "$DEBDIR/*.deb" >/dev/null; then
	cp "$DEBDIR"/*.deb "$WORKDIR/config/packages.chroot/"
	log "Including: $(cd "$WORKDIR/config/packages.chroot" && ls -- *.deb | tr '\n' ' ')"
else
	log "WARNING: no novaos-*.deb found - building a plain Debian/KDE image"
fi

cd "$WORKDIR"
if [ "$CLEAN" -eq 1 ] || [ -d chroot ]; then
	log "lb clean (package cache is kept)"
	lb clean
fi

log "lb config"
lb config

log "lb build (this takes a while; log: $WORKDIR/build.log)"
START=$(date +%s)
lb build
END=$(date +%s)

ISO=$(ls -t ./*.iso | head -n1)
[ -f "$ISO" ] || die "no ISO produced"
TARGET="$OUTDIR/novaos-${NOVAOS_VERSION}-amd64.iso"
mv -f "$ISO" "$TARGET"
cp -f "$WORKDIR"/*.packages "$OUTDIR/novaos-${NOVAOS_VERSION}-amd64.packages" 2>/dev/null || true
( cd "$OUTDIR" && sha256sum "$(basename "$TARGET")" > "$(basename "$TARGET").sha256" )

log "Done in $(( (END-START)/60 )) min: $TARGET ($(du -h "$TARGET" | cut -f1))"
log "SHA256: $(cut -d' ' -f1 "$TARGET.sha256")"
