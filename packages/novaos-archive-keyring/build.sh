#!/bin/bash
# Put the public NovaOS archive key into the package. The key exists only in the
# update-channel workflow (tools/pc_archive_key.sh); without it the package is skipped.
set -euo pipefail
STAGE="$1"
KEY="${NOVAOS_ARCHIVE_KEY:-}"
if [ -z "$KEY" ] || [ ! -s "$KEY" ]; then
	echo "    no NOVAOS_ARCHIVE_KEY - novaos-archive-keyring is not built"
	exit 77
fi
install -Dm0644 "$KEY" "$STAGE/usr/share/keyrings/novaos-archive-keyring.gpg"
