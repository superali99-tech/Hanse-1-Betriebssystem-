# NovaOS TV – die NovaOS-Oberfläche für Google TV / Android TV

**NovaOS TV** ist eine App für Fernseher mit **Google TV** oder **Android TV**
(z. B. Metz, Sony, Philips, TCL, Hisense mit Google TV) ab Android 7. Sie zeigt das
NovaOS-Design auf dem Fernseher:

* Startseite mit Aurora-Hintergrund, Uhrzeit, Datum und Netzwerkstatus
* **Favoriten** – eigene Lieblings-Apps, frei sortierbar
* **Fernsehen** – 30 Sender als Livestream über das Internet (Das Erste, ZDF, 3sat, ARTE,
  phoenix, tagesschau24, KiKA, alle Dritten …) mit eigenem Vollbild-Player, dazu
  **alle Sender, die der Fernseher selbst empfängt** (Antenne, Kabel, Satellit – auch
  RTL, ProSieben, Sat.1, VOX …)
* **Nova Filme** – kostenlose Spielfilme, Serien, Dokus und Kinderprogramm von
  offiziellen YouTube-Kanälen, abgespielt **direkt in NovaOS TV**
* **Alle Apps** als große Kacheln (mit den TV-Bannern der Apps), mit **App-Suche**
* **Einstellungen**: Netzwerk & WLAN, Bluetooth & Fernbedienungen, Bild, Ton,
  Datum & Uhrzeit, Speicher, alle Einstellungen
* Bedienung komplett mit der **Fernbedienung** (Pfeiltasten, OK, Zurück, P+/P−, Zifferntasten)
* Deutsch und Englisch

**Datenschutz:** Die App sammelt keine Daten und zeigt keine eigene Werbung. Internet
wird nur genutzt, wenn du einen Sender startest (der Stream kommt direkt vom Sender, z. B.
ARD oder ZDF) oder Nova Filme öffnest (Listen, Vorschaubilder und Videos kommen von YouTube,
im erweiterten Datenschutzmodus `youtube-nocookie.com` und mit der Werbung von YouTube).
Favoriten werden nur auf dem Fernseher gespeichert. Es gibt keinen eigenen Server und kein Konto.

> Ehrlicher Hinweis: NovaOS TV ist eine App, kein eigenes Fernseh-Betriebssystem.
> Das System des Fernsehers (Google TV / Android TV) bleibt unverändert; NovaOS TV
> lässt sich jederzeit wieder deinstallieren.

## Bedienung

| Taste | Funktion |
|---|---|
| Pfeiltasten | zwischen den Kacheln wechseln |
| **OK** | App oder Einstellung öffnen |
| **OK gedrückt halten** (oder Menütaste) | Optionen: zu Favoriten hinzufügen/entfernen, verschieben, App-Info, deinstallieren |
| **Zurück** | zurück / schließen |

Die erste Kachel in der Reihe „Apps“ ist die **Suche**: Namen mit der Bildschirmtastatur
(oder per Spracheingabe der Tastatur) eingeben.

## Fernsehen

**Internet-Sender:** In der Reihe „Fernsehen“ einen Sender wählen und **OK** drücken – er
läuft im Vollbild.

| Taste im Player | Funktion |
|---|---|
| **▲ / ▼** oder **P+ / P−** | nächster / vorheriger Sender |
| **Zifferntasten** | Sender direkt wählen (z. B. 1 2 = NDR Hamburg) |
| **OK** (oder Menü-/Guide-Taste) | Senderliste |
| **◀ / ▶** (oder Info) | Sendername einblenden |
| **Zurück** | Player beenden |

Senderliste: Das Erste (1), ZDF (2), 3sat, ARTE, phoenix, tagesschau24, ONE, ARD alpha,
ZDFneo, ZDFinfo, KiKA (11), NDR Hamburg (12) / Niedersachsen / Schleswig-Holstein /
Mecklenburg-Vorpommern, WDR, BR, hr, MDR, rbb, SWR, SR, Radio Bremen, WELT, DELUXE MUSIC
(Datei `res/raw/sender.m3u`). Es sind ausschließlich die offiziellen, kostenlosen Streams,
die die Sender selbst anbieten.

> **Ehrlich:** Die meisten deutschen Sender streamen im Internet **nur innerhalb
> Deutschlands** (Geoblocking) – im Ausland oder mit VPN zeigt der Player „nicht
> empfangbar“. Private Sender wie **RTL, ProSieben, Sat.1, VOX, kabel eins oder RTLZWEI**
> haben **keinen** kostenlosen Internet-Stream; ihre Streams sind verschlüsselt und nur in
> ihren eigenen Apps (RTL+, Joyn) erlaubt. Deshalb gibt es die zweite Möglichkeit:

