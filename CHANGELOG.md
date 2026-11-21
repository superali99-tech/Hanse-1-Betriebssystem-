# Changelog

## 0.1.1 „Aurora“ – in Arbeit

* **Updates alle 14 Tage, Installation beim Neustart:** NovaOS sucht alle 14 Tage nach
  Updates (Debian + systemweite Flatpak-Apps), lädt sie herunter und installiert sie beim
  nächsten Neustart (systemd offline updates, Fortschritt im Boot-Bildschirm). Alle
  Benutzer erhalten „Update verfügbar – bitte neu starten“ (bei Anmeldung, alle 4 Stunden).
  Einstellungen: `/etc/novaos/updates.conf`. Tägliche Sicherheitsupdates optional.
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
