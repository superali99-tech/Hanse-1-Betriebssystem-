# Changelog

## 0.1.1 „Aurora“ – in Arbeit

* **Updates alle 14 Tage, Installation beim Neustart:** NovaOS sucht alle 14 Tage nach
  Updates (Debian + systemweite Flatpak-Apps), lädt sie herunter und installiert sie beim
  nächsten Neustart (systemd offline updates, Fortschritt im Boot-Bildschirm). Alle
  Benutzer erhalten „Update verfügbar – bitte neu starten“ (bei Anmeldung, alle 4 Stunden).
  Einstellungen: `/etc/novaos/updates.conf`. Tägliche Sicherheitsupdates optional.
* **PC-Update-Kanal:** installierte PCs bekommen die NovaOS-Pakete (Nova-Apps, Desktop, Einstellungen) als
  Update – signierte apt-Quelle im GitHub-Release `pc-update` (Version 0.1.1.N, N = Anzahl der Commits, Workflow
  `build-pc-update.yml`, Schlüssel wie bei NovaOS Mobile einmal erzeugt und im Entwurf „NovaOS PC archive key“
  verwahrt). Paket `novaos-archive-keyring` (Quelle + Schlüssel, gilt nur für diese Quelle); PCs mit 0.1.0
  richten den Kanal einmal mit `novaos-update-einrichten.deb` ein. **„Software-Update“ in den
  KDE-Systemeinstellungen** (Kategorie System) öffnet Nova Updates; Knopf heißt jetzt „Nach Updates suchen“.
  Beim Update von 0.1.0 bleiben die täglichen Sicherheitsupdates an (0.1.0 hatte sie an).
* Boot-Bildschirm: Meldungen werden jetzt angezeigt (falscher Plymouth-Funktionsname korrigiert)
* Status: Unit-Tests bestanden; Test im installierten System **noch nicht abgeschlossen**
* **NovaOS TV:** App für Google TV / Android TV (Startseite mit Favoriten, allen Apps, Suche,
  TV-Einstellungen, Bedienung per Fernbedienung, Deutsch/Englisch);
  in Android-x86 9 getestet, gefundene Bedien- und Layoutfehler behoben
* **NovaOS TV – Fernsehen:** 30 Sender als Livestream (Das Erste, ZDF, 3sat, ARTE, phoenix,
  tagesschau24, KiKA, alle Dritten, WELT, DELUXE MUSIC) im eigenen Vollbild-Player
  (▲▼/P+/P− zappen, Zifferntasten, Senderliste mit OK); dazu „Sender vom Fernseher“ mit allen
  Sendern des eingebauten Empfängers (Antenne/Kabel/Satellit, auch Privatsender)
* **NovaOS TV – Nova Filme:** kostenlose Spielfilme, Serien, Dokus und Kinderprogramm von
  offiziellen YouTube-Kanälen (Netzkino, Moviedome, ZDFneo, funk, Terra X, ARTE, TOGGO …). NovaOS TV nutzt dafür jetzt das Internet (Datenschutztext angepasst)
* **NovaOS Mobile:** NovaOS-Startbildschirm für Android-Handys und -Tablets (Uhr, App-Suche,
  Nova-Karten, Schnelleinstellungen mit Taschenlampe, Favoriten, Dock, App-Übersicht, Gesten),
  Fernsehen mit Touch-Player und Nova Filme im eingebetteten YouTube-Player; teilt Code mit NovaOS TV
* **NovaOS TV Web** für Fernseher ohne Android (Samsung/Tizen, LG/webOS, Philips mit Saphi/Titan OS):
  Fernsehen und Nova Filme als Webseite im TV-Browser, Bedienung mit Pfeiltasten/OK oder Zeiger;
  Daten mit derselben Logik wie die App, alle 3 Stunden neu gebaut und auf GitHub Pages veröffentlicht
