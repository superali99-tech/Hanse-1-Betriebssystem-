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
