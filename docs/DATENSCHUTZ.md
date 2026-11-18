# Datenschutz in NovaOS

**Grundsatz: NovaOS enthält keine Telemetrie und sammelt keine Nutzungsdaten.**

## Welche Netzwerkverbindungen gibt es?

| Wann | Ziel | Zweck | Abschaltbar |
|---|---|---|---|
| täglich (apt-daily) | deb.debian.org, security.debian.org | Paketlisten, Sicherheitsupdates | Nova Updates → *automatisch installieren* |
| Nova Store geöffnet / alle 6 h | dl.flathub.org | App-Katalog, Screenshots, Updates | Store-Einstellungen |
| Nova Store, Detailseite | odrs.gnome.org | Bewertungen **lesen** (anonymer Zufalls-Hash, keine Geräte-ID) | Store-Einstellungen → *Bewertungen anzeigen* |
| Update-Benachrichtigung (täglich) | dl.flathub.org | prüfen, ob App-Updates vorliegen | `systemctl --user disable nova-update-notifier.timer` |
| Nova Browser | die Seiten, die Sie aufrufen; Mozilla für Sicherheitsdienste (Safe Browsing, Zertifikatsprüfungen) | | Firefox-Einstellungen |
| Installer | deb.debian.org (nur Erreichbarkeitsprüfung) | Anzeige „Internet verfügbar“ | – |

Es gibt **keine** GeoIP-Abfrage im Installer (Zeitzone wird manuell gewählt).

## Was ist abgeschaltet bzw. ausgeschlossen?

* Firefox ESR: `DisableTelemetry`, `DisableFirefoxStudies`, `DisablePocket`, keine
  Empfehlungen (Richtlinie in `/etc/firefox/policies/policies.json`)
* KDE: KUserFeedback auf Stufe 0 (`/etc/xdg/PlasmaUserFeedback`)
* `popularity-contest` und `python3-sentry-sdk` sind per apt-Pinning ausgeschlossen
* Baloo indiziert standardmäßig nur Dateinamen, keine Inhalte

Hinweis: KDEs Absturzassistent (DrKonqi) sendet Absturzberichte **nur**, wenn Sie das im
Dialog nach einem Absturz ausdrücklich auswählen.

## Daten der Nova-Apps

| App | Speicherort |
|---|---|
| Nova Notes | `~/Dokumente/Notizen/*.md` (normale Dateien) |
| Nova Calendar | `~/.local/share/nova-calendar/calendar.ics` |
| Nova Store | `~/.config/NovaOS/nova-store.conf`, Cache in `~/.cache/nova-store/` |
| Nova Welcome | `~/.config/NovaOS/nova-welcome.conf` |

Passwörter von Benutzerkonten werden ausschließlich als Hash (yescrypt/SHA-512) in
`/etc/shadow` gespeichert; WLAN-Passwörter im KDE-Passwortspeicher (KWallet) bzw. von
NetworkManager mit Root-Rechten.
