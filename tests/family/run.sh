#!/bin/bash
# Nova Family test partner: tests/family/run.sh CA.crt COMMAND ARGS...  (see Peer.java)
# Needs the classes of mobile/build.sh and a relay (tests/family/fake_ntfy.py or ntfy.sh).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$(dirname "$HERE")")"
CA="$1"
shift
OUT="$ROOT/build/family-test"
if [ ! -f "$OUT/org/novaos/mobile/Peer.class" ] || [ "$HERE/Peer.java" -nt "$OUT/org/novaos/mobile/Peer.class" ]; then
	rm -rf "$OUT" && mkdir -p "$OUT"
	keytool -importcert -noprompt -alias novatest -file "$CA" -keystore "$OUT/trust.jks" -storepass changeit >/dev/null
	javac --release 11 -encoding UTF-8 -cp "$ROOT/build/mobile/classes" -d "$OUT" "$HERE/Peer.java"
fi
java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Djavax.net.ssl.trustStore="$OUT/trust.jks" -Djavax.net.ssl.trustStorePassword=changeit \
	-cp "$ROOT/build/mobile/classes:$OUT" org.novaos.mobile.Peer "$@"
