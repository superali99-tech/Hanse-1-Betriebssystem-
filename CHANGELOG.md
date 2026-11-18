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
  TV-Einstellungen, Bedienung per Fernbedienung, Deutsch/Englisch, keine Internet-Berechtigung);
  in Android-x86 9 getestet, gefundene Bedien- und Layoutfehler behoben
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
