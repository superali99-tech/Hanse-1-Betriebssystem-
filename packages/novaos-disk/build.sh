#!/bin/bash
# Nova Disk: the Python module, the starter, the privileged helper, menu entry, icon, polkit.
set -euo pipefail
STAGE="$1"
SRC="$NOVAOS_ROOT/src"
PY="$STAGE/usr/lib/python3/dist-packages/novaos"
mkdir -p "$PY"
rsync -a --exclude '__pycache__' --exclude '*.pyc' "$SRC/novaos/disk" "$PY/"
mkdir -p "$STAGE/usr/bin"
cat > "$STAGE/usr/bin/nova-disk" <<'EOT'
#!/usr/bin/python3
import sys
from novaos.disk.main import main
sys.exit(main())
EOT
chmod 0755 "$STAGE/usr/bin/nova-disk"
install -Dm0755 "$SRC/data/libexec/nova-disk-helper" "$STAGE/usr/libexec/novaos/nova-disk-helper"
install -Dm0644 "$SRC/data/disk/org.novaos.Disk.desktop" "$STAGE/usr/share/applications/org.novaos.Disk.desktop"
install -Dm0644 "$SRC/data/disk/org.novaos.disk.policy" "$STAGE/usr/share/polkit-1/actions/org.novaos.disk.policy"
install -Dm0644 "$SRC/data/icons/nova-disk.svg" "$STAGE/usr/share/icons/hicolor/scalable/apps/nova-disk.svg"
