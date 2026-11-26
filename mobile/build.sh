#!/bin/bash
# Build NovaOS Mobile (home screen / launcher app for Android phones, APK) - no Gradle.
#
# Usage: mobile/build.sh         -> build/mobile/novaos-mobile-<version>.apk (+ .sha256)
#
# Shares code and resources with NovaOS TV (tv/): channel list and player logic, Nova Filme
# (Catalog, Video, Net, ImageLoader), app search and favorites (AppFilter, KeyList), icons,
# res/raw/sender.m3u and the YouTube player page. Libraries: tv/libs.lock (Media3).
# Environment: ANDROID_HOME, NOVAOS_MOBILE_BUILD (CI run number),
#              NOVAOS_MOBILE_KEYSTORE / NOVAOS_MOBILE_KS_PASS (release key, alias "novaos").
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
TV="$ROOT/tv"
. "$ROOT/VERSION.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -n1)"; BT="${BT%/}"
PLATFORM=""
for p in $(ls -d "$SDK"/platforms/android-* 2>/dev/null | sed 's/.*android-//' | grep -E '^[0-9]+$' | sort -n); do
	[ "$p" -ge 34 ] && PLATFORM="$SDK/platforms/android-$p/android.jar"
done
[ -x "$BT/aapt2" ] || { echo "Android build-tools not found in $SDK" >&2; exit 1; }
[ -f "$PLATFORM" ] || { echo "Android platform >= 34 not found in $SDK" >&2; exit 1; }

OUT="$ROOT/build/mobile"
rm -rf "$OUT/gen" "$OUT/classes" "$OUT/test-classes" "$OUT/dex" "$OUT/flat" "$OUT"/*.apk
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/test-classes" "$OUT/dex" "$OUT/flat"

# libraries: same pinned list as NovaOS TV
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
done < "$TV/libs.lock"
LIBCP="$(IFS=:; echo "${JARS[*]}")"

IFS=. read -r MA MI PA <<<"$NOVAOS_VERSION"
# build number = number of commits, the same in every workflow (checkout with full history),
# so the software update can tell which build is newer
BUILD="${NOVAOS_MOBILE_BUILD:-$(git -C "$ROOT" rev-list --count HEAD 2>/dev/null || echo 0)}"
CODE=$(( (MA * 10000 + MI * 100 + PA) * 100000 + BUILD % 100000 ))
NAME="$NOVAOS_VERSION.$BUILD"
echo "NovaOS Mobile $NAME (versionCode $CODE), $(basename "$BT"), $(basename "$(dirname "$PLATFORM")")"

echo "== resources"
# shared from NovaOS TV: icons, logo, launcher icon, colours, channel list, YouTube player page
for f in "$TV"/res/drawable/ic_*.xml "$TV"/res/drawable/list_item_bg.xml "$TV"/res/drawable/panel_bg.xml \
	"$TV"/res/drawable-nodpi/nova_logo.png "$TV"/res/mipmap-*/* "$TV"/res/raw/* "$TV"/res/values/colors.xml; do
	"$BT/aapt2" compile "$f" -o "$OUT/flat/"
done
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLATFORM" --manifest "$HERE/AndroidManifest.xml" \
	--java "$OUT/gen" --min-sdk-version 24 --target-sdk-version 34 \
	--version-code "$CODE" --version-name "$NAME" "$OUT"/flat/*.flat "$OUT/res.zip"

echo "== java"
SHARED=(Channel Catalog Video Net ImageLoader KeyList AppFilter)
javac -nowarn --release 11 -encoding UTF-8 -classpath "$PLATFORM:$LIBCP" -d "$OUT/classes" \
	$(for c in "${SHARED[@]}"; do echo "$TV/src/org/novaos/tv/$c.java"; done) \
	$(find "$HERE/src" "$OUT/gen" -name '*.java')

echo "== tests"
javac --release 11 -encoding UTF-8 -classpath "$OUT/classes" -d "$OUT/test-classes" $(find "$HERE/test" -name '*.java')
java -cp "$OUT/classes:$OUT/test-classes" org.novaos.mobile.MobileTest "$HERE/res"

echo "== dex"
"$BT/d8" --release --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" \
	$(find "$OUT/classes" -name '*.class') "${JARS[@]}"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes*.dex )
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== sign"
KS="${NOVAOS_MOBILE_KEYSTORE:-}"
PASS="${NOVAOS_MOBILE_KS_PASS:-}"
if [ -z "$KS" ]; then
	KS="$OUT/novaos-mobile-dev.keystore"
	PASS="novaos-dev"
	if [ ! -f "$KS" ]; then
		keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass "$PASS" -keypass "$PASS" \
			-alias novaos -keyalg RSA -keysize 3072 -validity 10000 \
			-dname "CN=NovaOS Mobile (development key)" >/dev/null 2>&1
		echo "created development signing key $KS"
	fi
fi
APK="$OUT/novaos-mobile-$NOVAOS_VERSION.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" --ks-key-alias novaos --out "$APK" "$OUT/aligned.apk"
"$BT/apksigner" verify "$APK"
rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk" "$OUT/base.apk" "$APK.idsig"
( cd "$OUT" && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256" )

# description for the software update (published with the APK as novaos-mobile.apk on the website)
python3 - "$HERE/res/raw/whatsnew.txt" "$CODE" "$NAME" "$(cut -d' ' -f1 "$APK.sha256")" "$(stat -c %s "$APK")" \
	> "$OUT/update.properties" <<'PY'
import sys
news, code, name, sha, size = sys.argv[1:]
def esc(t):  # Java properties: ASCII with \\uXXXX (UTF-16 units)
	b = t.encode("utf-16-be")
	units = [int.from_bytes(b[i:i + 2], "big") for i in range(0, len(b), 2)]
	return "".join(chr(u) if 32 <= u < 127 else "\\u%04x" % u for u in units)
print("# NovaOS Mobile software update")
print(f"versionCode={code}\nversionName={name}\napk=novaos-mobile.apk\nsha256={sha}\nsize={size}")
lines = [l.strip() for l in open(news, encoding="utf-8") if l.strip() and not l.startswith("#")]
apps = [a.strip() for l in lines if l.startswith("@apps:") for a in l[6:].split(",") if a.strip()]
rel = [l[9:].strip() for l in lines if l.startswith("@release:")]
if rel:  # a big update with a name ("1.5"): shown instead of the long number
	print("release=" + rel[0])
if any(l.startswith("@store-only") for l in lines) and apps:  # only Nova apps change: in the Nova Store, no software update
	print("storeOnly=true")
if apps:  # the Nova apps this update changes (Nova Store: "Aktualisieren")
	print("apps=" + ",".join(apps))
lines = [l for l in lines if not l.startswith("@")]
import re
per_app = {}
for i, line in enumerate(lines[:20], 1):
	m = re.match(r"^\{([a-z, ]+)\}\s*(.*)$", line)  # "{chat,mail} text": lines of single Nova apps
	text = m.group(2) if m else line
	de, _, en = text.partition("|")
	print(f"note.de.{i}={esc(de.strip())}")
	print(f"note.en.{i}={esc((en or de).strip())}")
	for key in ([k.strip() for k in m.group(1).split(",") if k.strip()] if m else []):
		per_app.setdefault(key, []).append((de.strip(), (en or de).strip()))
for key, notes in per_app.items():
	for j, (de, en) in enumerate(notes[:10], 1):
		print(f"appnote.{key}.de.{j}={esc(de)}")
		print(f"appnote.{key}.en.{j}={esc(en)}")
PY
echo "== done: $APK ($(du -h "$APK" | cut -f1)), versionCode $CODE"