* **NovaOS TV – Nova Filme spielt jetzt direkt in der App** (offizieller eingebetteter
  YouTube-Player, NovaOS-Infoleiste unter dem Bild, OK = Pause, ◀▶ = 10 s); nur Videos, die das
  Einbetten erlauben (Biene Maja, ZDFtivi und KiKA entfernt)
* NovaOS TV: Fernsehen und Nova Filme in Android-x86 9 mit Internet getestet (Das Erste, NDR
  Hamburg, tagesschau24 live; Nova Filme mit 94 echten Videos); gefundene Fehler behoben (Fokus,
  Bild ohne Ton weiter, schwarzes Umschalten, Live-TV-Erkennung)
* **NovaOS Mobile – Software-Update:** „Neues Update verfügbar“ in den NovaOS-Einstellungen und
  auf dem Startbildschirm; Download mit SHA-256- und Signaturprüfung,
  Installation über Android, danach Neustart von NovaOS mit **Start-Animation** und „Was ist neu“;
  „NovaOS neu starten“ in den Einstellungen. Versionsnummer = Version + Zahl der Commits
* **NovaOS Mobile – Nova Store** mit echten Apps (freie Apps direkt aus F-Droid installieren und
  aktualisieren, bekannte Apps über den Play Store) und eigene Apps **Nova Kamera**, **Nova Galerie**,
  **Nova Dateien** und **Nova Notizen** mit eigenen Symbolen
* NovaOS Mobile – Update-Kanal **ohne GitHub Pages**: das Update liegt als öffentliches Paket
  `novaos-mobile` in GitHubs Container-Registry (ghcr.io), das Repository bleibt privat; fester
  Signierschlüssel ohne Secrets (vom Workflow einmal erzeugt, im Entwurfs-Release aufbewahrt)
* **NovaOS Mobile – Nova Chat**: Chat, Sprach- und Videoanrufe über Matrix (matrix.org, kompatibel
  mit Element), Benachrichtigungen, eingehende Anrufe auch auf dem Sperrbildschirm, Kontakte im
  Matrix-Konto auf dem Server
* **NovaOS Mobile – Nova, der Assistent**: „Hey Nova“ und Mikrofon auf dem Startbildschirm, ohne
  Cloud-KI (Uhrzeit, Wecker, Timer, Taschenlampe, Apps, Anrufe, Notizen, Wetter, TV, Rechnen …)
* NovaOS Mobile: Updates erscheinen von selbst („Update verfügbar“, Benachrichtigung mit
  „Aktualisieren“) und laufen wie ein System-Update in Stufen (etwa 2–3 Minuten); neue Karten für
  Nova Chat und Nova auf dem Startbildschirm
* **NovaOS Mobile – Nova Chat**: Sprachnachrichten, Emoji-Tastatur, Fotos senden/ansehen,
  Schnellantworten, bearbeitete und gelöschte Nachrichten werden übernommen; **„Nova schreibt für
  dich“** (diktieren, vorlesen, „Soll ich das senden?“); Nova-Assistent liest Chat-Nachrichten vor
  und schreibt über Nova Chat
* **NovaOS Mobile – Nova Mail**: echte E-Mails mit dem eigenen Konto (GMX, WEB.DE, Gmail, T-Online,
  Yahoo, iCloud, freenet und andere über IMAP/SMTP, nur verschlüsselt), Ordner, Anhänge, Antworten,
  Weiterleiten, mehrere Konten, Benachrichtigungen; Nova liest E-Mails vor und schreibt sie
* **NovaOS Mobile – Einrichtung beim ersten Start**: Anmelden (Google-Konto des Handys, Microsoft,
  E-Mail mit Prüfung beim Anbieter), Bildschirmsperre (Passwort/PIN/Muster von Android),
  Berechtigungen, Alter; Begrüßung mit Namen
