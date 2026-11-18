# NovaOS – Architektur

NovaOS ist **kein Nachbau und keine Simulation**, sondern eine echte Linux-Distribution:
ein startfähiges ISO-Image mit Linux-Kernel, Systemdiensten, Desktop und Installer.
Statt alles neu zu erfinden, kombiniert NovaOS bewährte Open-Source-Komponenten und
ergänzt sie um eigene Anwendungen, ein eigenes Design und eine eigene Konfiguration.

## Schichtenmodell

```
┌──────────────────────────────────────────────────────────────────────────┐
│ Nova-Apps (eigene Entwicklung, Python 3 + Qt 6 / PyQt6)                   │
│  Nova Store · Nova Updates · Nova Notes · Nova Calendar · Nova Task       │
│  Manager · Nova Welcome · Nova Help · Über NovaOS                         │
├──────────────────────────────────────────────────────────────────────────┤
│ Integrierte Anwendungen (unter NovaOS-Namen, per novaos-rebrand)          │
│  Nova Browser = Firefox ESR · Nova Files = Dolphin · Nova Terminal =      │
│  Konsole · Nova Settings = Systemeinstellungen · Nova Calculator = KCalc  │
│  Nova Media = Haruna (mpv) · Nova Photos = Gwenview                       │
├──────────────────────────────────────────────────────────────────────────┤
│ Desktop: KDE Plasma 6.3 (KWin Wayland/X11, SDDM, KRunner, Baloo)          │
│  NovaOS Look-and-Feel · Nova Light/Dark · Aurora-Wallpaper · Icons ·      │
│  schwebende Leiste · Widgets · Plymouth- und GRUB-Theme                   │
├──────────────────────────────────────────────────────────────────────────┤
│ Systemdienste: systemd · NetworkManager · PipeWire/WirePlumber · BlueZ ·  │
│  CUPS · UPower · power-profiles-daemon · udisks2 · polkit · ufw ·         │
│  unattended-upgrades · Flatpak · zram · fwupd                             │
├──────────────────────────────────────────────────────────────────────────┤
│ Basis: Debian 13 „trixie“ (stable), Linux 6.12 LTS, Firmware non-free     │
└──────────────────────────────────────────────────────────────────────────┘
```

## Komponenten und Verantwortlichkeiten

| Bereich | Umsetzung | Ort im Repository |
|---|---|---|
| ISO-Build | Debian **live-build** (`lb config`/`lb build`) | `live-build/`, `tools/build-iso.sh` |
| Paketauswahl | Paketlisten, apt-Pinning (kein Discover, keine Telemetrie-SDKs) | `live-build/config/package-lists/`, `live-build/config/apt/` |
| Boot (Live-Medium) | GRUB für BIOS **und** UEFI, Secure Boot über Debians signierte shim/GRUB/Kernel | `live-build/config/bootloaders/grub-pc/` |
| Live-System | live-boot/live-config, Autologin Benutzer `nova`, Installer-Symbol | `packages/novaos-installer/root/usr/lib/live/config/2000-novaos` |
| Installer | **Calamares 3.3** mit NovaOS-Konfiguration und -Branding | `packages/novaos-installer/` |
| Systemidentität & Sicherheit | os-release, ufw, unattended-upgrades, journald, zram, Flathub-Remote, GRUB-Theme | `packages/novaos-base/` |
| Desktop-Design | Plasma Look-and-Feel, Farbschemata, Panel-Layout, SDDM, Plymouth, Firefox-Richtlinien, Rebranding | `packages/novaos-desktop/` |
| Eigene Apps | Python-Paket `novaos` | `src/novaos/`, `packages/novaos-apps/` |
| Artwork | generierte SVG-Quellen + PNG-Rendering | `artwork/generate.py`, `artwork/generate_colors.py` |
| Tests | Unit-Tests, UI-Smoke-Tests, QEMU-Boot-Tests | `tests/` |

## NovaOS-Pakete

NovaOS-Anpassungen werden als **echte Debian-Pakete** ausgeliefert (gebaut mit
`tools/build-debs.sh`). Sie liegen im ISO, werden von live-build installiert und
können später über ein eigenes APT-Repository aktualisiert werden.

| Paket | Inhalt |
|---|---|
| `novaos-base` | `/usr/lib/os-release` (per `dpkg-divert`), Flathub-Remote, ufw aktiv, automatische Sicherheitsupdates, persistentes Journal, zram, GRUB-Theme, apt-Pinning |
| `novaos-desktop` | Look-and-Feel `org.novaos.desktop` (+ `.dark`), Farbschemata, Wallpaper, App-Icons, KWin/Plasma/KRunner/Baloo-Defaults, SDDM, Plymouth-Theme, Konsole-Profil, Firefox-Richtlinien, `novaos-rebrand` |
| `novaos-apps` | Nova Store, Updates, Notes, Calendar, Task Manager, Welcome, Help, About; polkit-Aktion, APT-Helfer, systemd-User-Units |
| `novaos-installer` | Calamares-Konfiguration/Branding, Hilfsskripte (Bootloader, Paketquellen), live-config-Komponente |

