# NovaOS TV Web – für Samsung, Philips, LG und jeden anderen Fernseher

Die **APK-Datei** von NovaOS TV ist eine **Android-App**. Sie läuft nur auf Fernsehern mit
**Google TV / Android TV** (z. B. Metz, Sony, viele Philips bis 2023, TCL). Deshalb blenden
andere Fernseher die Datei aus:

| Fernseher | System | APK möglich? | Lösung |
|---|---|---|---|
| **Samsung** | Tizen | nein | **NovaOS TV Web** im Internet-Browser des Fernsehers |
| **Philips** mit Android TV / Google TV | Android | **ja** | APK mit Dateimanager-App installieren (siehe unten) |
| **Philips** mit Saphi oder Titan OS | eigenes System | nein | NovaOS TV Web im Browser (falls der Fernseher einen hat) |
| **LG** | webOS | nein | NovaOS TV Web im Browser |

**NovaOS TV Web** ist dieselbe Oberfläche als Webseite: **Fernsehen** (30 Sender als Livestream)
und **Nova Filme** (kostenlose Filme, Serien, Dokus und Kinderprogramm von offiziellen
YouTube-Kanälen, direkt auf der Seite abgespielt). Nichts muss installiert werden.

## Öffnen auf dem Fernseher

1. Auf dem Fernseher die App **„Internet“** (Samsung) bzw. den **Browser** öffnen.
2. Die Adresse eingeben: **`https://superali99-tech.github.io/Hanse-1-Betriebssystem-/tv/`**
   (die Adresse zeigt GitHub nach der Einrichtung unter *Settings → Pages* an).
3. Als **Lesezeichen / Favorit** speichern – beim nächsten Mal genügt ein Klick.

Bedienung: **Pfeiltasten** und **OK** der Fernbedienung (oder der Zeiger der Fernbedienung).
Im Sender-Player: **▲ ▼** bzw. **P+ / P−** wechseln den Sender, **Zifferntasten** wählen direkt
(z. B. 1 2 = NDR Hamburg), **OK** öffnet die Senderliste, **Zurück** beendet. Beim Film:
**OK** = Pause / weiter, **◀ ▶** = 10 Sekunden, **Zurück** = beenden. Alle Funktionen gibt es
zusätzlich als Schaltflächen zum Anklicken.

Hinweise: Die Livestreams der meisten Sender laufen nur in Deutschland. Sehr alte
Fernseher-Browser (vor etwa 2018) können die Streams eventuell nicht abspielen. Private Sender
(RTL, ProSieben, Sat.1 …) gibt es nicht kostenlos im Internet – die laufen über den Empfänger
des Fernsehers.

## Philips mit Android TV / Google TV: die APK installieren

1. Prüfen: *Einstellungen → Info / Systeminformationen* – steht dort **Android TV** oder
   **Google TV**, geht die APK. (Bei **Saphi** oder **Titan OS** nicht.)
2. Der eingebaute USB-/Medien-Browser des Fernsehers zeigt APK-Dateien **nie** an – das ist normal.
   Im **Google Play Store** des Fernsehers einen Dateimanager installieren, z. B. **„File Commander“**
   oder **„X-plore File Manager“**.
3. Im Dateimanager den USB-Stick öffnen → `novaos-tv-….apk` → **Installieren** (beim ersten Mal
   „Unbekannte Apps installieren“ für den Dateimanager erlauben).

## Wie die Webseite entsteht (für Entwickler)

```bash
tv/web/build.sh        # -> build/tv-web/  (index.html, app.js, style.css, hls.min.js, data/*.json)
```

* `index.html`, `style.css`, `app.js` – die Oberfläche (ES5, damit auch ältere TV-Browser sie können)
* `tools/ExportData.java` – erzeugt `data/sender.json` (aus `tv/res/raw/sender.m3u`) und
  `data/filme.json` (YouTube-Feeds, Einbett-Prüfung) mit **derselben Logik wie die Android-App**
  (`Channel`, `Catalog`, `Video`). TV-Browser dürfen die YouTube-Feeds nicht selbst abrufen (CORS),
  deshalb kommen die Daten als Dateien mit der Webseite.
* [hls.js](https://github.com/video-dev/hls.js) 1.7.3 (Apache-2.0) spielt die Livestreams in Browsern
  ohne eingebautes HLS; Prüfsumme in `build.sh`.
* Filme laufen im **offiziellen eingebetteten YouTube-Player** (Datenschutzmodus
  `youtube-nocookie.com`); nach den YouTube-Regeln liegt nichts über dem Bild – die NovaOS-Leiste mit
  Titel, Zeit und Schaltflächen ist unter dem Video.
* GitHub Actions (`.github/workflows/tv-web.yml`, „NovaOS website“) baut die Seite **alle 3 Stunden neu** (unter `/tv/` der NovaOS-Website) und
  veröffentlicht sie auf **GitHub Pages**. Einmalig nötig: *Settings → Pages → Source: GitHub Actions*
  (für private Repositories braucht GitHub Pages ein kostenpflichtiges GitHub-Konto – sonst das
  Repository öffentlich machen). Die Seite wird aus dem Standard-Branch veröffentlicht.

Eine echte **Samsung-App** (Tizen, `.wgt`) wäre zusätzlich möglich, lässt sich aber nur mit
Entwicklermodus am Fernseher, PC mit Tizen-Werkzeugen und einem persönlichen Samsung-Zertifikat
installieren – der Browser-Weg ist dafür viel einfacher.
