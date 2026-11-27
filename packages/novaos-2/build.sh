#!/bin/bash
# NovaOS 2: os-release of NovaOS 2 and the boot screen theme (script and logo from NovaOS 1,
# wordmark "NovaOS 2" and glow of NovaOS 2 - see artwork/generate_nova2.py).
set -euo pipefail
STAGE="$1"
. "$NOVAOS_ROOT/VERSION.sh"
mkdir -p "$STAGE/usr/lib/novaos"
cat > "$STAGE/usr/lib/novaos/os-release-2" <<EOT
PRETTY_NAME="NovaOS 2 (Nebula)"
NAME="NovaOS"
VERSION_ID="2"
VERSION="2 (Nebula)"
VERSION_CODENAME=${NOVAOS_DEBIAN_BASE}
ID=novaos
ID_LIKE=debian
HOME_URL="https://github.com/superali99-tech/Hanse-1-Betriebssystem-"
SUPPORT_URL="https://github.com/superali99-tech/Hanse-1-Betriebssystem-/issues"
BUG_REPORT_URL="https://github.com/superali99-tech/Hanse-1-Betriebssystem-/issues"
LOGO=novaos
NOVAOS_CODENAME="Nebula"
DEBIAN_BASE="${NOVAOS_DEBIAN_VERSION} (${NOVAOS_DEBIAN_BASE})"
EOT
P1="$NOVAOS_ROOT/packages/novaos-desktop/root/usr/share/plymouth/themes/novaos"
P2="$STAGE/usr/share/plymouth/themes/novaos2"
mkdir -p "$P2"
cp "$P1/logo.png" "$P1/dot.png" "$P2/"
cp "$P1/novaos.script" "$P2/novaos2.script"
cat > "$P2/novaos2.plymouth" <<EOT
[Plymouth Theme]
Name=NovaOS 2
Description=NovaOS 2 boot splash: logo, glow and "NovaOS 2"
ModuleName=script

[script]
ImageDir=/usr/share/plymouth/themes/novaos2
ScriptFile=/usr/share/plymouth/themes/novaos2/novaos2.script
EOT