* **NovaOS Mobile – Nova Family**: Eltern sperren das Kinderhandy (sofort, bis zu einer Uhrzeit, Ruhezeit),
  Bildschirmzeit, Apps sperren, Bitten erlauben; gesperrt nur Sperrbildschirm mit Anrufen (Eltern, 112),
  Taschenlampe und Eltern-PIN; Verbindung über ntfy.sh, Ende-zu-Ende-verschlüsselt
* **NovaOS Mobile 0.1.1.59**: Software-Update mit ⋮-Menü („Was ist neu“, Update-Verlauf aller Versionen,
  „Nach Updates suchen“), neue Fortschrittsanzeige (Ring mit Prozent, echte MB und Download-Geschwindigkeit,
  Restzeit, Schritte); Suchfelder in Nova Store, Nova Chat, Kontakten und Nova Mail (auch ohne Umlaute:
  „mueller“ findet „Müller“); Adressbuch in Nova Mail (Name, Adresse, Telefon, Notiz; „Als Kontakt speichern“
  in jeder Mail), Kontakt speichern direkt aus einem Chat und Umbenennen in Nova Chat; „Neue E-Mail-Adresse
  erstellen“ (GMX, WEB.DE, freenet, Gmail) und richtiger Outlook-Hilfelink; „Hey Nova“ standardmäßig aus –
  dadurch kein Android-Ton mehr nach jedem Tippen auf dem Startbildschirm
* **NovaOS Mobile 0.1.1.60 – Nova Store wie der Play Store**: Detailseite je App (Entwickler, Größe, Version,
  Android-Version, Lizenz, echte Bildschirmfotos, „Über diese App“, App-Info, Hinweise von F-Droid, Website und
  Quellcode; Daten aus F-Droids Index über `mobile/tools/store_meta.py`, keine erfundenen Bewertungen);
  Installieren mit Fortschrittsring um das Symbol, „42 % von 107,3 MB“ und „Abbrechen“; danach „Deinstallieren“/
  „Öffnen“; heruntergeladene APKs werden gelöscht. Neuer Bereich **Nova-Apps**: „Aktualisieren“ bei jeder Nova-App,
  die das wartende NovaOS-Update ändert (`@apps:` in `whatsnew.txt` → `apps=` in `update.properties`).
  Behoben: hängendes „Wird installiert …“ nach Abbrechen im Android-Installationsfenster
* **NovaOS Mobile 0.1.1.61 – Nova Musik**: eigene Musik (MediaStore, Alle abspielen/Zufällig, Suche), Live-Radio
  aus radio-browser.info (nur https-Streams, Logo, Genre, gerade laufender Titel aus den ICY-Daten), echte Songs
  aus Deezers öffentlicher Suche und Charts (30-Sekunden-Hörprobe, ganzes Lied über Deezer/Spotify/YouTube Music);
  Wiedergabe mit ExoPlayer im Hintergrund (Vordergrunddienst „Medienwiedergabe“), MediaSession für Sperrbildschirm,
  Bluetooth- und Kopfhörer-Tasten, Benachrichtigung ⏮ ⏯ ⏭, Pause beim Trennen von Kopfhörern, Anzeige des
  Ausgabegeräts mit Sprung in die Bluetooth-Einstellungen; Nova-Befehle „Spiel Radio …“, „Spiel <Lied>“, „Spiel meine
  Musik“, „Nächstes/Vorheriges Lied“, „Musik pausieren/weiter“; Karte auf dem Startbildschirm
* **NovaOS Mobile 0.1.1.62 – Nova Phone Shop**: Konzept-Handys Nova 2 Pro Max (ab 1.099 €), Nova 2 Pro (ab 999 €),
  Nova 2 (ab 799 €), Nova 1 Pro (649 €), Nova 1 (ab 449 €) und Nova Lite (ab 249 €), höchstens 1.199 €; Produktbilder
  werden gezeichnet (`PhoneRender`: Vorderseite mit NovaOS-Bildschirm, Rückseite mit Kameramodul je Modell, Metallrahmen,
  Glanz, Schatten), Trailer-Banner, Filter, Suche, Produktseite mit Farben, Speicher und Preis, Highlights,
  technischen Daten und Wunschliste; klar als Konzept gekennzeichnet, keine Zahlungsdaten
