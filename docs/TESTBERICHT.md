# NovaOS 0.1.0 – Testbericht

Stand: 18.11.2026. Dieser Bericht beschreibt **ausschließlich tatsächlich durchgeführte
Tests** und benennt offen, was nicht getestet wurde oder nicht funktioniert hat.

## Testumgebung

| | |
|---|---|
| Build-Host | Cloud-Container, Ubuntu 24.04, 4 CPU-Kerne, 15 GB RAM, root, **ohne KVM** |
| Build-Werkzeuge | Debian live-build 1:20250505+deb13u1, debootstrap 1.0.141 |
| Virtualisierung | QEMU 8.2 mit **TCG (Software-Emulation)**, q35, 4 vCPUs, 6 GB RAM, std-VGA, virtio-net, usb-tablet; Firmware SeaBIOS (BIOS) bzw. OVMF (UEFI/Secure Boot) |
| Testtreiber | `tests/qemu/novaqemu.py` (Screenshots über den QEMU-Monitor, Maus/Tastatur über QMP, Befehle über die serielle Konsole) |
| Netzwerk | Die Testumgebung leitet HTTPS über einen TLS-prüfenden Proxy der Sandbox. Für die Browser-/Store-Tests wurde dessen CA-Zertifikat **nur in der laufenden Test-VM** importiert (nicht im ISO). Ein normaler PC braucht das nicht. |

Ohne KVM ist alles 5–10× langsamer als auf echter Hardware; Startzeiten unten sind daher
**keine** Aussage über die Geschwindigkeit auf einem echten PC.

## Builds

| Build | Inhalt | Ergebnis |
|---|---|---|
| #1 | erster Versuch | ❌ abgebrochen: `firmware-ipw2x00` verlangte Lizenzbestätigung (debconf) → Preseed ergänzt |
| #2 | Debian/KDE ohne NovaOS-Pakete | ✅ ISO 2,1 GB (zstd), 11 min |
| #3 | mit allen novaos-*.deb | ✅ ISO 2,1 GB (zstd), 10 min |
| #4 | Korrekturen aus den VM-Tests | ✅ ISO 2,1 GB (zstd), 10 min |
| #5 | xz-komprimiert, GRUB-UEFI-Korrektur | ✅ 2 091 161 600 Byte, 18 min |
| #6 | **finales ISO**: zusätzlich EFI-Fallback im Installer, Widget-Layout, Willkommensassistent, Rechnername | ✅ `novaos-0.1.0-amd64.iso`, 2 091 169 792 Byte, 12 min, SHA256 `9104bf8b5115b49a1120ad76bbca2e893caea6c2f214094451f323e53626ba82` |

## Ergebnisse

Legende: ✅ getestet und funktioniert · ⚠️ funktioniert mit Einschränkung · ❌ fehlgeschlagen · ⬜ nicht getestet

### Start und Live-System

