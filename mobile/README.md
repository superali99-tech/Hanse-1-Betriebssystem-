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
* **Software-Update** in den NovaOS-Einstellungen: „Neues Update verfügbar“ (auch als Hinweis
  auf dem Startbildschirm) → *Herunterladen und installieren* → Android fragt einmal nach →
  NovaOS startet mit der **Start-Animation** neu und zeigt „Neu in NovaOS …“
* **NovaOS neu starten** (Einstellungen): startet die NovaOS-Oberfläche mit Animation neu, räumt
  temporäre Dateien auf und lädt die App-Liste frisch
* **Nova Store**: echte Apps – freie Apps (F-Droid) werden **direkt** heruntergeladen und
  installiert/aktualisiert (z. B. Öffi, Organic Maps, Thunderbird, Aegis, AntennaPod,
  Wikipedia); bekannte Apps (WhatsApp, Signal, DB Navigator, Netflix, Spotify …) öffnen ihre
  Seite im Google Play Store
* **Nova Kamera**: Fotos mit Vorschau, Blitz (Auto/An/Aus), Vorder-/Rückkamera, letztes Foto als
  Knopf zur Galerie, Symbole drehen sich mit dem Handy; Fotos landen in *DCIM/NovaOS*
* **Nova Galerie**: alle Fotos und Videos, neueste zuerst; Vollbild mit Wischen, Teilen, Löschen
* **Nova Dateien**: interner Speicher mit Abkürzungen (Downloads, Kamera, Bilder, Videos, Musik,
  Dokumente), Öffnen und Teilen mit anderen Apps, Umbenennen, Löschen, neuer Ordner
* **Nova Notizen**: Notizen mit Suche, automatisches Speichern, gedrückt halten = löschen
* Die Nova-Apps haben eigene Symbole und erscheinen in der App-Übersicht (und in jedem anderen
  Startbildschirm)
* Deutsch und Englisch, dunkles NovaOS-Design

> **Ehrlich:** NovaOS Mobile ist eine **Startbildschirm-App**, kein eigenes Handy-Betriebssystem.
> Ein komplett eigenes System („Custom ROM“) ginge nur für einzelne Handymodelle, braucht einen
> entsperrten Bootloader, löscht alle Daten und löst bei Samsung **Knox** dauerhaft aus (Samsung
> Pay, Secure Folder usw. funktionieren dann nicht mehr). Mit NovaOS Mobile bleibt Android darunter
> unverändert – mit allen Updates und Sicherheitsfunktionen – und du kannst jederzeit zurück.
> Deshalb startet „NovaOS neu starten“ und das Software-Update **die NovaOS-Oberfläche** neu (mit
> eigener Animation), nicht das ganze Handy – das darf nur Android selbst.

**Datenschutz:** keine Datensammlung, keine eigene Werbung. Internet nur für die Sender (Stream
direkt vom Sender), Nova Filme (Listen, Bilder und Videos von YouTube im erweiterten
Datenschutzmodus, mit YouTubes Werbung), den Nova Store (App-Liste und APKs von f-droid.org) und
das Software-Update (NovaOS-Website auf GitHub Pages). Favoriten, Notizen, Fotos und Dateien
bleiben auf dem Handy.

## Installieren (z. B. Samsung Galaxy)

1. Auf dem Handy **https://superali99-tech.github.io/Hanse-1-Betriebssystem-/mobile/** öffnen
   (sobald GitHub Pages eingeschaltet ist) und **„APK herunterladen“** tippen – oder die neueste
   Version unter **Releases** („NovaOS Mobile …“) laden: **`novaos-mobile-<version>.apk`**. (Das
   Repository ist privat: dazu im Browser des Handys bei GitHub angemeldet sein – oder die Datei
   am PC laden und per USB-Kabel aufs Handy kopieren.)
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

## Software-Update

NovaOS sucht alle 6 Stunden (und beim Öffnen von *Einstellungen → Software-Update*) nach einer
neuen Version. Die Beschreibung liegt auf der NovaOS-Website unter `mobile/update.properties`
(Versionsnummer, SHA-256, Größe, „Was ist neu“), die APK daneben als `mobile/novaos-mobile.apk`.
Beides baut der Workflow *NovaOS website (GitHub Pages)* bei jeder Änderung an `mobile/` neu.

Vor der Installation prüft NovaOS die **SHA-256-Prüfsumme** und ob das Update mit **demselben
Schlüssel** signiert ist wie das installierte NovaOS; Android fragt dann noch einmal nach
(„Aktualisieren?“). Danach startet NovaOS mit Animation neu und zeigt, was neu ist.

**Einmalig nötig, damit Updates funktionieren** (sonst signiert GitHub jeden Build mit einem
anderen Schlüssel und NovaOS meldet „mit einem anderen Schlüssel signiert“):

1. **GitHub Pages einschalten:** *Repository → Settings → Pages → Source: „GitHub Actions“*.
2. **Signierschlüssel hinterlegen:** einen Schlüssel erzeugen (am PC, Java nötig):
   ```bash
   keytool -genkeypair -keystore novaos-mobile.keystore -storetype PKCS12 -alias novaos \
       -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=NovaOS Mobile"
   base64 -w0 novaos-mobile.keystore > keystore.b64
   ```
   Dann unter *Settings → Secrets and variables → Actions* zwei **Repository secrets** anlegen:
   `NOVAOS_MOBILE_KEYSTORE_B64` = Inhalt von `keystore.b64`, `NOVAOS_MOBILE_KS_PASS` = das
   Passwort. Den Schlüssel gut aufbewahren – ohne ihn sind keine Updates mehr möglich.
3. Die erste Version mit diesem Schlüssel **einmal von Hand** installieren (vorher die alte
   deinstallieren). Ab dann kommen alle Updates über *Einstellungen → Software-Update*.

Die Versionsnummer ist `<NovaOS-Version>.<Build>` (z. B. 0.1.1.34), der Build ist die Zahl der
Commits – in jedem Workflow gleich, so erkennt das Handy, welche Version neuer ist.

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
| `SettingsActivity.java` | Software-Update, NovaOS neu starten, als Startbildschirm festlegen, Favoriten zurücksetzen, Über/Datenschutz |
| `Updates.java`, `UpdateActivity.java`, `UpdateInfo.java` | Software-Update: suchen, herunterladen, prüfen, installieren |
| `BootActivity.java` | Start-Animation nach Updates, beim ersten Start und für „NovaOS neu starten“; „Was ist neu“ |
| `Installer.java`, `InstallReceiver.java`, `Download.java` | Installieren über den Android-PackageInstaller, Downloads mit SHA-256 |
| `StoreActivity.java`, `StoreApp.java`, `res/raw/store.txt` | Nova Store: Katalog, F-Droid-Versionen, Installieren/Aktualisieren/Öffnen |
| `CameraActivity.java` | Nova Kamera (Camera2) |
| `GalleryActivity.java`, `ViewerActivity.java`, `Media.java` | Nova Galerie |
| `FilesActivity.java`, `FileNames.java`, `NovaFileProvider.java` | Nova Dateien, Öffnen/Teilen mit anderen Apps |
| `NotesActivity.java`, `NoteEditActivity.java`, `NotesDb.java` | Nova Notizen (SQLite auf dem Handy) |
| `web/index.html` | Download-Seite `…/mobile/` auf der NovaOS-Website |
| `tools/make_icons.py` | erzeugt die Symbole der Nova-Apps |
| `Greeting.java`, `StoreApp.java`, `UpdateInfo.java`, `FileNames.java` (+ `test/MobileTest.java`) | Logik ohne Android – getestet, auch der echte Store-Katalog |