* **NovaOS Mobile 0.1.1.63 – Konto und Sicherheit, klügerer Nova**: Seite „Konto und Sicherheit“ (Name, E-Mail,
  Anmeldeweg, Alter, Gerät, Bildschirmsperre, Fingerabdruck); Schutz der Nova-Apps mit persönlichen Daten (Chat, Mail,
  Family, Notizen, Dateien, Galerie, Einstellungen) über Androids eigene Abfrage (BiometricPrompt bzw. Sperrbildschirm-
  Bestätigung: PIN, Muster, Passwort, Fingerabdruck/Gesicht) – frei bis der Bildschirm ausgeht oder 5 Minuten ungenutzt;
  Abmelden, Namen ändern und Schutz ausschalten nur nach Bestätigung (ohne Sperre: 6-stelliger Code per E-Mail über das
  eigene Nova-Mail-Konto, 10 Minuten gültig, 5 Versuche); „PIN vergessen?“ mit ehrlichen Wegen (neue PIN, Google „Mein
  Gerät finden“, Samsung „SmartThings Find“, Zurücksetzen). Nova: Wikipedia-Antworten („Wer ist …“, „Was ist …“),
  „Wie viele Tage bis Weihnachten/Ostern/24.12.“, Einheiten umrechnen (km/Meilen, °C/°F, kg/Pfund, Liter/Gallonen,
  km/h/mph), Würfeln und Münzwurf
* **NovaOS Mobile 0.1.1.64 – Update-Seite wie bei Samsung**: gezeichnetes Titelbild (`UpdateArt`), „Update verfügbar“ /
  „Download und Installation …“, Neuerungen, zweiteiliger Balken (Download | Bestätigung), ein großer Knopf
  „Aktualisieren“ → „Pause“ → „Fortsetzen“; Querformat mit Bild links. Das Update läuft jetzt in `UpdateEngine` unabhängig
  von der Seite (Drehen, Verlassen, andere Apps brechen es nicht mehr ab), Download mit Pause und Fortsetzen per
  HTTP-Range (`Download.resumable`), Fortschritt in der Benachrichtigung. Behoben: Drehen ins Querformat brach das Update ab
* **NovaOS Mobile 0.1.1.66 – Neuerungen und Infoseiten der Nova-Apps**: Nova-Apps im Nova Store antippbar mit Seite
  wie im Play Store (NovaOS, Version, kostenlos, keine Werbung, Öffnen/Aktualisieren, „Neuerungen •“, „Über diese App“);
  Detailseite „Neue Funktionen •“; „Neuerungen“ mit blauem Punkt 3 Stunden nach einem Update und wenn das nächste wartet
  (Nova-Apps: Zeilen `{app} …` in `whatsnew.txt` → `appnote.<app>.*` in `update.properties`; F-Droid-Apps: Änderungstext
  der Version aus F-Droids Index), abschaltbar über ⋮ → „Neuerungen anzeigen“. Software-Update: „Zuletzt aktualisiert“
  und „Zuletzt nach Updates gesucht“ mit Datum und Uhrzeit, „Was ist neu“ und Update-Verlauf mit Erscheinungs- und
  Installationsdatum (Installationen werden auf dem Handy mitgeschrieben). Behoben: ein „|“ im Text der Neuerungen von
  0.1.1.64 schnitt die Zeile ab (Test prüft das jetzt für alle Zeilen)