| Test | Ergebnis | Details |
|---|---|---|
| ISO startet (BIOS/SeaBIOS) | ✅ | NovaOS-Bootmenü (GRUB-Theme) → Plymouth-Splash → Plasma; Desktop nach ~2–4 min (TCG) |
| ISO startet (UEFI/OVMF) | ✅ | Menü + Boot. Fehler gefunden und behoben: Schrift/Videomodule im signierten EFI-GRUB (Build #5 per gepatchtem ISO verifiziert) |
| **Secure Boot** (OVMF mit Microsoft-Schlüsseln, erzwungen) | ✅ | Live-Medium startet über signiertes shim → GRUB → Kernel bis zum NovaOS-Splash |
| „Welcome to NovaOS 0.1.0 (Aurora)!“ (systemd/os-release) | ✅ | |
| Autologin Live-Benutzer `nova` | ✅ | |
| Dienste aktiv: SDDM, NetworkManager, ufw, CUPS | ✅ | Bluetooth inaktiv – die VM hat keinen Bluetooth-Adapter |
| Firewall | ✅ | `ufw status` → `Status: active` |
| Flathub-Remote systemweit | ✅ | `flatpak remotes --system` → `flathub` |
| Rebranding (Nova Files, Terminal, Settings, Browser …) | ✅ | Startmenü zeigt alle Nova-Apps mit eigenen Icons |
| Desktop: schwebende Leiste, Startmenü, Fensterleiste, virtuelle Desktops, Infobereich, Uhr/Datum, Desktop-Symbole, Kalender-Widget, Wallpaper | ✅ | siehe Screenshots |
| Zentrale Suche (Alt+Leertaste) | ✅ | findet installierte Apps |
| Willkommensassistent startet automatisch | ✅ | |

### Gefundene und behobene Fehler (durch die Tests)

| Problem | Ursache | Status |
|---|---|---|
| plasmashell und kwin_wayland stürzten ab (SIGSEGV), System startete z. T. neu | **QEMU-TCG-Emulation von AVX2** (Absturz im llvmpipe-JIT bzw. in Qts AVX2-Bildroutinen). Mit CPU-Modell ohne AVX: keine Abstürze. Kein NovaOS-Fehler; auf echter Hardware/KVM nicht reproduziert (nicht getestet). Vorsorglich wurden eigene Breeze-Schatteneinstellungen entfernt. | ✅ Testtreiber nutzt ohne KVM ein CPU-Modell ohne AVX |
| Bildschirm sperrte sich im Live-System nach 10 min | Plasma-Standard | ✅ live-config schaltet Sperre/Abdunkeln/Bereitschaft im Live-System ab |
| UEFI-Bootmenü ohne Text | signierter EFI-GRUB sucht Schrift in `fonts/`, `insmod efi_uga` existiert nicht | ✅ |
| Nova-Apps teils englisch | Übersetzungskatalog fehlte in Build #3 | ✅ ab Build #4 deutsch |
| Schwarze Scrollleisten im Nova Store | zu allgemeine Stylesheet-Regel | ✅ |
| „Nova Browser“ doppelt in Favoriten | Kickoff-Standardfavorit + eigener Eintrag | ✅ |
| `/etc/os-release` zeigte Debian | Datei war Kopie statt Symlink | ✅ |
| Unit-Tests fanden: Lokalisierungsfehler im AppStream-Parser (Element ohne Kinder ist „falsy“), ungültiges YAML im Calamares-Branding, Rebranding-Formatierung | | ✅ |

### Internet und Browser

| Test | Ergebnis | Details |
|---|---|---|
| Netzwerk (NetworkManager, virtio-net) | ✅ | `enp0s2: connected` |
| HTTPS zu wikipedia.org, github.com, google.com, youtube.com, dl.flathub.org | ✅ | jeweils HTTP 200 (curl in der VM) |
| Nova Browser (Firefox ESR) lädt echte Website | ✅ | de.wikipedia.org/wiki/Linux vollständig dargestellt |
| Tabs, Lesezeichen, Downloads, privater Modus, Passwörter, PDF | ⬜ | nicht einzeln getestet (Standardfunktionen von Firefox ESR) |
| YouTube-Wiedergabe im Browser | ⬜ | nicht getestet (nur HTTP-Erreichbarkeit) |

### Nova Store

| Test | Ergebnis | Details |
|---|---|---|
| Katalog laden (Flathub-AppStream über Flatpak-System-Helper + polkit) | ✅ | 3 415 Apps, Bewertungen von ODRS |
| Suche, Kategorien, Detailseite (Screenshots, Version, Entwickler, Lizenz, Größen, Rezensionen) | ✅ | |
| Berechtigungsprüfung vor Installation | ✅ | Rückfrage bei „Alle Geräte“ (Nestopia) |
| **Echte Installation mit Fortschritt** | ✅ | Nestopia inkl. 5 Laufzeit-Komponenten (freedesktop 25.08) in der VM installiert; Fortschritt je Operation (1/6 … 6/6) |
| Installierte App startet | ✅ | Nestopia-Fenster, Symbol in der Leiste |
| App erscheint in Startmenü/Suche | ✅ | über Alt+Leertaste gefunden |
| Deinstallation, Update-Suche | ⚠️ | Backend-Funktionen auf dem Build-Host getestet (Installation dort ebenfalls erfolgreich, 58 s); in der VM nicht separat durchgeklickt |
| Fehleranzeige | ✅ | z. B. polkit-Verweigerung wurde verständlich angezeigt (beim Start aus der seriellen Konsole, die keine aktive Sitzung ist) |
| Debian-Pakete im Store | ❌ nicht umgesetzt | Debian-Pakete werden über Nova Updates und `apt` verwaltet; Store-Backend steht in der Roadmap |

### Nova-Apps (offscreen auf dem Build-Host, `tests/ui/smoke_apps.py`)

Alle acht eigenen Apps (Store, Updates, Notes, Calendar, Task Manager, Welcome, Help, About)
starten und rendern ohne Fehler; Nova Updates zeigte die echten 30 Updates des Hosts an,
der Task Manager echte Prozess-/CPU-/RAM-/Netzwerkdaten. Der APT-Helfer wurde mit
`install`/`remove` eines echten Pakets getestet (Fortschritt über APT Status-Fd).

### Automatisierte Tests

`make test`: **76 Tests bestanden** (AppStream-Parser, Berechtigungen, Notizen-Speicher,
Kalender inkl. iCalendar-Import/-Export und Erinnerungen, APT-Ausgabe, Übersetzungen
vollständig inkl. Platzhalter, Rebranding, Shell-Syntax aller Skripte, Calamares-YAML
ohne doppelte Schlüssel, `desktop-file-validate`, Paketlisten).

### Installation (Calamares, UEFI, leere 30-GB-Festplatte)

Durchgeführt mit ISO #4 im UEFI-Modus, vollständig über die grafische Oberfläche
(Mausklicks und Tastatur über QMP), ohne Internetzugang für den Installer.

| Schritt | Ergebnis | Details |
|---|---|---|
| Installer aus dem Willkommensassistenten starten | ✅ | Calamares mit NovaOS-Branding, deutsch |
| Anforderungsprüfung | ✅ | Hinweis „nicht mit dem Internet verbunden“ (TLS-Proxy der Testumgebung) – nur Warnung, Installation offline möglich |
| Standort/Zeitzone, Tastatur | ✅ | Europe/Berlin, Deutsch – ohne GeoIP-Abfrage |
| Partitionierung | ✅ | **Keine Option vorausgewählt, „Weiter“ gesperrt bis zur ausdrücklichen Wahl**; „Festplatte löschen“ → 512 MiB EFI + 29,5 GiB ext4; Verschlüsselung angeboten |
| Benutzer | ✅ | Name, Login, Rechnername, Passwort mit Prüfung |
| Zusammenfassung + **zusätzliche Sicherheitsabfrage** | ✅ | „Diese Änderungen können nicht rückgängig gemacht werden“ |
| Installation mit Fortschritt und Diashow | ✅ | ~29 min unter Emulation (parallel lief ein ISO-Build) |
| „Alles erledigt“ | ✅ | |
| **Erster Start von der Festplatte** | ❌ → behoben | OVMF landete in der UEFI-Shell. Ursache: `EFI/NovaOS/` enthielt korrekt shim + signierten GRUB, aber es gab **keinen NVRAM-Booteintrag** und der Fallback-Pfad `EFI/BOOT/` war leer (meine Annahme, das grub-efi-Paket befülle ihn, war falsch). **Fix:** neuer Installer-Schritt `novaos-efi-fallback` (shim → `EFI/BOOT/BOOTX64.EFI` + fbx64/mmx64/grubx64, zusätzlich efibootmgr-Versuch). |
| Installiertes System manuell über shim gestartet | ✅ | NovaOS-GRUB-Theme → Kernel → SDDM |
| Anmeldung mit dem bei der Installation gesetzten Passwort | ✅ | SDDM mit NovaOS-Hintergrund, Plasma (Wayland), Erststart-Assistent |
| Fallback-Dateien wie vom neuen Skript angelegt, frischer UEFI-NVRAM | ✅ | **System startet automatisch**: shim → GRUB → Kernel → Splash → Anmeldung |
| Zweiter Start (persistenter NVRAM) | ✅ | automatisch bis zur Anmeldung |
| **Änderungen bleiben gespeichert** | ✅ | vor dem Neustart angelegte Datei `~/persist.txt` (10:01) nach Neustart vorhanden |
| **Neu starten** (Anmeldebildschirm) | ✅ | GRUB → Splash → System |
| **Herunterfahren** (Abmeldedialog) | ✅ | VM nach ~30 s ausgeschaltet |
| Installer-Schritt `novaos-efi-fallback` im kompletten Calamares-Durchlauf | ⚠️ | in Build #6 enthalten; Skriptlogik wurde manuell (UEFI-Shell, gleiche Dateien) verifiziert, ein zweiter vollständiger Installationslauf mit Build #6: siehe unten |

Weitere Beobachtungen: Systemmonitor-Widgets zeigten erst nach einiger Zeit Daten; nach
einem Auflösungswechsel in der VM wurden die Desktop-Widgets über die Desktop-Symbole
verschoben → Layout in Build #6 kompakter (passt in 1024×768). Der Willkommensassistent
erschien erneut, wenn er per X geschlossen wurde → Schließen zählt ab Build #6 als erledigt.

### Nicht getestet

* echte Hardware (WLAN-Chips, Bluetooth, Drucker, Webcam, Mehrere Monitore, Akku) – nur
  Treiber/Dienste sind enthalten
* Installation im BIOS-Modus und mit LUKS-Verschlüsselung, Dual-Boot neben Windows
* Secure Boot des **installierten** Systems
* Barrierefreiheit, Ton-Ausgabe (VM ohne Audio-Backend)