**Sender vom Fernseher:** Die erste Kachel der Reihe („Sender vom Fernseher (Antenne ·
Kabel · Sat)“) zeigt **alle Sender, die der Fernseher selbst empfängt** – also auch alle
Privatsender. Beim ersten Öffnen fragt der Fernseher, ob NovaOS TV die Senderliste lesen
darf. Ein Sender öffnet sich im Live-TV des Fernsehers. Voraussetzung: ein Fernseher mit
eingebautem Empfänger (Metz, Sony, Philips … – nicht bei reinen Streaming-Sticks) und ein
Sendersuchlauf in den TV-Einstellungen. Hersteller-Live-TV-Apps, die ihre Senderliste nicht
an Android weitergeben, öffnen sich stattdessen über „Live-TV öffnen“.

## Nova Filme

Kostenlose **Spielfilme, Serien, Dokus und Kinderprogramm** von offiziellen YouTube-Kanälen –
abgespielt **direkt in NovaOS TV**, ohne in die YouTube-App zu wechseln. Die Reihen werden bei
jedem Öffnen aus den öffentlichen Kanal-Feeds aktualisiert (höchstens alle 3 Stunden, sonst aus
dem Zwischenspeicher – so erscheinen sie auch ohne Internet).

| Taste beim Film | Funktion |
|---|---|
| **OK** (oder Play/Pause) | Pause / weiter |
| **◀ / ▶** (oder Spulen) | 10 Sekunden zurück / vor |
| **▲ / ▼** (oder Info) | Infoleiste: Titel, Kanal, Zeit, Fortschritt |
| **Zurück** | Film beenden |

So ist das geregelt: Gespielt wird mit dem **offiziellen eingebetteten YouTube-Player**
(IFrame Player API), wie YouTube es für Apps erlaubt – mit YouTubes Werbung, damit die
Rechteinhaber bezahlt werden. Nach den YouTube-Regeln liegt nichts über dem Bild: Die
NovaOS-Infoleiste erscheint **unter** dem Film (das Bild wird dafür kurz etwas kleiner), die App
weist sich gegenüber YouTube mit `https://org.novaos.tv` aus, und beim Verlassen der App pausiert
der Film. Videos werden **nicht** an YouTube vorbei heruntergeladen.

| Reihe | Offizielle Kanäle |
|---|---|
| Spielfilme | Netzkino, Moviedome (lizenzierte Filme in voller Länge) |
| Serien | ganze Folgen von Netzkino, Moviedome, ZDFneo, funk |
| Dokus | Terra X History, ARTE, WDR Doku, NDR Doku |
| Kinder & Familie | TOGGO, PAW Patrol & Dino Ranch (TOGGO), Shaun das Schaf, Die Maus, Checker Welt |

Es werden **nur Kanäle der Rechteinhaber** gezeigt (Filmverleiher, Sender, Studios), keine Uploads
von Dritten, und nur Videos, deren Besitzer das Abspielen außerhalb von YouTube erlauben (die App
fragt das pro Video bei YouTube ab und merkt es sich). Die Biene Maja, ZDFtivi und KiKA erlauben
das nicht und fehlen deshalb. Sollte ein Video trotzdem gesperrt sein, bietet die App an, es in der
YouTube-App zu öffnen. Voraussetzung ist „Android System WebView“ (auf Google TV vorhanden). Die
Kanalliste steht in `src/org/novaos/tv/Catalog.java`.

## Installation auf dem Fernseher (z. B. Metz mit Google TV / Android TV)

NovaOS TV ist (noch) nicht im Google Play Store. Die App wird daher als Datei
(`novaos-tv-<version>.apk`) installiert. Das ist bei Google TV / Android TV vorgesehen,
muss aber einmal erlaubt werden.

### 1. APK herunterladen

Auf der GitHub-Seite des Projekts unter **Releases** das neueste Release
„NovaOS TV …“ öffnen und unter *Assets* die Datei **`novaos-tv-<version>.apk`**
herunterladen (am PC). Optional die Prüfsumme vergleichen (`.sha256`-Datei).

### 2. Auf einen USB-Stick kopieren

Die APK-Datei auf einen USB-Stick (FAT32 oder exFAT) kopieren und den Stick in den
USB-Anschluss des Fernsehers stecken.

### 3. Dateimanager auf dem Fernseher installieren

Im **Google Play Store des Fernsehers** einen Dateimanager installieren, zum Beispiel
**„File Commander“**, **„X-plore File Manager“** oder **„File Manager +“**.

### 4. Installation aus „unbekannten Quellen“ erlauben

* **Google TV** (neuere Metz-Modelle):
  1. *Einstellungen → System → Info* → siebenmal auf **„Android TV OS-Build“** klicken
     („Sie sind jetzt Entwickler“).
  2. *Einstellungen → Apps → Sicherheit & Einschränkungen → Unbekannte Quellen* →
     den installierten Dateimanager **erlauben**.