* **NovaOS Mobile 0.1.1.70 – Nova Karte**: neue Karten-App wie Google Maps – Karte von OpenStreetMap (Ziehen, Zoomen,
  Doppeltippen, langes Drücken setzt einen Punkt), Suche nach Orten und Adressen (Photon, Treffer in der Nähe zuerst,
  doppelte Einträge zusammengefasst, letzte Ziele), Route mit Auto, Fahrrad oder zu Fuß (OSRM von FOSSGIS) mit Dauer,
  Strecke, Ankunftszeit und „über …“, Liste der Schritte. Navigation mit grünem Banner (Pfeil, Entfernung, „Danach: …“),
  Ansagen über die Sprachausgabe des Handys („In 150 Metern rechts abbiegen auf Neue Burg, danach rechts“, „Jetzt …“,
  Kreisverkehr mit Ausfahrt, „Du hast dein Ziel erreicht – es ist links“), neue Route beim Abweichen, Ton aus/an,
  Bildschirm bleibt an; „Vorschau“ fährt die Route simuliert ab. Im Querformat liegen die Karten links und Route/Ziel
  werden im freien Teil der Karte gezeigt. Nova versteht „Navigiere nach …“, „Bring mich zum …“, „Wie komme ich zum …?“
  und „Öffne die Karte“; `geo:`-Links öffnen Nova Karte. Neuer Vertrauensanker ISRG Root X2 (Let's Encrypt) in der
  Netzwerk-Sicherheitskonfiguration, falls ein Server die kurze Kette ohne X1-Gegenzeichnung schickt (Android < 14 kennt
  X2 nicht); Nutzer-Zertifikate und unverschlüsseltes http bleiben verboten
* **NovaOS Mobile 0.1.1.72 – Software-Update wie bei One UI**: ohne neues Update eine dunkle Seite mit leuchtender
  Kugel, groß „NovaOS 1.0“ und „Deine Software ist auf dem neuesten Stand.“ (← und ⋮ oben); „NovaOS 1.0“ antippen oder
  ⋮ → „Letztes Update“ öffnet „Letztes erfolgreiches Update“ mit gezeichnetem „1“, Datum „… um 16:37“, Beschreibung,
  „Alle Änderungen“ und Aktualisierungsinformationen (NovaOS-Version 1.0, Android-Version, Build 0.1.1.N, Größe,
  Sicherheitspatch-Ebene). „NovaOS 1.0“ ist der Anzeigename; die Build-Nummer zählt für Updates weiter. Behoben: im
  Update-Verlauf standen Markierungen wie „{map}“ vor den Punkten
* **NovaOS Mobile 0.1.1.74 – Nova Karte nimmt den GPS-Standort**: Rückmeldung aus Mölln, die Karte zeigte Grambek. Ursache:
  eine neuere, aber ungenaue Position aus dem Mobilfunknetz (± 2–3 km) ersetzte die GPS-Position, beim Start wurde die
  neueste statt der genauesten bekannten Position genommen, und „ungefährer Standort“ (Android 12+) wurde nicht erkannt.
  Jetzt entscheidet `Fixes` (getestet): eine schlechtere Position anderer Herkunft ersetzt eine genaue erst, wenn diese
  2 Minuten alt ist; GPS, „fused“ (Android 12+) und Mobilfunk werden einzeln angemeldet; bei nur ungefährem Standort
  fragt Nova Karte nach dem genauen (oder öffnet die App-Einstellungen), bei ausgeschaltetem Standort nach dem
  Einschalten; ein Hinweis unter dem Suchfeld zeigt „Standort noch ungenau (± 2,4 km)“; Navigation startet erst ab
  einer Genauigkeit von 150 m
* **NovaOS Mobile 0.1.1.75 – NovaOS 1.5**: Startbildschirm wie bei Samsung One UI – Wetter-Widget (Ort aus dem
  besten bekannten Standort, Name über Photon, alle 30 Minuten neu) zwischen den ersten Apps, weitere Apps unten und auf
  weiteren Seiten (eigener `Pager` mit Punkten), Suchleiste, Dock mit Telefon, Browser, Kamera, Nachrichten, rote Zahlen
  für ungelesene Chats/Mails und Nova-Apps mit Update; Begrüßung, große Uhr, Nova-Karten und Schnelleinstellungen
  entfallen (Schnelleinstellungen gibt es im Benachrichtigungsfeld). Alle Apps: Seiten zum Seitwärts-Wischen, „Suchen“
  unten, ⋮ → Sortieren (eigene Reihenfolge mit neuen Apps hinten, oder alphabetisch), Apps nach vorne/hinten
  verschieben (`DrawerOrder`, getestet). Nova TV und Nova Filme sind eigene Apps. Nova Store: „Aktualisieren“ bei
  einer Nova-App startet das Update direkt dort mit Ring ums Symbol, „Wird heruntergeladen … 45 %“ und Pause,
  „Alle aktualisieren“ (die Nova-Apps stecken in NovaOS: ein Update bringt alle geänderten mit). Software-Update
  zeigt „NovaOS 1.5 (0.1.1.N)“ (`release=` in `update.properties`), „Letztes erfolgreiches Update“ zeigt „1.5“
* **NovaOS Mobile 0.1.1.77 – große Updates heißen „NovaOS 1.5“**: steht in `whatsnew.txt` die Zeile `@release: 1.5`,
  zeigen „Was ist neu“, Update-Verlauf, Software-Update, Nova Store, Einstellungen, Hinweise und die Startanimation
  „NovaOS 1.5“ statt der langen Nummer; kleine und mittlere Updates (ohne die Zeile) zeigen weiter 0.1.1.N. Im
  Update-Verlauf: „== NovaOS 1.5 (0.1.1.75) · Datum“. `release=` steht nur noch bei großen Updates in `update.properties`
* **NovaOS Mobile 0.1.1.78 – Glas-Design**: Vorlage: Bild des Glas-Designs (Chat auf buntem Hintergrund, Musik-Steuerung,
  Startbildschirm mit Glas-Widgets); eigene NovaOS-Umsetzung, keine fremden Symbole. Schrift **Inter** (OFL, `res/font`,
  Android 8+ über das Theme, alle Texte von `Ui.text` über `Fonts`); Glas-Hintergründe für Karten, Knöpfe, Dock
  (oben heller, Lichtrand, größere Rundung), dünnerer Schleier auf allen Seiten. Startbildschirm: Glas-Widgets Wetter
  (→ **Nova Wetter**, neue App: Open-Meteo mit 24 Stunden und 7 Tagen, Temperaturbalken, Himmel nach Wetter und
  Tageszeit, letzte Antwort gespeichert) und Kalender (nächster Termin, Berechtigung „Kalender lesen“ erst beim Antippen),
  Musik-Widget während Musik läuft, Apps darunter (`DrawerOrder.fill`, getestet). Nova Musik: Glas-Leiste, großer
  Player (`NowPlayingActivity`) mit Hintergrund in den Cover-Farben. Nova Chat: bunter Verlauf, blaue eigene und
  Glas-Blasen. „Letztes erfolgreiches Update“ zeigt die Neuerungen. Kleines/mittleres Update: lange Nummer
* **NovaOS Mobile 0.1.1.80 – Nova AI**: Nova als Chat (Blasen, `NovaOrb` „Nova denkt nach …“, Senden-Knopf), Modell
  auswählen (`NovaModelsActivity`): Nova 5 (Befehle und Wikipedia, 30 Fragen am Tag), Nova Pro 5 (6 Sätze mit Quelle,
  unbegrenzt, nur mit Abo), Nova Mini (nur Befehle, ohne Internet, unbegrenzt), Nova Vision 5 („kommt bald“, nicht
  wählbar) – ehrlich beschrieben, Nova bleibt ohne Cloud-KI (`NovaAi`, getestet). **Nova Pro 5 Abo**
  (`NovaProActivity`): keine Zahlung – Preise durchgestrichen, „0,00 €“, in der Testphase kostenlos, bestätigt mit
  der Sperre des Handys (Gesicht/Fingerabdruck/PIN über `ConfirmActivity`), jederzeit beendbar; eine Prüfung per
  Personalausweis gibt es bewusst nicht. Nova Store: Nova-Apps aktualisieren im Schnellmodus (`UpdateEngine.start(u,
  true)`: kurze Stufen) und ohne Start-Animation danach (nur „Nova-Apps aktualisiert ✓“)
* **NovaOS Mobile 0.1.1.82 – Zoomen und Bearbeiten (nur Nova-Store-Update für Kamera und Galerie)**: Fotos und
  Videos zoomen (`ZoomLayout`: zwei Finger, Doppeltippen 2,5×, im Zoom verschieben bis zum Bildrand; Videos jetzt in
  `VideoPane` auf einer TextureView mit eigenen Knöpfen). **Fotos bearbeiten** (`PhotoEditActivity`): zuschneiden
  (frei, 1:1, 4:3, 16:9, 9:16), drehen, spiegeln, Helligkeit, Kontrast, Sättigung, Wärme, 7 Filter – gespeichert als
  Kopie „…_bearbeitet.jpg“ in DCIM/NovaOS (`MediaSave`). **Videos kürzen** (`VideoTrimActivity`): Anfang/Ende ziehen,
  auf Wunsch ohne Ton, ohne neu zu kodieren (MediaExtractor/MediaMuxer), Kopie „…_gekürzt.mp4“. **Kamera-Zoom**: zwei
  Finger oder 0,5×/1×/2×/5× (je nach Handy, `CONTROL_ZOOM_RATIO` oder Bildausschnitt). Rechenteil in `Zoom` (getestet).
  Neu: **`@store-only`** in den Neuerungen → `storeOnly=true` – das Update erscheint nur im Nova Store, nicht als
  Software-Update und ohne Neustart-Animation (`Updates.systemPending`, Benachrichtigung „App-Updates im Nova Store“)
* Nova-Apps übernehmen einen Wechsel hell/dunkel jetzt auch, wenn sie schon geöffnet sind
* Trailer-Werkzeuge (`tools/trailer/`) und Full-HD-Screenshots

## 0.1.0 „Aurora“ – erste Entwicklerversion

* Startfähiges 64-Bit-ISO (BIOS + UEFI, Secure-Boot-Kette von Debian) auf Basis von
  Debian 13 „trixie“ mit Linux 6.12 und KDE Plasma 6.3
* Live-System mit Autologin, Installer Calamares 3.3 mit NovaOS-Branding
  (Verschlüsselung, ext4/btrfs/xfs, Dual-Boot, Offline-Bootloader-Installation)
* NovaOS-Design: Look-and-Feel hell/dunkel, Farbschemata, Aurora-Wallpaper, 16 App-Icons,
  schwebende Leiste, Desktop-Widgets, Boot-Splash, Login-Splash, GRUB-Themes
* Eigene Apps: Nova Store (Flathub), Nova Updates, Nova Notes, Nova Calendar,
  Nova Task Manager, Nova Welcome, Nova Help, Über NovaOS
* Integrierte Apps unter NovaOS-Namen: Nova Browser (Firefox ESR), Nova Files, Nova Terminal,
  Nova Settings, Nova Calculator, Nova Media, Nova Photos
* Installiertes System startet unter UEFI selbstständig: NVRAM-Eintrag „NovaOS“ aus dem
  Live-System plus Fallback-Pfad `EFI/BOOT/BOOTX64.EFI` (shim legt den Eintrag bei Verlust neu an)
* ISO-Build und Veröffentlichung als GitHub-Release über GitHub Actions
* Sicherheit: ufw aktiv, automatische Sicherheitsupdates, Flatpak-Sandbox, keine Telemetrie
