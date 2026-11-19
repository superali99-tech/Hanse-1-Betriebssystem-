#!/bin/bash
# Publishes the NovaOS Mobile software update to a container registry (GitHub: ghcr.io)
# as one OCI artifact with two files, update.properties and novaos-mobile.apk, under the
# tags "latest" and the version name. The phones read it anonymously once the package is
# public (see mobile/src/org/novaos/mobile/Registry.java) - no website needed.
#
# Usage: mobile/tools/publish_update.sh DIR      (DIR: build/mobile after mobile/build.sh)
# Env:   PACKAGE (e.g. superali99-tech/novaos-mobile), REGISTRY_USER, REGISTRY_TOKEN,
#        REGISTRY (default ghcr.io), SOURCE_URL (repository URL; links the package to it)
# Needs: curl, jq, sha256sum
set -euo pipefail
dir="$1"
REGISTRY="${REGISTRY:-ghcr.io}"
: "${PACKAGE:?}" "${REGISTRY_USER:?}" "${REGISTRY_TOKEN:?}"

props="$dir/update.properties"
apk_name="$(sed -n 's/^apk=//p' "$props")"
version="$(sed -n 's/^versionName=//p' "$props")"
src_apk="$(ls "$dir"/novaos-mobile-*.apk | head -n1)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp "$src_apk" "$work/$apk_name"
cp "$props" "$work/update.properties"
printf '{}' > "$work/config.json"
[ "$(sha256sum "$work/$apk_name" | cut -d' ' -f1)" = "$(sed -n 's/^sha256=//p' "$props")" ] \
	|| { echo "update.properties does not describe $src_apk" >&2; exit 1; }

token="$(curl -sSf -u "$REGISTRY_USER:$REGISTRY_TOKEN" \
	"https://$REGISTRY/token?scope=repository:$PACKAGE:pull,push&service=${REGISTRY%%:*}" | jq -r '.token // .access_token')"
[ -n "$token" ] && [ "$token" != null ] || { echo "no registry token" >&2; exit 1; }
api="https://$REGISTRY/v2/$PACKAGE"
auth=(-H "Authorization: Bearer $token")

# uploads a file (unless the registry has it already) and prints its digest
push_blob() {
	local f="$1" digest loc sep
	digest="sha256:$(sha256sum "$f" | cut -d' ' -f1)"
	if [ "$(curl -s -o /dev/null -w '%{http_code}' -I "${auth[@]}" "$api/blobs/$digest")" != 200 ]; then
		loc="$(curl -sSf -X POST -D - -o /dev/null "${auth[@]}" "$api/blobs/uploads/" | tr -d '\r' | sed -n 's/^[Ll]ocation: //p')"
		[ -n "$loc" ] || { echo "registry gave no upload location" >&2; return 1; }
		case "$loc" in http*) ;; *) loc="https://$REGISTRY$loc" ;; esac
		case "$loc" in *\?*) sep='&' ;; *) sep='?' ;; esac
		curl -sSf -X PUT "${auth[@]}" -H "Content-Type: application/octet-stream" --data-binary "@$f" \
			"$loc${sep}digest=$digest" -o /dev/null
	fi
	echo "$digest"
}

cfg="$(push_blob "$work/config.json")"
apk="$(push_blob "$work/$apk_name")"
upd="$(push_blob "$work/update.properties")"
jq -n --arg cfg "$cfg" --arg apk "$apk" --argjson apk_size "$(stat -c %s "$work/$apk_name")" --arg apk_name "$apk_name" \
	--arg upd "$upd" --argjson upd_size "$(stat -c %s "$work/update.properties")" \
	--arg version "$version" --arg src "${SOURCE_URL:-}" '{
	schemaVersion: 2,
	mediaType: "application/vnd.oci.image.manifest.v1+json",
	artifactType: "application/vnd.novaos.mobile.update.v1",
	config: {mediaType: "application/vnd.oci.empty.v1+json", digest: $cfg, size: 2},
	layers: [
		{mediaType: "application/vnd.android.package-archive", digest: $apk, size: $apk_size,
		 annotations: {"org.opencontainers.image.title": $apk_name}},
		{mediaType: "text/plain", digest: $upd, size: $upd_size,
		 annotations: {"org.opencontainers.image.title": "update.properties"}}
	],
	annotations: ({"org.opencontainers.image.description": "NovaOS Mobile software update",
	               "org.opencontainers.image.version": $version}
	              + (if $src != "" then {"org.opencontainers.image.source": $src} else {} end))
}' > "$work/manifest.json"
for tag in "$version" latest; do
	curl -sSf -X PUT "${auth[@]}" -H "Content-Type: application/vnd.oci.image.manifest.v1+json" \
		--data-binary "@$work/manifest.json" "$api/manifests/$tag" -o /dev/null
done
echo "published NovaOS Mobile $version to $REGISTRY/$PACKAGE (tags $version, latest)"