* **Android TV** (ältere Modelle): *Einstellungen → Geräteeinstellungen → Sicherheit &
  Einschränkungen → Unbekannte Quellen* → den Dateimanager erlauben.

(Die Menünamen können je nach Fernseher leicht abweichen. Oft fragt der Fernseher
beim ersten Öffnen der APK auch direkt nach dieser Erlaubnis.)

### 5. Installieren

Im Dateimanager den USB-Stick öffnen, **`novaos-tv-…apk`** auswählen →
**Installieren**. Danach erscheint **NovaOS TV** in der App-Liste des Fernsehers
(Google TV: *Apps → Ihre Apps*). Tipp: Die App dort in die Favoritenreihe verschieben.

Nach der Installation kann die Erlaubnis für unbekannte Quellen wieder
ausgeschaltet werden.

### Deinstallieren

*Einstellungen → Apps → NovaOS TV → Deinstallieren*.

## Updates und Signatur

Eine neue Version wird genauso installiert und ersetzt die alte, **wenn beide mit
demselben Schlüssel signiert sind**. Ist im Repository das Geheimnis
`NOVAOS_TV_KEYSTORE_B64` (+ `NOVAOS_TV_KS_PASS`) hinterlegt, signiert GitHub Actions
immer mit diesem festen Schlüssel. Ohne dieses Geheimnis bekommt jeder Build einen
eigenen, temporären Schlüssel – dann vor einem Update die alte Version deinstallieren
(Favoriten gehen dabei verloren). In den Release-Notizen steht, welcher Fall vorliegt.

Eigenen Schlüssel erzeugen (einmalig, gut aufbewahren – nie ins Repository!):

```bash
keytool -genkeypair -keystore novaos-tv.p12 -storetype PKCS12 -alias novaos \
  -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=NovaOS TV"
base64 -w0 novaos-tv.p12   # -> GitHub: Settings → Secrets → NOVAOS_TV_KEYSTORE_B64
```

## Bauen

```bash
# Android SDK: build-tools >= 34 und platforms;android-34 oder neuer, JDK 17+
ANDROID_HOME=/pfad/zum/sdk ./tv/build.sh     # -> build/tv/novaos-tv-<version>.apk
```

Das Skript baut ohne Gradle direkt mit `aapt2`, `javac`, `d8`, `zipalign` und
`apksigner` und führt dabei die Logik-Tests (`tv/test/`) aus. Die einzige Bibliothek ist
**Media3 ExoPlayer** (Apache-2.0, mit Guava) für die Livestreams: Versionen und SHA-256-Prüfsummen
stehen in `tv/libs.lock`, das Skript lädt sie beim ersten Bau nach `build/tv-libs/`. In GitHub Actions:
*Actions → Build NovaOS TV app → Run workflow* (veröffentlicht ein Release mit der APK).

## Aufbau

| Datei | Aufgabe |
|---|---|
| `AndroidManifest.xml` | TV-Startereintrag (Leanback) mit Banner, Berechtigungen, `<queries>` |
| `src/org/novaos/tv/MainActivity.java` | Startseite: Favoriten, Apps, Einstellungen, Uhr, Netzwerk |
| `AppRepository.java`, `AppEntry.java` | installierte Apps lesen (TV-Apps mit Banner bevorzugt) |
| `Tiles.java`, `FlowLayout.java` | Kacheln mit Fokusrahmen und Vergrößerung für die Fernbedienung |
| `SearchActivity.java`, `AppFilter.java` | App-Suche (unempfindlich für Groß-/Kleinschreibung und Umlaute) |
| `AppMenu.java`, `Favorites.java`, `KeyList.java` | Favoriten (nur lokal gespeichert) und App-Optionen |
| `AboutActivity.java` | Version, Gerät, Datenschutz, Favoriten zurücksetzen |
| `PlayerActivity.java`, `Channel.java`, `Channels.java`, `res/raw/sender.m3u` | Internet-Sender: Liste und Vollbild-Player (ExoPlayer, HLS) |
| `TunerActivity.java`, `TunerChannel.java` | Sender vom Fernseher (Android-Senderdatenbank, Live-TV-App) |
| `NovaFilmeActivity.java`, `Catalog.java`, `Video.java` | Nova Filme: Kanäle, Feeds, Reihen, kurze Titel |
| `VideoActivity.java`, `res/raw/youtube_player.html` | Nova Filme: eingebetteter YouTube-Player, Infoleiste, Fernbedienung |
| `Net.java`, `ImageLoader.java` | Laden von Feeds und Vorschaubildern mit Zwischenspeicher |
| `Rows.java` | Kachelreihen (Startseite, Nova Filme) |
| `res/` | Layouts, Texte (de/en), Symbole, Banner, Hintergrund (aus `tools/make_assets.py`) |

Testergebnisse: siehe [docs/TESTBERICHT.md](../docs/TESTBERICHT.md), Abschnitt „NovaOS TV“.
