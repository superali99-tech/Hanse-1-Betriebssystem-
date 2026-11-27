#!/bin/bash
# Build the NovaOS Debian packages from packages/<name>/ into an output dir.
#
# Layout of a package source directory:
#   packages/<name>/DEBIAN/control      (Version: is filled in from VERSION.sh)
#   packages/<name>/DEBIAN/{postinst,prerm,postrm,triggers,conffiles}  optional
#   packages/<name>/root/...            files installed into /
#   packages/<name>/build.sh            optional: run before packing, gets the
#                                       staging root as $1 (e.g. to copy src/)
#
# Usage: ./tools/build-debs.sh [OUTDIR]   (default: build/debs)
# Env:   NOVAOS_DEB_VERSION  package version (default: NOVAOS_VERSION.<number of commits>,
#                            the same as the PC update channel, build-pc-update.yml)
#        NOVAOS_DEB_SKIP     package names to leave out, separated by spaces
# A package whose build.sh exits with 77 is skipped (e.g. no archive key given).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
. "$ROOT/VERSION.sh"
OUT="${1:-$ROOT/build/debs}"
# 0.1.1.<number of commits>: the same scheme for the ISO and the PC update channel, so that
# package relations (e.g. novaos-browser Breaks novaos-desktop << 0.1.1.92) fit both
DEB_VERSION="${NOVAOS_DEB_VERSION:-$NOVAOS_VERSION.$(git -C "$ROOT" rev-list --count HEAD 2>/dev/null || echo 0)}"
STAGE_BASE="$ROOT/build/deb-stage"

mkdir -p "$OUT"
rm -f "$OUT"/novaos-*.deb
rm -rf "$STAGE_BASE"
mkdir -p "$STAGE_BASE"

for src in "$ROOT"/packages/*/; do
	[ -f "$src/DEBIAN/control" ] || continue
	name="$(basename "$src")"
	case " ${NOVAOS_DEB_SKIP:-} " in *" $name "*) echo "==> $name (skipped)"; continue ;; esac
	stage="$STAGE_BASE/$name"
	echo "==> $name"
	mkdir -p "$stage"
	if [ -d "$src/root" ]; then
		cp -a "$src/root/." "$stage/"
	fi
	if [ -x "$src/build.sh" ]; then
		rc=0
		NOVAOS_ROOT="$ROOT" "$src/build.sh" "$stage" || rc=$?
		if [ "$rc" = 77 ]; then
			echo "    (skipped by build.sh)"
			continue
		fi
		[ "$rc" = 0 ] || exit "$rc"
	fi
	mkdir -p "$stage/DEBIAN"
	cp -a "$src/DEBIAN/." "$stage/DEBIAN/"
	sed -i "s/@VERSION@/${DEB_VERSION}/g" "$stage/DEBIAN/control"
	# Installed-Size in KiB
	size=$(du -sk --exclude=DEBIAN "$stage" | cut -f1)
	echo "Installed-Size: $size" >> "$stage/DEBIAN/control"
	# Maintainer scripts must be executable, data files must not be group-writable.
	for s in preinst postinst prerm postrm config; do
		[ -f "$stage/DEBIAN/$s" ] && chmod 0755 "$stage/DEBIAN/$s"
	done
	find "$stage" -path "$stage/DEBIAN" -prune -o -type d -exec chmod 0755 {} +
	# Every file below /etc is a conffile (local changes survive upgrades)
	if [ -d "$stage/etc" ]; then
		( cd "$stage" && find etc -type f | sed 's|^|/|' | sort ) >> "$stage/DEBIAN/conffiles"
		sort -u -o "$stage/DEBIAN/conffiles" "$stage/DEBIAN/conffiles"
	fi
	# Generate md5sums
	( cd "$stage" && find . -path ./DEBIAN -prune -o -type f -print0 | sort -z | \
		xargs -0 -r md5sum | sed 's| \./| |' > DEBIAN/md5sums )
	arch=$(awk -F': *' '$1=="Architecture"{print $2}' "$stage/DEBIAN/control")
	dpkg-deb --root-owner-group -Zxz --build "$stage" "$OUT/${name}_${DEB_VERSION}_${arch}.deb" >/dev/null
	echo "    -> $OUT/${name}_${DEB_VERSION}_${arch}.deb"
done

echo "Built $(ls "$OUT"/novaos-*.deb 2>/dev/null | wc -l) package(s) in $OUT"
