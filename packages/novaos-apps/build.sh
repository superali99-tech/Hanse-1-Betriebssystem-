#!/bin/bash
# Copy the NovaOS apps from src/ into the package staging root ($1).
set -euo pipefail
STAGE="$1"
SRC="$NOVAOS_ROOT/src"

PY="$STAGE/usr/lib/python3/dist-packages"
mkdir -p "$PY"
# Nova Disk (novaos/disk) is its own package, novaos-disk (NovaOS 2)
rsync -a --exclude '__pycache__' --exclude '*.pyc' --exclude 'novaos/disk' "$SRC/novaos" "$PY/"

mkdir -p "$STAGE/usr/bin"
for app in store:store updates:updates notes:notes calendar:calendar taskmanager:taskmanager \
           welcome:welcome help:help about:about wifi:wifi account:account; do
	name="${app%%:*}"; mod="${app##*:}"
	cat > "$STAGE/usr/bin/nova-$name" <<EOT
#!/usr/bin/python3
import sys
from novaos.$mod.main import main
sys.exit(main())
EOT
	chmod 0755 "$STAGE/usr/bin/nova-$name"
done

install -Dm0755 "$SRC/data/libexec/nova-apt-helper" "$STAGE/usr/libexec/novaos/nova-apt-helper"
install -Dm0755 "$SRC/data/libexec/nova-scheduled-update" "$STAGE/usr/libexec/novaos/nova-scheduled-update"
install -Dm0755 "$SRC/data/libexec/nova-webapp" "$STAGE/usr/libexec/novaos/nova-webapp"
# icons of the Nova Store web apps (own designs, no company logos)
install -Dm0644 -t "$STAGE/usr/share/icons/hicolor/scalable/apps" "$SRC"/data/icons/novaos-webapp-*.svg
install -Dm0644 "$SRC/data/etc/novaos/updates.conf" "$STAGE/etc/novaos/updates.conf"
install -Dm0644 "$SRC/data/whatsnew-pc.txt" "$STAGE/usr/share/novaos/whatsnew-pc.txt"
install -Dm0644 -t "$STAGE/usr/share/applications" "$SRC"/data/applications/*.desktop
# "Software-Update" in the KDE System Settings (category System), opens Nova Updates
install -Dm0644 -t "$STAGE/usr/share/plasma/systemsettings/externalmodules" "$SRC"/data/systemsettings/*.desktop
install -Dm0644 -t "$STAGE/etc/xdg/autostart" "$SRC"/data/autostart/*.desktop
install -Dm0644 -t "$STAGE/usr/share/polkit-1/actions" "$SRC"/data/polkit/*.policy
install -Dm0644 -t "$STAGE/usr/lib/systemd/user" "$SRC"/data/systemd/*
install -Dm0644 -t "$STAGE/usr/lib/systemd/system" "$SRC"/data/systemd-system/*
# offline updates: started whenever the system boots into system-update.target
mkdir -p "$STAGE/usr/lib/systemd/system/system-update.target.wants"
ln -sf ../novaos-offline-update.service "$STAGE/usr/lib/systemd/system/system-update.target.wants/novaos-offline-update.service"
python3 "$SRC/data/help/generate_help.py" >/dev/null
for lang in de en; do
	install -Dm0644 -t "$STAGE/usr/share/novaos/help/$lang" "$SRC"/data/help/$lang/*.html
done
