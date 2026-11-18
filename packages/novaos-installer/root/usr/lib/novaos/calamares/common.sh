# Shared helpers for the NovaOS Calamares shellprocess jobs.
# $1 is the target root (${ROOT} from Calamares). As a fallback the root is
# detected from the mounts Calamares created (like Debian's helpers do).

target_root() {
	if [ -n "$1" ] && [ -d "$1/etc" ] && [ "$1" != '${ROOT}' ]; then
		echo "$1"
		return
	fi
	mount | grep proc | grep calamares | awk '{print $3}' | sed -e 's#/proc##g' | head -n1
}

live_medium() {
	for p in /run/live/medium /run/initramfs/live; do
		if mountpoint -q "$p"; then
			echo "$p"
			return
		fi
	done
}

log() { echo "[novaos-installer] $*"; }
