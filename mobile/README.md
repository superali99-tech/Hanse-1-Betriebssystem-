# NovaOS Mobile – NovaOS fürs Handy

**NovaOS Mobile** bringt NovaOS auf **Android-Handys und -Tablets** (Samsung Galaxy, Google
Pixel, Xiaomi, Motorola … ab Android 7). Es ersetzt den **Startbildschirm** – so wie Samsung
selbst „One UI Home“ oder andere Hersteller ihre Oberflächen über Android legen:

* **Startbildschirm**: Begrüßung, große Uhr und Datum, **App-Suche**, Aurora-Hintergrund
* **Nova**: Karten für **Fernsehen** (30 Sender live: Das Erste, ZDF, ARTE, alle Dritten …) und
  **Nova Filme** (kostenlose Filme, Serien, Dokus, Kinderprogramm – direkt in der App)
* **Schnelleinstellungen**: WLAN, Bluetooth, **Taschenlampe**, Anzeige, Ton, Akku, Einstellungen
* **Favoriten** (App in der App-Übersicht gedrückt halten) und ein **Dock** mit Telefon,
  Nachrichten, App-Übersicht, Kamera und Browser des Handys
* **Gesten**: nach oben wischen = alle Apps, nach unten wischen = Benachrichtigungen
* **App-Übersicht** mit Suche (auch ohne Umlaute: „grosse“ findet „Größe“), gedrückt halten =
  Favorit, App-Info, Deinstallieren
* **Fernsehen** im Vollbild: antippen = Bedienleiste, nach links/rechts wischen = Sender wechseln,
  Listen-Knopf = alle Sender
* **Nova Filme**: im offiziellen YouTube-Player; Handy quer drehen = Vollbild
* Deutsch und Englisch, dunkles NovaOS-Design

> **Ehrlich:** NovaOS Mobile ist eine **Startbildschirm-App**, kein eigenes Handy-Betriebssystem.
> Ein komplett eigenes System („Custom ROM“) ginge nur für einzelne Handymodelle, braucht einen
> entsperrten Bootloader, löscht alle Daten und löst bei Samsung **Knox** dauerhaft aus (Samsung
> Pay, Secure Folder usw. funktionieren dann nicht mehr). Mit NovaOS Mobile bleibt Android darunter
> unverändert – mit allen Updates und Sicherheitsfunktionen – und du kannst jederzeit zurück.

**Datenschutz:** keine Datensammlung, keine eigene Werbung. Internet nur für die Sender (Stream
direkt vom Sender) und Nova Filme (Listen, Bilder und Videos von YouTube im erweiterten
Datenschutzmodus, mit YouTubes Werbung). Favoriten bleiben auf dem Handy.

## Installieren (z. B. Samsung Galaxy)

1. Auf dem Handy die neueste Version unter **Releases** öffnen („NovaOS Mobile …“) und
   **`novaos-mobile-<version>.apk`** herunterladen. (Das Repository ist privat: dazu im Browser
   des Handys bei GitHub angemeldet sein – oder die Datei am PC laden und per USB-Kabel aufs
   Handy kopieren.)
2. **Nur Samsung (One UI 6 und neuer):** *Einstellungen → Sicherheit und Datenschutz →
   Auto Blocker* vorübergehend **ausschalten** – sonst blockiert Samsung jede App, die nicht aus
   einem Store kommt.
3. Die heruntergeladene Datei antippen (oder in *Eigene Dateien → Downloads*) → beim ersten Mal
   „**Unbekannte Apps installieren**“ für den Browser bzw. „Eigene Dateien“ **erlauben** →
   **Installieren**. Warnt Google Play Protect („App nicht geprüft“): *Weitere Details →
   Trotzdem installieren*.
4. **NovaOS** öffnen → *Einstellungen* (Zahnrad oben rechts) → **„NovaOS als Startbildschirm
   verwenden“** → **NovaOS** wählen. Alternativ die Home-Taste drücken und „NovaOS“ → „Immer“.
5. Danach Auto Blocker wieder einschalten.

**Zurück zum alten Startbildschirm:** *Android-Einstellungen → Apps → Standard-Apps →
Startbildschirm* (Samsung: „One UI Home“ wählen). **Deinstallieren:** *Einstellungen → Apps →
NovaOS → Deinstallieren*.

**Updates:** Eine neue Version wird genauso installiert. Solange GitHub jeden Build mit einem eigenen
Schlüssel signiert (kein Geheimnis `NOVAOS_MOBILE_KEYSTORE_B64` hinterlegt), vorher die alte
Version deinstallieren (Favoriten gehen dabei verloren).

## Bauen

```bash
ANDROID_HOME=/pfad/zum/sdk ./mobile/build.sh     # -> build/mobile/novaos-mobile-<version>.apk
```

Wie NovaOS TV ohne Gradle (`aapt2`, `javac`, `d8`, `apksigner`); in GitHub Actions: *Actions →
Build NovaOS Mobile app → Run workflow* (veröffentlicht ein Release mit der APK).

NovaOS Mobile **teilt Code und Grafiken mit NovaOS TV** (`tv/`): Senderliste und Player-Logik
(`Channel`, `res/raw/sender.m3u`, Media3 ExoPlayer aus `tv/libs.lock`), Nova Filme (`Catalog`,
`Video`, `Net`, `ImageLoader`, `res/raw/youtube_player.html`), App-Suche und Favoriten
(`AppFilter`, `KeyList`), Symbole und App-Icon.

| Datei | Aufgabe |
|---|---|
| `HomeActivity.java` | Startbildschirm: Uhr, Suche, Nova-Karten, Schnelleinstellungen, Favoriten, Dock, Gesten |
| `AppsActivity.java`, `Apps.java`, `AppMenu.java`, `Favorites.java` | App-Übersicht, Suche, Favoriten, App-Optionen |
| `TvActivity.java`, `PlayerActivity.java`, `Channels.java` | Fernsehen: Senderkacheln, Touch-Player |
| `FilmeActivity.java`, `VideoActivity.java` | Nova Filme: Reihen, eingebetteter YouTube-Player |
| `SettingsActivity.java` | Als Startbildschirm festlegen, Favoriten zurücksetzen, Über/Datenschutz |
| `Greeting.java` (+ `test/MobileTest.java`) | Begrüßung nach Uhrzeit, Spaltenzahl – getestet |