### Rebranding ohne Forks

`novaos-rebrand` lenkt die `.desktop`-Dateien von Dolphin, Konsole, Firefox ESR usw.
per `dpkg-divert` nach `/usr/share/novaos/rebrand/orig/` um und erzeugt daraus Kopien
mit NovaOS-Namen und -Icons. Ein dpkg-Trigger erzeugt die Kopien bei jedem Update der
Originalpakete neu. Programme selbst werden **nicht** verändert – Sicherheitsupdates
von Debian kommen weiterhin unverändert an.

## Nova Store – Aufbau

```
 Flathub ──(libflatpak/OSTree)──► /var/lib/flatpak/appstream/flathub/x86_64/active/
                                     appstream.xml(.gz) + Icons
                                            │
                       appstream.py (Parser, Suche, Kategorien)
                                            │
 odrs.gnome.org ──► ratings.py ──► controller.py ◄── flatpak_backend.py (libflatpak GI)
                                            │            Installation/Update/Deinstallation
                                            │            Fortschritt, Berechtigungen (metadata)
                                   main_window.py / details.py / tiles.py (PyQt6)
```

* **Katalog:** Flathubs AppStream-Daten (≈ 3 400 Desktop-Apps), mit Übersetzungen,
  Screenshots, Versionen, Entwickler, Lizenz, Verifizierung.
* **Installation:** `Flatpak.Transaction` in einem Worker-Thread mit eigenem
  GLib-Main-Context; Fortschritt pro Operation (Runtime, Erweiterungen, App) in Prozent.
  Systemweite Installation über Flatpaks System-Helper + polkit.
* **Berechtigungen:** Vor der Installation wird die `metadata` der App vom Remote geladen
  (`fetch_remote_ref_sync`) und verständlich angezeigt; bei kritischen Rechten
  (z. B. `filesystems=host`, `devices=all`, X11 ohne Wayland) folgt eine Rückfrage.
* **Bewertungen:** Open Desktop Ratings Service (öffentliche, reale Datenquelle).
* **Updates:** `list_installed_refs_for_update`, automatische Prüfung alle 6 h.

## Nova Updates – Rechtemodell

Die GUI läuft als normaler Benutzer. Systemänderungen erledigt
`/usr/libexec/novaos/nova-apt-helper`, gestartet über `pkexec`
(polkit-Aktion `org.novaos.updates.apt-helper`, `auth_admin_keep`). Der Helfer erlaubt
nur feste Befehle (`refresh`, `upgrade`, `install`, `remove`, `auto-updates on|off`),
prüft Paketnamen per Regex und liefert Fortschritt über APTs `Status-Fd`.

## Installationsablauf (Calamares)

1. Willkommen (Anforderungen: 20 GB, 2 GB RAM), Sprache/Zeitzone, Tastatur
2. Partitionierung: *Festplatte löschen*, *Daneben installieren*, *Partition ersetzen*
   oder *manuell*; optional LUKS-Verschlüsselung; ext4/btrfs/xfs
3. Benutzer (Passwort als SHA-512-Hash in `/etc/shadow`, erster Benutzer = sudo)
4. Zusammenfassung **und** zusätzliche Bestätigung („Jetzt installieren?“) –
   vorher wird nichts auf Datenträger geschrieben
5. Ausführung: Partitionieren → SquashFS entpacken → Konfiguration → Live-Pakete
   entfernen → Bootloader (UEFI: signiertes shim/GRUB, BIOS: grub-pc, offline aus
   dem `/pool` des ISO) → Plymouth → initramfs → endgültige Paketquellen

## Sicherheit und Datenschutz (Überblick)

* Firewall ufw aktiv (eingehend blockiert)
* tägliche automatische Sicherheitsupdates (Debian Security)
* Flatpak-Sandbox, sichtbare Berechtigungen, Flatseal/KDE-KCM zur Anpassung
* Firefox ESR mit `policies.json`: Telemetrie/Studien aus, Tracking-Schutz, HTTPS-only
* KDE-Telemetrie (KUserFeedback) aus, Sentry-SDK und popularity-contest ausgeschlossen
* optionale Vollverschlüsselung (LUKS), persistentes Journal, Timeshift-Snapshots

Details: [DATENSCHUTZ.md](DATENSCHUTZ.md), [SECURE-BOOT.md](SECURE-BOOT.md).
