# NovaOS TV – die NovaOS-Oberfläche für Google TV / Android TV

**NovaOS TV** ist eine App für Fernseher mit **Google TV** oder **Android TV**
(z. B. Metz, Sony, Philips, TCL, Hisense mit Google TV) ab Android 7. Sie zeigt das
NovaOS-Design auf dem Fernseher:

* Startseite mit Aurora-Hintergrund, Uhrzeit, Datum und Netzwerkstatus
* **Favoriten** – eigene Lieblings-Apps, frei sortierbar
* **Alle Apps** als große Kacheln (mit den TV-Bannern der Apps), mit **App-Suche**
* **Einstellungen**: Netzwerk & WLAN, Bluetooth & Fernbedienungen, Bild, Ton,
  Datum & Uhrzeit, Speicher, alle Einstellungen
* Bedienung komplett mit der **Fernbedienung** (Pfeiltasten, OK, Zurück)
* Deutsch und Englisch

**Datenschutz:** Die App hat **keine Internet-Berechtigung**, sammelt keine Daten und
zeigt keine Werbung. Favoriten werden nur auf dem Fernseher gespeichert.

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
`apksigner` und führt dabei die Logik-Tests (`tv/test/`) aus. In GitHub Actions:
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
| `res/` | Layouts, Texte (de/en), Symbole, Banner, Hintergrund (aus `tools/make_assets.py`) |

Testergebnisse: siehe [docs/TESTBERICHT.md](../docs/TESTBERICHT.md), Abschnitt „NovaOS TV“.
