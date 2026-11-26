#!/bin/bash
# Fixed OpenPGP key that signs the NovaOS PC update channel (GitHub Actions, no secrets
# needed). Installed PCs trust only this key for the NovaOS source (novaos-archive-keyring),
# so every build must use the same one. It is created on the first run and kept as the
# file of a DRAFT release "NovaOS PC archive key": drafts are visible only to people with
# write access. Do not publish or delete it - without it the PCs get no NovaOS updates.
#
# Usage: tools/pc_archive_key.sh OUTDIR  -> OUTDIR/gnupg (secret key), OUTDIR/novaos-archive-keyring.gpg
#        (public key, binary) and OUTDIR/novaos-archive-key.asc; sets NOVAOS_ARCHIVE_GNUPGHOME,
#        NOVAOS_ARCHIVE_KEY and NOVAOS_ARCHIVE_KEYID in $GITHUB_ENV.
# Env:   GH_TOKEN (contents: write), GITHUB_REPOSITORY, GITHUB_ENV. Needs gh, curl, jq, gpg.
set -euo pipefail
out="$(mkdir -p "$1" && cd "$1" && pwd)"
: "${GH_TOKEN:?}" "${GITHUB_REPOSITORY:?}" "${GITHUB_ENV:?}"
NAME="NovaOS PC archive key"
FILE="novaos-pc-archive-key.asc"
export GNUPGHOME="$out/gnupg"
mkdir -p -m 0700 "$GNUPGHOME"

# all releases, every page; a failing request stops the script, so a missing answer
# never leads to a second key
ids="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" \
	--jq ".[] | select(.draft and .name == \"$NAME\") | .id")"
id="$(echo "$ids" | sed -n '1p')"
if [ -n "$id" ]; then
	asset="$(gh api "repos/$GITHUB_REPOSITORY/releases/$id/assets" --jq ".[] | select(.name == \"$FILE\") | .id")"
	[ -n "$asset" ] || { echo "draft release \"$NAME\" has no $FILE" >&2; exit 1; }
	curl -sSfL -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/octet-stream" \
		"https://api.github.com/repos/$GITHUB_REPOSITORY/releases/assets/$asset" -o "$out/$FILE"
	gpg --batch --quiet --import "$out/$FILE"
	echo "using the NovaOS PC archive key of the draft release"
else
	gpg --batch --quiet --passphrase '' --quick-gen-key "NovaOS Archive Signing Key" rsa4096 sign 0
	gpg --batch --armor --export-secret-keys > "$out/$FILE"
	body="Signierschlüssel des NovaOS-PC-Update-Kanals (vom Workflow erzeugt). NICHT veröffentlichen und NICHT löschen: ohne ihn bekommen die PCs keine NovaOS-Updates mehr. Entwürfe sieht nur, wer Schreibrechte am Repository hat."
	id="$(jq -n --arg n "$NAME" --arg b "$body" '{tag_name: "novaos-pc-archive-key", name: $n, body: $b, draft: true}' \
		| gh api -X POST "repos/$GITHUB_REPOSITORY/releases" --input - --jq .id)"
	curl -sSf -X POST -H "Authorization: Bearer $GH_TOKEN" -H "Content-Type: application/pgp-keys" \
		--data-binary "@$out/$FILE" "https://uploads.github.com/repos/$GITHUB_REPOSITORY/releases/$id/assets?name=$FILE" -o /dev/null
	echo "created a new NovaOS PC archive key (draft release \"$NAME\")"
fi
rm -f "$out/$FILE"
keyid="$(gpg --batch --with-colons --list-secret-keys | awk -F: '$1=="fpr"{print $10; exit}')"
[ -n "$keyid" ] || { echo "no secret key" >&2; exit 1; }
gpg --batch --export "$keyid" > "$out/novaos-archive-keyring.gpg"
gpg --batch --armor --export "$keyid" > "$out/novaos-archive-key.asc"
{
	echo "NOVAOS_ARCHIVE_GNUPGHOME=$GNUPGHOME"
	echo "NOVAOS_ARCHIVE_KEY=$out/novaos-archive-keyring.gpg"
	echo "NOVAOS_ARCHIVE_KEYID=$keyid"
} >> "$GITHUB_ENV"
echo "archive key $keyid"
