#!/bin/bash
# Fill in the NovaOS version in the Calamares branding.
set -e
. "$NOVAOS_ROOT/VERSION.sh"
sed -i "s/@VERSION@/${NOVAOS_VERSION}/g" "$1/etc/calamares/branding/novaos/branding.desc"
