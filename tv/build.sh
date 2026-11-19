#!/bin/bash
# Build NovaOS TV (Android TV app, APK) with the plain Android SDK tools - no Gradle.
#
# Usage: tv/build.sh            -> build/tv/novaos-tv-<version>.apk (+ .sha256)
#
# Environment:
#   ANDROID_HOME                    Android SDK (build-tools >= 34, platforms;android-34 or newer)
#   NOVAOS_TV_BUILD                 build number, added to the versionCode (CI: run number)
#   NOVAOS_TV_KEYSTORE / _KS_PASS   release signing key (PKCS12/JKS, alias "novaos").
#                                   Without it a local development key is created in build/tv/.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
. "$ROOT/VERSION.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -n1)"; BT="${BT%/}"
PLATFORM=""
for p in $(ls -d "$SDK"/platforms/android-* 2>/dev/null | sed 's/.*android-//' | grep -E '^[0-9]+$' | sort -n); do
	[ "$p" -ge 34 ] && PLATFORM="$SDK/platforms/android-$p/android.jar"
done
[ -x "$BT/aapt2" ] || { echo "Android build-tools not found in $SDK" >&2; exit 1; }
[ -f "$PLATFORM" ] || { echo "Android platform >= 34 not found in $SDK" >&2; exit 1; }

OUT="$ROOT/build/tv"
rm -rf "$OUT/gen" "$OUT/classes" "$OUT/test-classes" "$OUT/dex" "$OUT"/*.apk
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/test-classes" "$OUT/dex"

# Libraries (Media3 ExoPlayer for the TV streams): pinned in tv/libs.lock, checked by
# SHA-256, cached in build/tv-libs/ (kept between builds).
LIBS="$ROOT/build/tv-libs"
mkdir -p "$LIBS"
JARS=()
while read -r sum url; do
	case "$sum" in ''|'#'*) continue ;; esac
	f="$LIBS/$(basename "$url")"
	if [ ! -f "$f" ] || ! echo "$sum  $f" | sha256sum -c --status; then
		echo "download $(basename "$url")"
		curl -fsSL --retry 3 -o "$f.part" "$url"
		echo "$sum  $f.part" | sha256sum -c --status || { echo "checksum mismatch: $url" >&2; exit 1; }
		mv "$f.part" "$f"
	fi
	case "$f" in
	*.aar)
		jar="${f%.aar}.jar"
		[ "$jar" -nt "$f" ] || unzip -p "$f" classes.jar > "$jar"
		JARS+=("$jar") ;;
	*) JARS+=("$f") ;;
	esac
done < "$HERE/libs.lock"
LIBCP="$(IFS=:; echo "${JARS[*]}")"

IFS=. read -r MA MI PA <<<"$NOVAOS_VERSION"
CODE=$(( (MA * 10000 + MI * 100 + PA) * 1000 + ${NOVAOS_TV_BUILD:-0} % 1000 ))
echo "NovaOS TV $NOVAOS_VERSION (versionCode $CODE), $(basename "$BT"), $(basename "$(dirname "$PLATFORM")")"

echo "== resources"
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLATFORM" --manifest "$HERE/AndroidManifest.xml" \
	--java "$OUT/gen" --min-sdk-version 24 --target-sdk-version 34 \
	--version-code "$CODE" --version-name "$NOVAOS_VERSION" "$OUT/res.zip"

echo "== java"
javac -nowarn --release 11 -encoding UTF-8 -classpath "$PLATFORM:$LIBCP" -d "$OUT/classes" \
	$(find "$HERE/src" "$OUT/gen" -name '*.java')

echo "== tests"
javac --release 11 -encoding UTF-8 -classpath "$OUT/classes" -d "$OUT/test-classes" $(find "$HERE/test" -name '*.java')
java -cp "$OUT/classes:$OUT/test-classes" org.novaos.tv.LogicTest "$HERE/res/raw/sender.m3u"

echo "== dex"
"$BT/d8" --release --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" \
	$(find "$OUT/classes" -name '*.class') "${JARS[@]}"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes*.dex )
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== sign"
KS="${NOVAOS_TV_KEYSTORE:-}"
PASS="${NOVAOS_TV_KS_PASS:-}"
if [ -z "$KS" ]; then
	KS="$OUT/novaos-tv-dev.keystore"
	PASS="novaos-dev"
	if [ ! -f "$KS" ]; then
		keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$PASS" -keypass "$PASS" \
			-alias novaos -keyalg RSA -keysize 3072 -validity 10000 \
			-dname "CN=NovaOS TV (development key)" >/dev/null 2>&1
		echo "created development signing key $KS"
	fi
fi
APK="$OUT/novaos-tv-$NOVAOS_VERSION.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" --ks-key-alias novaos --out "$APK" "$OUT/aligned.apk"
"$BT/apksigner" verify "$APK"
rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk" "$OUT/base.apk" "$APK.idsig"
( cd "$OUT" && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256" )
echo "== done: $APK ($(du -h "$APK" | cut -f1))"
