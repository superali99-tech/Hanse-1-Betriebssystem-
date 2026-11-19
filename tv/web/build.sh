#!/bin/bash
# Build NovaOS TV Web (static website for the web browser of any smart TV).
#
# Usage: tv/web/build.sh            -> build/tv-web/ (index.html, app.js, data/*.json, ...)
#
# The data (channel list, Nova Filme rows) is built with the Java logic of the Android app
# (Channel, Catalog, Video + tools/ExportData.java); TV browsers may not read the YouTube
# feeds themselves. Needs: JDK 11+, curl, internet (YouTube feeds).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TV="$(dirname "$HERE")"
ROOT="$(dirname "$TV")"
OUT="$ROOT/build/tv-web"
LIBS="$ROOT/build/tv-libs"
rm -rf "$OUT" "$ROOT/build/tv-web-classes"
mkdir -p "$OUT/data" "$OUT/img" "$LIBS" "$ROOT/build/tv-web-classes"

echo "== data"
javac -nowarn --release 11 -encoding UTF-8 -d "$ROOT/build/tv-web-classes" \
	"$TV/src/org/novaos/tv/Channel.java" "$TV/src/org/novaos/tv/Catalog.java" \
	"$TV/src/org/novaos/tv/Video.java" "$HERE/tools/ExportData.java"
java -cp "$ROOT/build/tv-web-classes" org.novaos.tv.ExportData "$TV/res/raw/sender.m3u" "$OUT/data"

echo "== site"
cp "$HERE/index.html" "$HERE/app.js" "$HERE/style.css" "$OUT/"
cp "$TV/res/drawable-nodpi/nova_logo.png" "$TV/res/drawable-nodpi/wallpaper.jpg" "$OUT/img/"
# hls.js (Apache-2.0) plays the HLS live streams in browsers without native HLS
HLS_URL="https://cdn.jsdelivr.net/npm/hls.js@1.7.3/dist/hls.min.js"
HLS_SHA="a12e7ee1cd64a69dcdb314157e45dafcba705bfb0b1440b7935cb265d374423e"
HLS="$LIBS/hls-1.7.3.min.js"
if [ ! -f "$HLS" ] || ! echo "$HLS_SHA  $HLS" | sha256sum -c --status; then
	curl -fsSL --retry 3 -o "$HLS.part" "$HLS_URL"
	echo "$HLS_SHA  $HLS.part" | sha256sum -c --status || { echo "checksum mismatch: $HLS_URL" >&2; exit 1; }
	mv "$HLS.part" "$HLS"
fi
cp "$HLS" "$OUT/hls.min.js"
touch "$OUT/.nojekyll"
echo "== done: $OUT ($(du -sh "$OUT" | cut -f1))"
