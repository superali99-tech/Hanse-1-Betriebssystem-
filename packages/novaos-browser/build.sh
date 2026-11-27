#!/bin/sh
# The NovaOS website (start page of Nova Browser, update details, guides) - works offline.
set -e
python3 "$NOVAOS_ROOT/website/build.py" "$1/usr/share/novaos/website" --version "${NOVAOS_DEB_VERSION:-}"
