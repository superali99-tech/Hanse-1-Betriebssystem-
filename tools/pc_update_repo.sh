#!/bin/bash
# Turn a directory of NovaOS .deb files into the signed apt source of the PC update
# channel (a "flat" repository: Packages, Release, InRelease and the .debs side by side,
# published as the files of the GitHub release "pc-update"; sources line: "URI /").
#
# Usage: tools/pc_update_repo.sh DIR VERSION
# Env:   GNUPGHOME (with the secret archive key), NOVAOS_ARCHIVE_KEYID. Needs apt-ftparchive, gpg.
set -euo pipefail
dir="$1"; version="$2"
: "${NOVAOS_ARCHIVE_KEYID:?}"
cd "$dir"
ls ./*.deb >/dev/null
# Filename relative to the source URI (no "./": GitHub release files are a flat list)
apt-ftparchive packages . | sed 's|^Filename: \./|Filename: |' > Packages
gzip -9nkf Packages
apt-ftparchive \
	-o APT::FTPArchive::Release::Origin=NovaOS \
	-o APT::FTPArchive::Release::Label=NovaOS \
	-o APT::FTPArchive::Release::Suite=stable \
	-o APT::FTPArchive::Release::Codename=trixie \
	-o APT::FTPArchive::Release::Version="$version" \
	-o APT::FTPArchive::Release::Description="NovaOS $version - Nova apps, desktop and system settings" \
	release . > Release
gpg --batch --yes --local-user "$NOVAOS_ARCHIVE_KEYID" --clearsign -o InRelease Release
gpg --batch --yes --local-user "$NOVAOS_ARCHIVE_KEYID" -abs -o Release.gpg Release
echo "signed apt source for NovaOS $version in $dir:"
ls -l
