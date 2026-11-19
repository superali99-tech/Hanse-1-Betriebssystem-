#!/bin/bash
# Fixed signing key for NovaOS Mobile in GitHub Actions, without repository secrets.
# Android installs an update only if it is signed with the same key as the installed app,
# so every build must use the same key. It is created on the first run and kept as the
# file of a DRAFT release "NovaOS Mobile signing key": drafts are visible only to people
# with write access (also if the repository becomes public). Do not publish or delete it.
# (Repository secrets NOVAOS_MOBILE_KEYSTORE_B64 / NOVAOS_MOBILE_KS_PASS take precedence,
# see .github/workflows/build-mobile.yml.)
#
# Usage: mobile/tools/signing_key.sh OUTDIR   -> OUTDIR/novaos-mobile.keystore; sets
#        NOVAOS_MOBILE_KEYSTORE and NOVAOS_MOBILE_KS_PASS (masked) in $GITHUB_ENV.
# Env:   GH_TOKEN (contents: write), GITHUB_REPOSITORY, GITHUB_ENV. Needs gh, curl, jq, keytool.
set -euo pipefail
out="$1"
: "${GH_TOKEN:?}" "${GITHUB_REPOSITORY:?}" "${GITHUB_ENV:?}"
NAME="NovaOS Mobile signing key"
FILE="novaos-mobile-key.tar"
mkdir -p "$out"

# all releases, every page (drafts are listed for tokens with write access); a failing
# request stops the script, so a missing answer never leads to a second key
ids="$(gh api --paginate "repos/$GITHUB_REPOSITORY/releases?per_page=100" \
	--jq ".[] | select(.draft and .name == \"$NAME\") | .id")"
id="$(echo "$ids" | sed -n '1p')"
if [ -n "$id" ]; then
	asset="$(gh api "repos/$GITHUB_REPOSITORY/releases/$id/assets" --jq ".[] | select(.name == \"$FILE\") | .id")"
	[ -n "$asset" ] || { echo "draft release \"$NAME\" has no $FILE" >&2; exit 1; }
	curl -sSfL -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/octet-stream" \
		"https://api.github.com/repos/$GITHUB_REPOSITORY/releases/assets/$asset" -o "$out/$FILE"
	tar -xf "$out/$FILE" -C "$out"
	echo "using the NovaOS Mobile signing key of the draft release"
else
	pass="$(openssl rand -hex 24)"
	echo "::add-mask::$pass"
	keytool -genkeypair -keystore "$out/novaos-mobile.keystore" -storetype PKCS12 -storepass "$pass" -keypass "$pass" \
		-alias novaos -keyalg RSA -keysize 3072 -validity 18250 -dname "CN=NovaOS Mobile" >/dev/null 2>&1
	printf '%s' "$pass" > "$out/password"
	tar -cf "$out/$FILE" -C "$out" novaos-mobile.keystore password
	body="Signierschlüssel von NovaOS Mobile (vom Workflow erzeugt). NICHT veröffentlichen und NICHT löschen: ohne ihn können die Handys keine Updates mehr installieren. Entwürfe sieht nur, wer Schreibrechte am Repository hat."
	id="$(jq -n --arg n "$NAME" --arg b "$body" '{tag_name: "novaos-signing-key", name: $n, body: $b, draft: true}' \
		| gh api -X POST "repos/$GITHUB_REPOSITORY/releases" --input - --jq .id)"
	curl -sSf -X POST -H "Authorization: Bearer $GH_TOKEN" -H "Content-Type: application/x-tar" \
		--data-binary "@$out/$FILE" "https://uploads.github.com/repos/$GITHUB_REPOSITORY/releases/$id/assets?name=$FILE" -o /dev/null
	echo "created a new NovaOS Mobile signing key (draft release \"$NAME\")"
fi
pass="$(cat "$out/password")"
echo "::add-mask::$pass"
keytool -list -keystore "$out/novaos-mobile.keystore" -storepass "$pass" -alias novaos >/dev/null
echo "NOVAOS_MOBILE_KEYSTORE=$out/novaos-mobile.keystore" >> "$GITHUB_ENV"
echo "NOVAOS_MOBILE_KS_PASS=$pass" >> "$GITHUB_ENV"
rm -f "$out/$FILE" "$out/password"
