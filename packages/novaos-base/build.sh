#!/bin/bash
# Generate /usr/lib/novaos/os-release from VERSION.sh (single version source).
set -e
STAGE="$1"
. "$NOVAOS_ROOT/VERSION.sh"
mkdir -p "$STAGE/usr/lib/novaos"
cat > "$STAGE/usr/lib/novaos/os-release" <<EOT
PRETTY_NAME="NovaOS ${NOVAOS_VERSION} (${NOVAOS_CODENAME})"
NAME="NovaOS"
VERSION_ID="${NOVAOS_VERSION}"
VERSION="${NOVAOS_VERSION} (${NOVAOS_CODENAME})"
VERSION_CODENAME=${NOVAOS_DEBIAN_BASE}
ID=novaos
ID_LIKE=debian
HOME_URL="https://github.com/superali99-tech/Hanse-1-Betriebssystem-"
SUPPORT_URL="https://github.com/superali99-tech/Hanse-1-Betriebssystem-/issues"
BUG_REPORT_URL="https://github.com/superali99-tech/Hanse-1-Betriebssystem-/issues"
LOGO=novaos
NOVAOS_CODENAME="${NOVAOS_CODENAME}"
DEBIAN_BASE="${NOVAOS_DEBIAN_VERSION} (${NOVAOS_DEBIAN_BASE})"
EOT
