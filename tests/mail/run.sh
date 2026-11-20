#!/bin/bash
# Nova Mail against a real IMAP server: tests/mail/run.sh CA.crt
# Needs the classes of mobile/build.sh, Dovecot on 127.0.0.1:3993 (TLS) and :3143 (STARTTLS)
# with the users alice@nova.test / bob@nova.test, and tests/mail/smtp_server.py.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$(dirname "$HERE")")"
CA="$1"
OUT="$ROOT/build/mail-test"
rm -rf "$OUT" && mkdir -p "$OUT"
keytool -importcert -noprompt -alias novatest -file "$CA" -keystore "$OUT/trust.jks" -storepass changeit >/dev/null
javac --release 11 -encoding UTF-8 -cp "$ROOT/build/mobile/classes" -d "$OUT" "$HERE/MailIntegration.java"
java -Djavax.net.ssl.trustStore="$OUT/trust.jks" -Djavax.net.ssl.trustStorePassword=changeit \
	-cp "$ROOT/build/mobile/classes:$OUT" org.novaos.mobile.MailIntegration
