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
| #6 | EFI-Fallback im Installer, Widget-Layout, Willkommensassistent, Rechnername | ✅ 2 091 169 792 Byte, 12 min (Grundlage des 2. Installationstests) |
| #7 | **finales ISO**: zusätzlich NVRAM-Eintrag aus dem Live-System, Booteintrag „NovaOS“ | ✅ `novaos-0.1.0-amd64.iso`, 2 091 169 792 Byte, 12 min, SHA256 `54059f545bc816efbb5acf4c01f8fd242530225053cf67326f8a6ddd19046249`; Boot-Rauchtest (BIOS-Menü + Live-Desktop) bestanden; Grundlage des 3. Installationstests |
| CI 1 | GitHub Actions, Commit 60afa64 | ❌ `config/packages.chroot` fehlte im frischen Checkout (git speichert keine leeren Ordner) → `build-iso.sh` legt die Ordner an |
| CI 2 | GitHub Actions, Commit dd9b848 (gleicher ISO-Inhalt wie #7, nur Build-Skript-Korrektur) | ✅ Unit-Tests + ISO-Build, 2 092 853 248 Byte, SHA256 `a468107495b5e1485647d913ce2d7a20345139ee0490de6e21cd6db99b58fb1a`, als Release **`build-2`** veröffentlicht. Dieses ISO selbst wurde **nicht** in QEMU gestartet (das Repository ist privat, der Download war aus der Testumgebung ohne Anmeldung nicht möglich); es stammt aus denselben Quellen wie #7. |

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
| Installer-Schritt `novaos-efi-fallback` im kompletten Calamares-Durchlauf | ✅ | siehe zweiter Installationstest unten |

#### Zweiter vollständiger Installationstest (finales ISO #6, UEFI)

| Schritt | Ergebnis | Details |
|---|---|---|
| Komplette Installation über die Oberfläche | ✅ | ~26 min; Rechnername-Vorschlag jetzt „anna-novaos“ |
| Installer legt `EFI/BOOT/BOOTX64.EFI` (shim) + fbx64/grubx64/mmx64/grub.cfg an | ✅ | im UEFI-Shell-Listing nachgewiesen |
| Erster Start ohne Eingriff (gleiche NVRAM wie bei der Installation) | ❌ | wieder UEFI-Shell: In dieser VM stand die Shell in der Bootreihenfolge **vor** der Festplatte, und der NVRAM-Eintrag aus dem Installer fehlte erneut |
| Fallback einmal von Hand gestartet (`fs0:\EFI\BOOT\BOOTX64.EFI`) | ✅ | shims `fbx64` legte `Boot0007` → `\EFI\NovaOS\shimx64.efi` an und setzte ihn an **Platz 1** der Bootreihenfolge |
| `efibootmgr -c` aus dem laufenden NovaOS | ✅ | RC=0, Eintrag angelegt → NVRAM-Schreiben funktioniert; Schluss: Der Aufruf **im Ziel-chroot** des Installers war das Problem |
| Herunterfahren → Start **ohne jeden Eingriff** | ✅ | GRUB → Kernel → Anmeldebildschirm (auch mit einem absichtlich defekten Eintrag an Platz 1 fiel die Firmware korrekt auf NovaOS zurück) |
| Korrektur in Build #7 | ✅ | `efibootmgr` wird jetzt aus dem Live-System aufgerufen (dort sind die EFI-Variablen eingehängt), Booteintrag heißt „NovaOS“ – siehe dritter Installationstest |

#### Dritter vollständiger Installationstest (finales ISO #7, UEFI)

| Schritt | Ergebnis | Details |
|---|---|---|
| Komplette Installation über die Oberfläche bis „Alles erledigt“ | ✅ | inkl. Schritte `novaos-efi-fallback` und NVRAM-Eintrag aus dem Live-System |
| Neustart in **derselben** Test-VM wie bei der Installation | ❌ (Testaufbau) | wieder UEFI-Shell, `bcfg boot dump` ohne NovaOS-Eintrag. **Ursache im Testaufbau:** Mein Testtreiber gab nur dem CD-Laufwerk einen QEMU-`bootindex`, der Festplatte keinen. OVMF gleicht die Booteinträge bei jedem Neustart mit dieser QEMU-Bootreihenfolge ab und nimmt nicht passende Einträge (hier: „NovaOS“ auf der Festplatte) aus der Bootreihenfolge; ohne CD und ohne `bootindex` stand die eingebaute UEFI-Shell vor der Festplatte. |
| Testtreiber korrigiert: Festplatte bekommt immer einen `bootindex` (wie bei virt-manager/GNOME Boxes) | ✅ | `tests/qemu/novaqemu.py` |
| Start der **unveränderten** installierten Festplatte, NVRAM unverändert | ✅ | Firmware startet `Boot0001 "UEFI Misc Device"` (Fallback-Pfad `EFI/BOOT/BOOTX64.EFI` = shim) → shims Fallback legt den Eintrag an → Anmeldebildschirm, **ohne jeden Eingriff** |
| NVRAM danach (aus `OVMF_VARS.fd` ausgelesen) | ✅ | `BootOrder: 0003 0001 0000 0002`, `Boot0003 "NovaOS" → \EFI\NovaOS\shimx64.efi` |
| „Neu starten“ am Anmeldebildschirm | ✅ | serielle Konsole: `BdsDxe: starting Boot0003 "NovaOS"` → Splash → Anmeldebildschirm |
| „Herunterfahren“ am Anmeldebildschirm | ✅ | VM schaltet sich aus |

**Bewertung:** Das installierte System startet unter UEFI selbstständig – entweder über den
NVRAM-Eintrag „NovaOS“ oder, falls die Firmware ihn verwirft oder nie gespeichert hat, über
den Fallback-Pfad, der den Eintrag automatisch neu anlegt. Beides ist jetzt im Gesamtablauf
getestet. Einschränkung: Eine Firmware, die eine UEFI-Shell **vor** der Festplatte startet
(nur in manchen VM-Konfigurationen), bleibt in der Shell stehen; dann hilft einmalig
`fs0:\EFI\BOOT\BOOTX64.EFI` (siehe [INSTALLATION.md](INSTALLATION.md)).

Weitere Beobachtungen: Systemmonitor-Widgets zeigten erst nach einiger Zeit Daten; nach
einem Auflösungswechsel in der VM wurden die Desktop-Widgets über die Desktop-Symbole
verschoben → Layout in Build #6 kompakter (passt in 1024×768). Der Willkommensassistent
erschien erneut, wenn er per X geschlossen wurde → Schließen zählt ab Build #6 als erledigt.

### Trailer-Aufnahmen (Live-System, Full-HD) – dabei gefunden

| Befund | Ergebnis |
|---|---|
| Live-System in 1920×1080 (QEMU std-VGA mit EDID, 64 MB Videospeicher) | ✅ Desktop, Store, alle Nova-Apps, Browser, Dunkelmodus aufgenommen (`docs/screenshots/fullhd/`) |
| Full HD mit 16 MB Videospeicher | ❌ KWin „atomic commit failed: out of memory“ → Testtreiber nutzt 64 MB |
| Nova Store: echte App-Installation (Tux Paint) mit Fortschritt, danach „Öffnen“ startet die App | ✅ |
| Hell → dunkel im laufenden Betrieb | ⚠️ → behoben: bereits geöffnete Nova-Apps behielten die hellen Farben (dunkle Schrift auf dunklem Grund). Die Apps berechnen ihr Stylesheet jetzt bei `ApplicationPaletteChange` neu (Unit-Test `test_style.py`). Im ISO `build-2` ist dieser Fehler noch enthalten. |

### PC-Update-Kanal: Update von 0.1.0 (26.11.2026)

Getestet in einem Debian-13-System (debootstrap, chroot) mit den Paketen `novaos-base` und `novaos-apps`
**0.1.0** aus dem Commit des ISO `build-2`, der Kanal mit einem Testschlüssel lokal bereitgestellt.

| Test | Ergebnis | Details |
|---|---|---|
| Kanal einrichten (`novaos-update-einrichten.deb`) | ✅ | Quelle `Suites: /` + Schlüssel, `apt-get update` prüft die Signatur, NovaOS-Pakete erscheinen als Update |
| Update mit dem Helfer von 0.1.0 (`nova-apt-helper refresh/upgrade`, wie „Alle Updates installieren“) | ✅ | keine Fehler, `dpkg --audit` leer, os-release „NovaOS 0.1.1“, 14-Tage-Timer aktiviert, „Software-Update“ für die Systemeinstellungen installiert |
| Tägliche Sicherheitsupdates nach dem Update | ⚠️ → behoben | 0.1.1 hätte sie ausgeschaltet (neue Voreinstellung) → `novaos-base` lässt sie bei Updates von 0.1.0 an; erneut getestet ✅ |
| Suche + Installation beim Neustart (`nova-scheduled-update check --force`, dann `offline`) | ✅ | 3 Pakete geladen, `/system-update` gesetzt, „offline“ installiert, Marker entfernt, `last-result` ok |
| Nova Updates (offscreen) | ✅ | startet, „Sicherheitsupdates zusätzlich täglich“ angehakt; Knopf heißt jetzt „Nach Updates suchen“ |
| Unit-Tests (im Debian-13-System) | ✅ | 91 bestanden |
| Echter Kanal auf GitHub (0.1.1.84, Schlüssel aus dem Workflow) | ✅ | genau die Schritte aus der Anleitung: `wget` + `sudo apt install` von `novaos-update-einrichten.deb`, dann Update mit dem Helfer von 0.1.0 → alle drei Pakete 0.1.1.84, Signatur geprüft, tägliche Sicherheitsupdates an, „Software-Update“ installiert |

**Nova WLAN (0.1.1.86):** Unit-Tests für die `nmcli`-Ausgabe (Doppelpunkte im Namen, gleiche Netze mehrfach,
versteckte Netze, Fehlermeldungen); Willkommensseite offscreen mit simulierten Netzen: Liste, Passwortfeld erst nach
Auswahl, „Das Passwort stimmt nicht“ bei falschem Passwort ✅. **Nicht getestet:** echtes WLAN (die Testumgebung hat
keins). `shakecursorEnabled=false` laut KWin-6.3-Quelltext (eingebauter Effekt `shakecursor`).

**Neustart nach dem Update (0.1.1.89):** Update von `novaos-base` im Debian-13-System setzt `/run/reboot-required`
(+ `.pkgs`) ✅; Nova Updates offscreen: „Fertig – bitte neu starten“, „Jetzt neu starten“ als Hauptknopf, Frage
„Jetzt neu starten / Später“ nach der Installation ✅ (`docs/screenshots/glass/07`); 96 Tests ✅.

**Nova Updates (0.1.1.88):** offscreen im Debian-13-System gerendert – „Alles ist aktuell“ und „3 Updates
verfügbar“ (`docs/screenshots/glass/05`, `06`); erster Versuch zeigte „Neu in …“ nicht (Pfad zu früh gelesen) und
eine zu hohe Karte → behoben; Unit-Test für die Neuerungen-Datei; 96 Tests ✅.

**Glas-Design (0.1.1.87):** Willkommens-Splash (QML) im Debian-13-System offscreen gerendert – Glas-Karte mit Logo,
„Willkommen“ und Balken ✅ (`docs/screenshots/glass/01-anmelde-splash-glas.jpg`); Nova Welcome mit neuem Stylesheet
✅ (02–04); `nova-look-update` mit simuliertem `busctl`: wartet auf Plasma (2 Fehlversuche), setzt die Leiste,
merkt sich die Version, zweiter Lauf ohne Aufruf ✅; 95 Unit-Tests ✅. Einstellungsnamen geprüft im Quelltext von
Plasma 6.3 (`Panel.opacity`, Breeze `MenuOpacity`/`ShadowSize`/`OutlineIntensity`/`TitleAlignment`, SDDM-Breeze
`showlogo`/`logo`/`background`). **Nicht getestet:** echter Plasma-Desktop, Anmeldebildschirm und Plymouth beim
Booten (keine grafische Sitzung in der Testumgebung).

**Nicht getestet:** echter Neustart in `system-update.target` mit Plymouth (nur im chroot, ohne systemd) und
der Eintrag in den echten KDE-Systemeinstellungen (Ort laut Quelltext von Plasma 6.3:
`/usr/share/plasma/systemsettings/externalmodules/`).

## NovaOS Mobile (Startbildschirm für Android-Handys)

Getestet am 19.11.2026 mit **Android-x86 9.0-r2** in QEMU als Handy (Bildschirm per `wm size
720x1280`, 280 dpi = 411 × 731 dp wie ein Galaxy-Handy), mit Internet über den Proxy der
Testumgebung (Ausgang USA). Bedient mit Touch-Ereignissen (`input tap/swipe`). Bilder:
[docs/screenshots/mobile/](screenshots/mobile/).

| Test | Ergebnis | Details |
|---|---|---|
| Build ohne Gradle (`mobile/build.sh`), Logik-Tests | ✅ | APK 2,4 MB; teilt Code mit NovaOS TV, deren 234 Tests weiter bestehen |
| Als Startbildschirm setzen (`HOME`), Home-Taste | ✅ | Begrüßung, Uhr, Datum, Suche, Nova-Karten, Schnelleinstellungen, Dock |
| Inhalte unsichtbar: leere Dock-Plätze nahmen die ganze Höhe ein | ❌ → behoben | leere Plätze 1 px hoch |
| Seitenabstand fehlte (`fitsSystemWindows` ersetzt das Padding) | ❌ → behoben | Rahmen-Layout |
| Nach oben wischen → App-Übersicht, Suche „chr“ → Chrome | ✅ | „1 apps“ → Pluralform korrigiert |
| App gedrückt halten → Menü → „Zu Favoriten hinzufügen“ → erscheint unter Favoriten | ✅ | |
| Fernsehen → Senderkacheln → tagesschau24 / Das Erste live im Querformat | ✅ | |
| Antippen blendete die Bedienknöpfe nicht ein | ❌ → behoben | Touch-Geste wird jetzt ganz verarbeitet; Wischen wechselt den Sender |
| Nova Filme: Reihen mit Vorschaubildern, Film antippen → Filmseite | ✅ | Wiedergabe aus den USA gesperrt (Fehler 150, nur Deutschland) – Hinweis + „In der YouTube-App öffnen“ |
| Einstellungen: als Startbildschirm, Favoriten zurücksetzen, Über/Datenschutz | ✅ | |

**Nicht getestet:** echtes Samsung-Handy (One UI, Auto Blocker), Taschenlampe (VM ohne Blitz),
Benachrichtigungen per Wischen, Filmwiedergabe aus Deutschland, Ton.

### Software-Update, Nova Store und Nova-Apps (19.11.2026)

Gleiche Umgebung (Android-x86 9 als Handy, Systemsprache Englisch). Für den kompletten
Update-Weg lief ein **lokaler HTTPS-Server** mit eigenem Test-Zertifikat als Ersatz für die
NovaOS-Website (GitHub Pages ist noch nicht eingeschaltet): Testversionen 0.1.1.900–903 mit
derselben Signatur, `update.properties` und APK genau so, wie `mobile/build.sh` sie für die
Website erzeugt. Bilder 08–16 in [docs/screenshots/mobile/](screenshots/mobile/).

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests (`MobileTest`) | ✅ | 122 bestanden: Update-Beschreibung, Store-Katalog (echte `store.txt`), Dateinamen, Pfadschutz |
| Erster Start → Start-Animation „Willkommen bei NovaOS“ → Startbildschirm | ✅ | Texte standen links statt mittig ❌ → behoben |
| Startbildschirm fragt den Update-Server, Hinweis **„Systemsoftware-Update verfügbar“** | ✅ | Karte über den Nova-Karten, antippen → Software-Update |
| Software-Update: Version, Größe, „Was ist neu“ → *Herunterladen und installieren* | ✅ | Download, SHA-256 und Signatur geprüft, dann Androids Dialog „Update installieren?“ |
| Nach der Installation startet NovaOS **von selbst** neu | ❌ → behoben | zuerst erschien die zuletzt offene App; jetzt startet NovaOS nach dem Update selbst (`MY_PACKAGE_REPLACED`) |
| Update-Animation „Update wird abgeschlossen“ → „Neu in NovaOS 0.1.1.903“ | ✅ | Karte war zu durchsichtig ❌ → deckend |
| Ohne Website (GitHub Pages aus): „Update-Server nicht erreichbar“ | ✅ | |
| Einstellungen → **NovaOS neu starten** | ✅ | Animation, NovaOS-Prozess wirklich neu gestartet (neue Prozess-ID), danach Startbildschirm |
| **Nova Store**: Katalog mit echten F-Droid-Versionen und -Symbolen | ✅ | z. B. Organic Maps 2026.11.11, Thunderbird 25.0 |
| Nova Store → Breezy Weather **installieren** | ✅ | „Unbekannte Apps erlauben“ → echter Download von f-droid.org → Androids Installationsdialog → installiert, Knopf zeigt „Öffnen“ |
| Nova-Apps mit eigenen Symbolen in der App-Übersicht; Nova Kamera im Dock (VM ohne Kamera-App) | ✅ | |
| **Nova Notizen**: neue Notiz, automatisch gespeichert, Liste | ✅ | Enter im Titel machte einen Zeilenumbruch ❌ → springt jetzt zum Text; „0 minutes ago“ → „Gerade eben“; „+“ halb unter der Navigationsleiste ❌ → behoben |
| **Nova Galerie**: Berechtigung, Raster, Vollbild, Wischen, Teilen/Löschen | ✅ | Löschen scheiterte unter Android 9 ❌ → Schreibrecht wird mit angefragt (gleiche Frage), jetzt gelöscht |
| **Nova Dateien**: Abkürzungen, Ordner, Datei öffnen (in Androids Galerie über den NovaOS-Dateianbieter), Umbenennen, neuer Ordner | ✅ | |
| **Nova Kamera**: Berechtigung, „Keine Kamera gefunden“ (VM hat keine), letztes Foto als Knopf | ✅ | Auslöser teils unter der Navigationsleiste ❌ → behoben. **Fotografieren selbst nicht getestet** (keine Kamera in der VM) |

**Nicht getestet:** Fotografieren und Blitz auf einem echten Handy, Android 11+ („Zugriff auf alle
Dateien“, Löschabfrage von Android, Hintergrund-Start nach dem Update).

#### Update-Kanal ohne GitHub Pages (ghcr.io), 19.11.2026

Auf einem Samsung-Tablet zeigte 0.1.1.38 „Update-Server nicht erreichbar“: Das Repository ist
privat, GitHub Pages dafür kostenpflichtig. Neu: Update als öffentliches Paket in ghcr.io.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 141 bestanden (neu: JSON, Token, Manifest-Digests, Registry-Adressen) |
| GitHub Actions: Schlüssel, Build, Release, Upload nach ghcr.io (Lauf 4) | ✅ | Schlüssel einmal erzeugt (Entwurfs-Release), 0.1.1.39 als `mobile-build-4` und `ghcr.io/superali99-tech/novaos-mobile:latest`; anonym noch 401, bis das Paket öffentlich ist |
| `publish_update.sh` gegen `tests/update/fake_registry.py` | ✅ | zweiter Lauf lädt vorhandene Dateien nicht erneut hoch |
| App, Paket privat → „Der Update-Kanal ist noch nicht freigegeben“ | ✅ | |
| App, Paket öffentlich → Update 0.1.1.910 → 911 gefunden, geladen, installiert | ✅ | Token → Manifest → Datei über Weiterleitung zum Speicher; der Login-Token geht nicht mit (der Ersatz-Speicher lehnt ihn ab wie GitHub) |
| Nach der Installation: NovaOS startet selbst, „Update wird abgeschlossen“ → „Neu in NovaOS 0.1.1.911“ | ✅ | |

Danach wurde das **Repository** öffentlich gemacht (nicht das Paket). Neu deshalb: zweiter
Update-Kanal über das feste Release `mobile-update` (0.1.1.42, Lauf 5).

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 148 bestanden (neu: Release-Adressen) |
| Release `mobile-update` ohne Anmeldung lesbar, APK passt zur SHA-256, gleiches Zertifikat wie 0.1.1.39 | ✅ | Zertifikat SHA-256 `24:be:02:5d…` |
| Nova Dateien: Vorschaubilder für Fotos | ✅ | |
| Nova Store: Öffi-Symbol | ❌ → behoben | App steht zweimal in der Liste (empfohlen + Kategorie), nur eine Zeile bekam das Ersatz-Symbol |
| Testversion (Entwicklerschlüssel) findet 0.1.1.42 über das Release, lädt es, SHA-256 stimmt, stoppt bei „anderer Schlüssel“ | ✅ | wie vorgesehen |
| Echte 0.1.1.39 aus dem Release installiert | ✅ | **Google Play Protect: „Schädliche App blockiert“** → *Weitere Details → Trotzdem installieren* |
| 0.1.1.39: Update-Seite | ✅ | „Update-Kanal noch nicht freigegeben“ (kennt nur das Paket, das privat ist) |
| 0.1.1.42 über 0.1.1.39 installiert (wie über den festen Link) | ✅ | ohne Deinstallieren, Daten bleiben; NovaOS startet selbst mit „Neu in NovaOS 0.1.1.42“ |
| 0.1.1.42: Update-Seite | ✅ | „NovaOS ist auf dem neuesten Stand“ (über das Release) |

**Nicht getestet:** das ghcr.io-Paket öffentlich (ist privat), Play Protect auf dem Samsung-Tablet.

#### Updates von selbst (Hintergrundsuche, Benachrichtigung), 19.11.2026

Wunsch: Ein neues Update soll erscheinen, ohne in den Einstellungen zu suchen. Test mit dem
Ersatz-Server (Testversionen 0.1.1.950 → 951 → 952):

| Test | Ergebnis | Details |
|---|---|---|
| Erster Start → Startbildschirm sucht selbst, lädt im WLAN, Benachrichtigung „bereit – tippen zum Installieren“ | ✅ | |
| Die Start-Animation räumte das gerade geladene Update mit weg | ❌ → behoben | Aufräumen behält die APK eines ausstehenden Updates |
| Hinweis auf dem Startbildschirm zeigte noch „tippen zum Installieren“ statt „bereit“ | ❌ → behoben | Startbildschirm aktualisiert sich sofort, wenn die Hintergrundsuche etwas findet |
| Hintergrundsuche (JobScheduler) bei offenem Startbildschirm, ohne Bedienung: 952 gefunden, geladen, Hinweis + Benachrichtigung | ✅ | Job geplant (alle 3 Stunden, übersteht Neustarts) |
| Benachrichtigung antippen → „Update bereit zur Installation“ → „Jetzt installieren“ | ✅ | kein zweiter Download; Androids Dialog → installiert → „Neu in NovaOS 0.1.1.952“; Benachrichtigung verschwindet |

### Nova Chat, Anrufe, Nova-Assistent, Update-Stufen (20.11.2026)

Android-x86 9 als Handy; **eigener Matrix-Server** (Synapse 1.165, HTTPS mit Test-Zertifikat) in
der Testumgebung mit zwei Konten; Gegenüber „Bob“ = `tests/chat/peer.js` (kleiner Matrix-Client in
Chromium mit simulierter Kamera/Mikrofon, antwortet auf Nachrichten und Anrufe). Bilder 17–24.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 243 bestanden (neu: 74 Nova-Sätze DE/EN, Rechnen, Matrix-/sync, Kontakte, Wetter) |
| Nova Chat: Anmelden (Passwort) | ✅ | @alice:nova.test |
| Erste Synchronisierung ohne Chats zeigte weiter „Laden …“ | ❌ → behoben | |
| Neuer Chat mit @bob, Kontakt „Bob“ wird gespeichert | ✅ | Kontakte liegen im Konto auf dem Server (`org.novaos.contacts`) |
| Nachrichten hin und zurück in Echtzeit | ✅ | Sprechblasen schnitten den Text ab ❌ → behoben |
| Neue Nachricht bei geschlossenem Chat → Benachrichtigung „Bob: …“ | ✅ | sofort (Synchronisierung läuft mit dem Startbildschirm) |
| Sprachanruf Alice → Bob | ✅ | „Wird angerufen …“ → „Verbunden 00:10“, Audio fließt, Auflegen kommt bei Bob an |
| Anruf Bob → Alice (vom Startbildschirm aus) | ✅ | klingelt mit Annehmen/Ablehnen → „Verbunden“; zuerst ohne Verbindung ❌ → behoben (Signale vor dem Öffnen des Anrufbildschirms gingen verloren) |
| Nova: Mikrofon-Knopf, Erlaubnis, „Ich höre zu …“ | ✅ | echte Spracheingabe nicht prüfbar (VM ohne Mikrofon) |
| Nova: getippte Befehle (Rechnen, Uhrzeit, Notiz, Witz) | ✅ | Notizen wurden kleingeschrieben ❌ → behalten jetzt die Schreibweise |
| Startbildschirm: Karten „Nova Chat – 3 neue Nachrichten“ und „Nova“ | ✅ | |
| Update 0.1.1.960 → 961 mit Stufen | ✅ | 5 % Herunterladen → 66 % Prüfen → 81–92 % Vorbereiten → nach ca. 2:20 Androids Abfrage → installiert, Neustart |

**Nicht getestet:** Videoanruf mit echter Kamera, matrix.org selbst (keine Testkonten angelegt),
echte Spracherkennung und „Hey Nova“ (kein Mikrofon in der VM), Wetter im Gerät (Open-Meteo-Antworten
nur in den Logik-Tests), Android 11+.

### Nova Chat: Sprachnachrichten, Emojis, Fotos, Nova schreibt für dich (20.11.2026)

Gleicher Aufbau (Android-x86 9 als Handy, eigener Synapse-Server); „Bob“ sendet über die Matrix-API
Text, Foto, Sprachnachricht (Ogg Opus wie Element) und Emojis. Bilder 25–29.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1284 bestanden (neu: Medien in /sync, bearbeitete/gelöschte Nachrichten, Antwort-Zitate, mxc-Adressen, 900+ Emojis, Schnellantworten, Diktat, Wellenbild, Nova-Sätze, alle Texte auf Deutsch und Englisch) |
| Foto von Bob: Vorschau in der Sprechblase | ✅ | geladen über die angemeldete Medien-Schnittstelle (wie matrix.org sie verlangt) |
| Sprachnachricht von Bob (Ogg Opus): Wellenbild, Abspielen mit Fortschritt | ✅ | Blase war zu schmal ❌ → behoben |
| Nur Emojis (😂👍) werden groß gezeigt | ✅ | |
| Emoji-Tastatur: Kategorien, Einfügen, Löschen (auch ❤️ mit Zusatzzeichen), Senden | ✅ | bei Bob angekommen |
| Schnellantwort „Ja 👍“ auf „Kommst du heute Abend?“ | ✅ | ein Tipp, bei Bob angekommen |
| Sprachnachricht aufnehmen und senden | ✅ | bei Bob: AAC, 44,1 kHz, mono, 8 s (Wellenbild leer, die VM hat kein Mikrofon) |
| Foto aus der Galerie senden | ✅ | 1200×1600, bei Bob angekommen |
| Benachrichtigung bei Sprachnachricht | ✅ | „Bob – 🎤 Sprachnachricht“ |
| Nova-Hilfe im Chat öffnet sich, „Vorlesen“ | ✅ | Knöpfe waren auf schmalen Handys abgeschnitten ❌ → zwei Reihen |
| Nova-App: „Lies meine Nachrichten vor“ | ✅ | „Du hast 2 neue Nachrichten. Bob schreibt: … Bob hat eine Sprachnachricht geschickt.“ |
| Nova-App: „Schreib Bob im Chat: Alles klar komma bis gleich daumen hoch“ | ✅ | zuerst nicht verstanden ❌ → behoben; bei Bob: „Alles klar, bis gleich 👍“ |

**Nicht getestet:** echte Spracheingabe (die VM hat keine funktionierende Spracherkennung und kein
Mikrofon), Vorlesen mit hörbarer Stimme (kein Ton in der VM), Sprachnachrichten in Element selbst.

### Nova Mail (20.11.2026)

**Echter Mailserver** in der Testumgebung: Dovecot 2.3 (IMAP mit TLS auf 3993 und STARTTLS auf 3143,
Test-Zertifikat, Ordner mit Sonderrollen) und ein kleiner SMTP-Server (`tests/mail/smtp_server.py`,
TLS 465-Art und STARTTLS 587-Art, liefert in die Dovecot-Postfächer). Bilder 30–37.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1327 bestanden (neu: MIME lesen/bauen mit Umlauten, Anhängen, RFC-2231-Namen, Quoted-Printable, Datumsformate, IMAP-Antworten, UTF-7-Ordnernamen, Server-Suche, Nova-Sätze zu E-Mails) |
| `tests/mail/run.sh` gegen Dovecot | ✅ | 20 bestanden: Senden über TLS und STARTTLS, Ordner mit Rollen, Kopfzeilen, ganze Mail mit Umlauten/Anhang/Punkt-Zeile, ungelesen bleibt ungelesen beim Lesen, Gelesen-Markierung, „Gesendet“-Kopie, Papierkorb, endgültig löschen, falsches Passwort (IMAP und SMTP) wird erkannt, **Zertifikat für einen anderen Namen wird abgelehnt** |
| Gerät: Anbieterauswahl, GMX-Hinweis mit Hilfeseite | ✅ | |
| Gerät: Konto über „Anderer Anbieter“ mit eigenen Servereinstellungen | ✅ | Felder ohne Beschriftung waren nach dem Ausfüllen nicht zu unterscheiden ❌ → Beschriftungen |
| Posteingang mit Vorschau, ungelesen-Punkt, 📎 | ✅ | 4 Mails |
| HTML-Mail | ✅ | weiße Seite, Bild aus dem Internet blockiert, „Bilder laden“; Link fragt nach |
| Anhang öffnen | ✅ | Foto öffnet sich in der Galerie |
| Antworten | ✅ | bei Bob: „Re: Fotos vom Urlaub“, In-Reply-To/References richtig, Zitat; Kopie in „Gesendet“ |
| Neue E-Mail mit Adressvorschlag und Foto-Anhang | ✅ | bei Bob: multipart/mixed mit image/jpeg |
| Benachrichtigung bei neuer E-Mail | ✅ | „Bob Becker – Treffen am Samstag 🍰“ (Hintergrund-Job) |
| Startbildschirm-Karte „Nova Mail“ | ✅ | |
| Nova: „Lies meine E-Mails vor“ | ✅ | „Du hast 3 ungelesene E-Mails. Von Bob Becker: …“ |
| Nova: „Schreib eine E-Mail an Bob: Hallo Bob komma bis morgen“ | ✅ | neue E-Mail an Bob Becker mit „Hallo Bob, bis morgen“ |

**Nicht getestet:** echte Anbieter (GMX, Gmail usw. – keine Testkonten), Outlook.com (geht absichtlich
nicht, siehe README), sehr große Postfächer (Nova Mail lädt die neuesten 80 Mails je Ordner).

### Einrichtung beim ersten Start (20.11.2026)

Bilder 38–43.

| Test | Ergebnis | Details |
|---|---|---|
| Nach dem Update: Start-Animation → „Was ist neu“ → Einrichtung | ✅ | |
| „Weiter mit Google“ | ✅ | Androids echte Google-Anmeldung öffnet sich (Google Play-Dienste); weiter ging es nicht, weil die VM kein Internet hat |
| „Weiter mit E-Mail“ mit Adresse ohne auffindbaren Server | ✅ | „imap.nova.test ist nicht erreichbar“ und „Ohne Prüfung weiter“ |
| Bildschirmsperre: „PIN“ öffnet Androids Auswahl, PIN 1234 eingerichtet | ✅ | zurück in NovaOS: „Bildschirmsperre ist eingerichtet ✓“ |
| Berechtigungen: „Alle erlauben“ | ✅ | Androids Dialoge, danach alle mit ✓ |
| Alter 11 | ✅ | Hinweis auf Nova Family erscheint unter 16 |
| Fertig → Startbildschirm „Guten Abend, Alice“ | ✅ | |

### Nova Family (20.11.2026)

Weil die VM kein Internet hat: lokaler ntfy-kompatibler Dienst (`tests/family/fake_ntfy.py`, HTTPS mit
Test-Zertifikat) und ein Test-Partner mit derselben Verschlüsselung wie die App (`tests/family/run.sh`, spielt
das andere Handy). Für den Test war die Dienst-Adresse auf den lokalen Dienst gestellt (sonst ntfy.sh).
Bilder 44–51.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1368 bestanden (neu: Familien-Code, Kanal und Schlüssel, Verschlüsseln/Öffnen, fremde/veränderte Nachrichten abgelehnt, PIN-Prüfsumme, Sperre jetzt/bis, Ruhezeit über Mitternacht, nur vor Schultagen, mittags, Bildschirmzeit, „Mehr Zeit“ nur heute, Regeln als JSON, Uhrzeiten, ntfy-Zeilen) |
| ntfy.sh selbst (aus der Testumgebung) | ✅ | Senden und Abholen funktionieren (`/json?poll=1`) |
| **Testgerät als Kinderhandy:** Code eingeben, „Lisa“ | ✅ | Beitritt, Status und App-Liste kommen verschlüsselt beim Eltern-Partner an; Schutz-Liste zeigt ✓ |
| Eltern: „Jetzt sperren“ mit Nachricht | ✅ | nach wenigen Sekunden „Dein Handy wurde gesperrt – Bis deine Eltern es entsperren“, Nachricht, „Mama anrufen“, Notruf 112, Taschenlampe, Eltern fragen |
| Home-Taste und geöffnete Einstellungen während der Sperre | ✅ | der Sperrbildschirm bleibt über allem |
| „Eltern fragen → Bitte entsperren“ | ✅ | Bitte kommt bei den Eltern an |
| „Mama anrufen“ | ✅ | Telefon-App mit Mamas Nummer, Sperrbildschirm verschwindet; ohne Anruf nach ca. 90 s wieder da |
| Ruhezeit 19:00–7:00 | ✅ | „Ruhezeit – Komme am Samstag um 07:00 Uhr wieder“ → jetzt „Komme morgen um …“ |
| Eltern-PIN am Kinderhandy | ✅ | 30 Minuten frei, Eltern bekommen Bescheid |
| Familie verlassen am Kinderhandy | ✅ | nur mit der Eltern-PIN (falsche PIN abgelehnt) |
| **Testgerät als Elternhandy:** Familie anlegen | ✅ | Familien-Code wird angezeigt |
| Kinderhandy „Max“ (Partner) tritt bei | ✅ | Karte mit Zustand, Akku, Bildschirmzeit, Warnung „Schutz unvollständig“; Benachrichtigung „Kinderhandy verbunden“ |
| „Jetzt sperren“ | ✅ | Regeln mit Sperre, Prüfsumme der Eltern-PIN und Elternnummer gehen hinaus |
| Bitte „30 Minuten mehr“ → Benachrichtigung „Erlauben“ | ✅ | Regeln und Antwort gehen hinaus; bei Sperre von Hand gab es nur Bildschirmzeit dazu ❌ → gibt jetzt 30 Minuten frei |
| Ruhezeit „Täglich 21:00 – 7:00“, Apps TikTok und Roblox sperren | ✅ | kommt beim Kind an; die Karte zeigt neue Regeln jetzt sofort („wird übertragen …“) |

**Nicht getestet:** zwei echte Handys über das echte ntfy.sh, Samsung/Android 14 (Hintergrund-Regeln,
Akkusparen), echter Anruf während der Sperre (VM ohne Telefon), gesperrte App mit Nutzungszugriff,
Neustart des Kinderhandys.

### Update 0.1.1.59: Update-Menü, Fortschritt, Suche, Kontakte (21.11.2026)

Android-x86 9 in QEMU, Hochformat 720×1280, Deutsch; Internet über den Proxy der Testumgebung.
Für den Update-Test lief eine Testversion 0.1.1.57 gegen das echte Update 0.1.1.58 auf ghcr.io. Bilder 52–57.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1377 bestanden (neu: Suche mit Umlauten/Akzenten/mehreren Wörtern, Update-Verlauf lesen) |
| Software-Update: ⋮ → „Was ist neu“, „Update-Verlauf“, „Nach Updates suchen“ | ✅ | Verlauf zeigt die installierte Version und alle früheren |
| Update 0.1.1.58 gefunden, „Aktualisieren“ | ✅ | Ring, Prozent, „360 KB von 2,7 MB“, Restzeit, Schritte |
| Anzeige „1,3 MB von 2,7 MB · vollständig geladen“ gleichzeitig | ❌ → behoben | der Download war längst fertig, die Anzeige gestreckt; jetzt echte MB, echte Geschwindigkeit, danach „✓ vollständig geladen“ |
| Prüfung des Updates gegen den NovaOS-Schlüssel | ✅ | die lokal (mit anderem Schlüssel) gebaute Testversion lehnt das echte Update richtig ab – mit dem Release-Schlüssel passt er |
| Nova Store: Suche „karte“ | ✅ | Organic Maps und Google Maps, leere Kategorien ausgeblendet |
| Nova Mail: „Neue E-Mail-Adresse erstellen“ | ✅ | Erklärung und GMX, WEB.DE, freenet, Gmail (Links geprüft: Seiten erreichbar) |
| Outlook-Hilfelink | ❌ → behoben | zeigte auf eine nicht vorhandene Microsoft-Seite (404); jetzt die echte Seite „POP-, IMAP- und SMTP-Einstellungen für Outlook.com“ |
| Adressbuch: Kontakt aus Mail-Vorschlag speichern, zweiten Kontakt anlegen, Suche „mueller“ | ✅ | findet „anna.mueller@web.de“; Tippen: E-Mail schreiben, Anrufen, Bearbeiten, Löschen |
| „Hey Nova“ standardmäßig aus | ✅ | kein Zuhören (und kein Android-Ton) nach dem Zurückkehren auf den Startbildschirm |

**Nicht getestet:** Suche und „Als Kontakt speichern“ in Nova Chat mit einem Matrix-Konto (kein Matrix-Server in
dieser Sitzung), Mail-Suche mit echtem Postfach.

### Update 0.1.1.60: Nova Store wie der Play Store (21.11.2026)

Android-x86 9 in QEMU, Hochformat, Deutsch, Internet über den Proxy der Testumgebung (echtes f-droid.org). Bilder 58–61.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1398 bestanden (neu: `store_meta.json` für alle F-Droid-Apps, nur https-Links und sichere Bildpfade, `@apps:`/`apps=`, unbekannte Nova-App-Namen) |
| Detailseite Aegis | ✅ | Beem Development, 6,6 MB, 3.4.3, Android 6.0+, GPL-3.0, Bildschirmfotos von F-Droid, „Über diese App“ |
| Installieren Aegis | ✅ | Ring, „Wird installiert …“, Androids Nachfrage, danach „Deinstallieren“ / „Öffnen“ |
| Nextcloud (107,3 MB): Fortschritt | ✅ | „7 % von 107,3 MB“ → „21 %“, Ring um das Symbol |
| „Abbrechen“ während des Downloads | ❌ → behoben | Tipp kam nicht an, weil die Knöpfe bei jedem Fortschritt neu gebaut wurden; jetzt nur bei Zustandswechsel – Abbruch bei 4 %, keine Datei bleibt zurück |
| Abbrechen im Android-Installationsfenster | ❌ → behoben | Store blieb bei „Wird installiert …“ stehen (Ergebnis kam, während die Seite pausiert war); jetzt wieder „Installieren“ |
| Heruntergeladene APKs | ❌ → behoben | blieben im Zwischenspeicher (6,9 MB / 107 MB); werden jetzt gelöscht |
| Nova-Apps mit simuliertem Update `apps=chat,mail` | ✅ | Nova Chat und Nova Mail oben mit „Aktualisieren“ und „Neu im nächsten Update (0.1.1.99)“, die anderen „Öffnen“ |
| Ohne Nova-Update | ✅ | „Empfohlen“ oben, Nova-Apps am Ende |

**Nicht getestet:** „Aktualisieren“ einer F-Droid-App mit neuerer Version (keine veraltete App installiert),
Deinstallieren-Dialog, Play-Store-Apps (kein Play Store in der VM).

### Update 0.1.1.61: Nova Musik (21.11.2026)

Android-x86 9 in QEMU, Hochformat, Deutsch, Internet über den Proxy der Testumgebung (echtes Deezer und
radio-browser.info). Die VM hat keine Tonausgabe – geprüft wurde, dass die Wiedergabe läuft (Position, Zustand der
MediaSession). Bilder 62–67.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1418 bestanden (neu: Radio nur https, doppelte Sender, Namen säubern, Genres; Deezer-Antwort, Fehler, Links; Nova-Befehle „Spiel Radio SWR3“, „Spiel 1LIVE im Radio“, „Spiel Atemlos von Helene Fischer“, „Nächstes Lied“, „Musik pausieren/weiter“, „spiel ZDF“ bleibt Fernsehen) |
| Songs: aktuelle Charts mit Covern | ✅ | |
| Suche „helene fischer“, Lied antippen | ✅ | spielt (0:05 / 0:29), danach automatisch das nächste Lied |
| Benachrichtigung und MediaSession | ✅ | MediaStyle mit ⏮ ⏯ ⏭; NovaOS ist die „Media button session“ |
| Kopfhörer-/Bluetooth-Tasten (`MEDIA_NEXT`, `MEDIA_PAUSE`) | ✅ | nächstes Lied, Pause |
| Radio: beliebte Sender mit Logo, SWR3 antippen | ✅ | live, zeigt das laufende Lied („Halo / Rea Garvey“) |
| Nova: „spiel radio 1live“ | ✅ | Nova Musik öffnet sich, 1LIVE läuft |
| Meine Musik: Erlaubnis, Testlied, abspielen | ✅ | „1 Lieder“ und „ · 0:20“ ohne Künstler → behoben („1 Lied“, „0:20“) |
| Startbildschirm: Karten Nova Musik und Nova Store | ✅ | |

**Nicht getestet:** echter Ton, echte Bluetooth-Geräte (die VM hat kein Bluetooth), Sperrbildschirm-Steuerung,
Android 14 (Vordergrunddienst „Medienwiedergabe“).

### Update 0.1.1.62: Nova Phone Shop (21.11.2026)

Android-x86 9 in QEMU, Hochformat, Deutsch. Bilder 68–72.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1459 bestanden (neu: jedes Modell vollständig, höchstens 1.200 €, mehr Speicher kostet mehr, günstige und teure Modelle, Preisformat „1.199 €“/„€1,199“) |
| Shop: Trailer-Banner wechseln, Filter, Modell-Karten | ✅ | jedes Modell mit eigenem Kameramodul (drei Linsen, Doppelkamera, Kamera-Leiste, Einzelkamera) und Farben |
| Produktseite Nova 2 Pro Max | ✅ | Bild vorne+hinten, Farbe „Polarlicht“ ändert das Bild, Antippen dreht es |
| Speicher 512 GB | ✅ | Preis 1.199 € |
| Wunschliste | ✅ | „Auf deiner Wunschliste“, Herz auf der Karte, Filter „Wunschliste“ |

### Update 0.1.1.63: Konto und Sicherheit, klügerer Nova (21.11.2026)

Android-x86 9 in QEMU, Hochformat, Deutsch; Wikipedia über den Proxy der Testumgebung. Bilder 73–78
(Androids PIN-Abfrage selbst lässt sich nicht fotografieren – Android sperrt Bildschirmfotos davon).

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1485 bestanden (neu: Einheiten, Tage bis Weihnachten/Silvester/Ostern, Osterdatum, Würfel/Münze, Wikipedia-Titel/Antwort/Sätze, Sperr-Regel, Codes: 6 Ziffern, Ablauf, Versuche) |
| Konto ohne Bildschirmsperre | ✅ | Infos, „keine“ Sperre, „PIN, Muster oder Passwort einrichten“ |
| PIN 1234 gesetzt, Nova Chat öffnen | ✅ | Androids PIN-Abfrage, nach 1234 öffnet sich der Chat |
| Bildschirm aus/an, Nova Mail öffnen, Abfrage abbrechen | ✅ | Nova Mail schließt sich, Startbildschirm |
| Konto öffnen → PIN; „Name ändern“ → erneut PIN → Dialog | ✅ | Name gespeichert |
| Nova: „wer ist albert einstein“ | ✅ | erste zwei Sätze aus Wikipedia |
| Nova: „wie viele tage bis weihnachten“, „5 km in meilen“ | ✅ | „Noch 34 Tage bis Weihnachten.“, „Das sind 3,11 Meilen.“ |

**Nicht getestet:** Fingerabdruck (VM ohne Sensor; auf Android 10+ fragt Android über BiometricPrompt nach
Fingerabdruck oder PIN), Bestätigungscode per E-Mail mit echtem Postfach, Abmelden.

### Update 0.1.1.64: Update-Seite wie bei Samsung, Drehen bricht nicht mehr ab (21.11.2026)

Android-x86 9, Testversion 0.1.1.62 gegen das echte Update 0.1.1.63 auf ghcr.io. Das Drehen wurde durch Umschalten der
Bildschirmgröße auf Querformat ausgelöst (dieselbe Neuerstellung der Seite wie beim Drehen). Bilder 79–82.

| Test | Ergebnis | Details |
|---|---|---|
| Seite „Update verfügbar“ | ✅ | Bild, Version und Größe, Neuerungen, Knopf „Aktualisieren“ |
| Titelbild zu grau | ❌ → behoben | eine Transparenz des Leuchteffekts blieb am Pinsel und dimmte alles |
| „Aktualisieren“, sofort „Pause“ | ✅ | „Update angehalten“, 74 %, „Fortsetzen“ |
| Querformat während der Pause | ✅ | Seite neu aufgebaut, Update weiter angehalten bei 74 % (vorher: Update abgebrochen) |
| Querformat: Navigationsleiste überdeckte den Knopf | ❌ → behoben | Abstände jetzt aus Androids Fensterrändern |
| „Fortsetzen“ im Querformat, zurück ins Hochformat | ✅ | läuft weiter bis zur Prüfung; die lokal signierte Testversion lehnt das echte Update dort richtig ab, der Zustand bleibt nach dem Drehen erhalten |

**Nicht getestet:** Drehen während „Vorbereiten“ mit einem passend signierten Update (gleiche Hintergrund-Logik), echtes
Drehen am Handy, langsames Netz mit Fortsetzen mitten im Download (HTTP-Range).

### Update 0.1.1.66: Neuerungen, Infoseiten der Nova-Apps, Update-Daten (21.11.2026)

Android-x86 9 in QEMU, Hochformat, Deutsch, Internet über den Proxy der Testumgebung. Bilder 83–88.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1544 bestanden (neu: 3-Stunden-Regel, wartendes Update, ausgeschaltet; Zeilen je Nova-App `{app}`; `appnote.*`; Datum aus dem Verlauf; **jede Zeile mit genau einem „\|“** – fängt den Fehler aus 0.1.1.64) |
| Software-Update | ✅ | „Zuletzt aktualisiert: Heute, 12:31“, „Zuletzt nach Updates gesucht: Heute, 12:33“ |
| ⋮ → Update-Verlauf | ✅ | installierte Version „Auf diesem Handy installiert: Heute, 12:31“, ältere „Erschienen am 21. November 2026“ |
| Nova Store direkt nach dem Update | ✅ | blauer Punkt nur bei „Nova Store“ (die App, die das Update ändert) |
| Nova Store antippen | ✅ | Infoseite: NovaOS, Version, kostenlos, keine Werbung, Öffnen, „Neuerungen •“ mit Datum und Punkten, „Über diese App“ |
| → bei Neuerungen | ✅ | „Neue Funktionen •“ und „Über diese App“ |
| ⋮ → „Neuerungen anzeigen“ aus | ✅ | Neuerungen verschwinden |
| Simuliertes Update für Nova Chat (`apps=chat,store`, eigene Zeilen) | ✅ | „Öffnen \| Aktualisieren“, „Kommt mit dem nächsten Update (Version 0.1.1.99)“ mit den Chat-Punkten |
| Angabe „Auf dem Har…“ abgeschnitten | ❌ → behoben | jetzt „Privat“ |

**Nicht getestet:** Neuerungen einer F-Droid-App (dafür hätte eine F-Droid-App erst aktualisiert werden müssen), das
Verschwinden nach genau 3 Stunden (nur als Logik-Test).

### Update 0.1.1.70: Nova Karte (21.11.2026)

Android-x86 9 in QEMU, Deutsch, Internet über den Proxy der Testumgebung, kein GPS im Emulator (Start ohne Standort =
Kartenmitte, Navigation als „Vorschau“). Hochformat 720×1280 (Bilder 89–93), Querformat 1280×720 (Bilder 94–95).

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1748 bestanden (neu: Kartenmathematik, Polyline, Routen-Antwort, Ansagen-Texte und -Zeitpunkte, Abweichen von der Route, Orte-Suche, doppelte Orte, Nova-Befehle, Vertrauensanker) |
| Karte öffnen | ✅ | OpenStreetMap-Kacheln laden, Ziehen/Zoomen, Quellenangabe „© OpenStreetMap-Mitwirkende“ |
| Suche „rathaus hamburg“ | ✅ | Rathaus, Harburg Rathaus, Rathaus Harburg |
| Gleicher Ort dreimal in den Treffern | ❌ → behoben | Einträge mit gleichem Namen innerhalb von 250 m werden zusammengefasst |
| Zoom-Knöpfe über den Treffern / dem Löschen-X | ❌ → behoben | Knöpfe verschwinden während der Suche und sitzen über der Karte unten |
| Ort wählen | ✅ | Karte mit Name, Adresse, „Route“, „Starten“, roter Pin |
| „Route“ im Test-Handy | ❌ → Ursache Testumgebung | der Proxy der Testumgebung schaltet sich bei `routing.openstreetmap.de` mit eigenem Zertifikat dazwischen; mit installiertem Proxy-Zertifikat ✅. Die echte Kette (Let's Encrypt YE2 → Root YE → X2 → ISRG Root X1) prüft Android 9 erfolgreich (eigener Test im Emulator) |
| Route Hbf → Rathaus | ✅ | „2 Min. (700 m)“, Ankunftszeit, „über Willy-Brandt-Straße (B 4) und Große Johannisstraße“, Hinweis „Start: Kartenmitte“ |
| Querformat: Route unter der Karte versteckt | ❌ → behoben | Karten schmal links wie bei Google Maps, Route und Pin im freien Teil zwischen Karte und Knöpfen |
| Querformat: „Schritte“ brach in vier Zeilen um | ❌ → behoben | Knöpfe einzeilig, Zeile lässt sich seitlich schieben |
| „Vorschau“ | ✅ | grünes Banner mit Pfeil „Rechts abbiegen auf Neue Burg – 200 m – Danach: Rechts abbiegen auf Trostbrücke“, unten „2 Min. · 600 m · 14:30“, Ton-Knopf, ✕ |
| Ansagen (Protokoll, der Emulator hat keine Sprachausgabe) | ✅ | „Vorschau der Route. Fahre Richtung Westen auf Willy-Brandt-Straße (B 4). Dann in 300 Metern rechts abbiegen auf Neue Burg.“ → „In 150 Metern rechts abbiegen auf Neue Burg, danach rechts.“ → „Jetzt rechts abbiegen …“ → … → „In 150 Metern erreichst du dein Ziel.“ → „Du hast dein Ziel erreicht – es ist links.“ |
| Ziel erreicht | ✅ | Karte „🏁 Ziel erreicht – Rathaus“ mit OK |
| Nova: „navigiere zum hamburger hauptbahnhof“ | ✅ | öffnet Nova Karte, sucht das Ziel, berechnet die Route und startet |
| … ohne bekannten Standort | ❌ → behoben | fuhr von der Kartenmitte los (im Taunus); jetzt wartet „Starten“ (und Nova) auf den Standort und rechnet dann die Route ab dort, nur „Vorschau“ simuliert |
| Nova im Querformat: Eingabefeld unten abgeschnitten | ❌ → behoben | kleinere Kugel im Querformat |
| Version 0.1.1.70 frisch installiert, Hochformat | ✅ | fragt einmal nach dem Standort; Suche, Ortskarte (Pin mittig zwischen Suchfeld und Karte), Route „6 Min. (2,1 km)“, Vorschau mit Banner „Links abbiegen – 10 m – Danach: Links abbiegen auf Stockmeyerstraße“ |
| „Starten“ ohne Standort (0.1.1.70) | ✅ | „Warte auf deinen Standort … die Navigation startet, sobald er bekannt ist …“ – keine Fahrt ab der Kartenmitte |
| Update auf ghcr.io | ✅ | `versionName=0.1.1.70`, `apps=map,nova` |

**Nicht getestet:** eine echte Fahrt mit GPS (der Emulator hat keins) und damit auch das Warten auf den Standort bis zum
Start und die Neuberechnung beim Abweichen (beides nur als Logik bzw. Code geprüft), die hörbare Sprachausgabe (keine
Sprach-Engine im Emulator; die Texte und Zeitpunkte sind im Protokoll geprüft).

### Update 0.1.1.72: Software-Update wie bei One UI (21.11.2026)

Android-x86 9, Hochformat 720×1280, Deutsch. Vorlage: Bilder von Samsung One UI 9 („One UI 9.0 – Deine Software ist auf
dem neuesten Stand.“, „Letztes erfolgreiches Update“). Bilder 96–99.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1759 bestanden (neu: „NovaOS 1.0“, „31. Oktober 2026 um 16:37“, Sicherheitspatch „5. Oktober 2026“, Liste der Aktualisierungsinformationen, Markierungen im Verlauf) |
| Software-Update ohne neues Update | ✅ | dunkle Seite, Leuchten blau oben / warm unten, „NovaOS 1.0“, „Deine Software ist auf dem neuesten Stand.“, ← und ⋮ |
| ⋮ | ✅ | „Letztes Update“, „Was ist neu“, „Update-Verlauf“, „Nach Updates suchen“ |
| „NovaOS 1.0“ antippen | ✅ | „Letztes erfolgreiches Update – 21. November 2026 um 18:07“, Text, „Alle Änderungen“, Aktualisierungsinformationen: NovaOS-Version 1.0, Android-Version 9, Version 0.1.1.71, Größe 2,9 MB, Sicherheitspatch-Ebene 5. August 2018 (die des Emulators), zuletzt gesucht |
| Titelbild | ❌ → behoben | erste Fassung sah eher wie ein Lolli aus; jetzt eine klare „1“ (oranger Bogen, blauer Strich) vor einer Leuchtkugel |
| „Alle Änderungen“ | ❌ → behoben | im Verlauf standen „{map}“ usw. vor den Punkten (auch schon bei 0.1.1.66 mit „{store}“) |
| Testversion 0.1.1.65 gegen das echte 0.1.1.70 | ✅ | die Download-Seite „Update verfügbar“ erscheint wie bisher mit den Neuerungen von Nova Karte |
| Update auf ghcr.io | ✅ | `versionName=0.1.1.72`, `apps=settings` |

**Nicht getestet:** Querformat dieser Seiten, „Erneut versuchen“ ohne Internet.

### Update 0.1.1.74: Nova Karte nimmt den GPS-Standort (21.11.2026)

Rückmeldung: „Er denkt, wir sind in Grambek, aber wir sind in Mölln.“ Nachgestellt in Android-x86 9 mit einer kleinen
Test-App, die Positionen vorspielt (Android-Testanbieter für „gps“ und „network“, nur im Emulator): GPS Mölln
(53,6281 / 10,6889, ± 8 m), Mobilfunk Grambek (53,5850 / 10,6640, ± 2,5 km). Bilder 100–101.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1768 bestanden (neu: Mobilfunk nach GPS wird ignoriert, GPS ersetzt Mobilfunk, nach 2 Minuten ohne GPS zählt die neuere, alte Position kommt nicht zurück, beste bekannte Position) |
| Start mit GPS Mölln und neuerer Mobilfunk-Position Grambek | ✅ | Karte startet in Mölln (vorher: die neueste, also Grambek) |
| Dreimal Mobilfunk Grambek, dann GPS ein paar Meter weiter | ✅ | blauer Punkt bleibt in der Mölner Altstadt und wandert mit GPS |
| Nur „ungefährer Standort“ erlaubt | ✅ | Dialog „Genauer Standort“ (Genau erlauben / Einstellungen / Später) und Hinweis „Nur ungefährer Standort – hier tippen“ |
| „Genau erlauben“ | ✅ | danach GPS angemeldet, Karte springt nach Mölln |
| „Standort ist aus“ im Emulator, obwohl an | ❌ → behoben | Android 9 im Emulator meldet den Hauptschalter falsch; jetzt reicht eine eingeschaltete Quelle |

**Nicht getestet:** ein echtes Handy mit GPS-Empfang und der Dialog „Genau/Ungefähr“ von Android 12+ (der Emulator hat
Android 9). Bitte nach dem Update draußen ausprobieren: der blaue Punkt sollte nach einigen Sekunden genau stehen.

### Update 0.1.1.75: NovaOS 1.5 – Startbildschirm und alle Apps wie bei Samsung (21.11.2026)

Vorlage: Bilder von Samsung One UI (Startbildschirm mit Wetter-Widget, App-Seiten mit Punkten und „Suchen“). Android-x86 9,
Hochformat 720×1280, frisch gestartet, Standort über die Test-App (Mölln). Bilder 102–107.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1791 bestanden (neu: eigene Reihenfolge, neue Apps hinten, verschieben, Seiten, Reihen, Startbildschirm-Raster mit Widget, Name „1.5“ im Update) |
| Startbildschirm | ✅ | Wetter-Widget „5° ☁ Bedeckt, ↑ 7° ↓ 1°, 📍 Mölln“, Nova Chat/Mail/Karte/Musik daneben, 8 Nova-Apps unten, Suchleiste, Dock |
| „bedeckt“ klein geschrieben | ❌ → behoben | erster Buchstabe groß |
| Hochwischen | ✅ | alle Apps: ⋮, 4 Spalten, 3 Seiten mit Punkten, „Suchen“ mit Mikrofon; zuerst die alten Apps, die neu installierten Nova-Apps hinten |
| Seitwärts wischen | ✅ | Seite 3 mit Nova Store, Nova TV, NovaOS-Einstellungen |
| „nova k“ suchen | ✅ | Nova Kamera, Nova Karte |
| Nova Store mit Testversion 0.1.1.60 gegen das echte 0.1.1.74 | ✅ | Nova Karte „Aktualisieren“ → Ring ums Symbol, „Wird heruntergeladen … 72 %“, „Pause“ – direkt in der Zeile |
| … Prüfung | ✅ (erwartet) | lehnt das echte Update ab, weil die Testversion mit dem Entwicklungsschlüssel signiert ist (auf echten Handys passt der Schlüssel) |
| „Letztes erfolgreiches Update“ | ✅ | großes blaues „1.5“ vor der Leuchtkugel |
| „1.5“ zu blass | ❌ → behoben | die Transparenz des Schattens blieb am Pinsel |
| Update auf ghcr.io | ✅ | `versionName=0.1.1.75`, `release=1.5`, `apps=store,settings` |

**Nicht getestet:** Querformat, ein Handy mit mehr als 6 Reihen, „Alle aktualisieren“ (das echte Update ändert nur
Nova Karte), Wischen auf einem echten Handy (im Emulator kamen schnelle Gesten manchmal zu spät an).

### Update 0.1.1.77: große Updates heißen „NovaOS 1.5“ (21.11.2026)

Rückmeldung (Bild vom Tablet): „Was ist neu“ zeigte „Installiert: NovaOS 0.1.1.75“ statt „NovaOS 1.5“. Regel jetzt: großes
Update (vom Nutzer so angesagt, `@release: 1.5`) = Name, kleine und mittlere = lange Nummer.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1796 bestanden (neu: `@release` in den Neuerungen, großes Update heißt „1.5“, kleines behält die Nummer, Verlauf „NovaOS 1.5 (0.1.1.75)“ mit Build für das Installationsdatum) |
| `update.properties` des Builds | ✅ | `release=1.5` nur mit `@release:`-Zeile |

**Nicht getestet:** im Emulator (die Testumgebung startete währenddessen neu); die Anzeigen nutzen alle dieselbe
Funktion (`Updates.displayName`, `UpdateInfo.label`).

### Update 0.1.1.78: Glas-Design, Nova Wetter, Kalender, großer Player (21.11.2026)

Android-x86 9, Hochformat 720×1280, Standort Mölln über die Test-App. Bilder 108–114.

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1810 bestanden (neu: Vorhersage im Open-Meteo-Format – jetzt, 24 Stunden ab der aktuellen, 7 Tage –, Wochentage, Startbildschirm-Raster mit Widgets, Musik-Zeile, Tablet) |
| Schrift Inter | ✅ | Start-Animation, Einrichtung, Seiten und Widgets in Inter |
| Startbildschirm | ✅ | Glas-Widgets „Mölln 5° Bedeckt H: 7° T: 1°“ und „SAMSTAG 21 – Tippe hier für deine Termine“, Apps darunter |
| Wetter-Widget antippen | ✅ | Nova Wetter: Nachthimmel, „Mölln 5° Bedeckt“, 24 Stunden (Jetzt, 20 … 01), 7 Tage mit Balken. Hinweis: Open-Meteo sperrte die gemeinsame Internet-Adresse der Testumgebung (Tageslimit), die Seite wurde mit der gespeicherten Beispiel-Vorhersage gezeigt |
| Temperaturbalken zu blass | ❌ → behoben | Transparenz der Schiene blieb am Pinsel |
| „Letztes erfolgreiches Update“ | ✅ | „Neu in NovaOS 1.5“ mit allen Punkten |
| Radio SWR3 | ✅ | Glas-Leiste unten; antippen → großer Player in SWR3-Rot, großes Cover, „POP mit Marcus Barsch“, ⏮ ⏸ ⏭ |
| Startbildschirm mit Musik | ✅ | Musik-Widget mit der laufenden Sendung und ⏮ ⏸ ⏭ |
| Pause-Zeichen als oranges Emoji | ❌ → behoben | weiße Symbole wie im Player |
| Update auf ghcr.io | ✅ | `versionName=0.1.1.78`, ohne `release=` (mittleres Update), `apps=weather,music,chat,settings` |

**Nicht getestet:** Nova Chat (im Emulator ist kein Matrix-Konto angemeldet – nur gebaut), Kalender mit echten
Terminen (die Berechtigung kommt erst beim Antippen), Nova Wetter mit frischen Daten (Tageslimit in der Testumgebung),
Tablet-Querformat.

### Update 0.1.1.80: Nova AI – Chat, Modelle, Nova Pro 5 (21.11.2026)

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1816 bestanden (neu: Pro 5 nur mit Abo, Vision 5 noch nicht wählbar, 30 Fragen am Tag, neuer Tag beginnt bei 0, Restfragen, Modelle) |
| Bauen | ✅ | alle neuen Seiten übersetzt und im Paket |
| Update auf ghcr.io | ✅ | `versionName=0.1.1.80`, `apps=nova,store` |

**Nicht getestet:** im Emulator (die Testumgebung startete währenddessen neu, das Test-Handy war weg) – Chat-Ansicht,
Modell-Auswahl, Abo-Seite mit Handy-Sperre und das schnelle Aktualisieren im Nova Store sind nur gebaut, nicht angesehen.

### Update 0.1.1.82: Zoomen, Fotos bearbeiten, Videos kürzen – nur im Nova Store (26.11.2026)

| Test | Ergebnis | Details |
|---|---|---|
| Logik-Tests | ✅ | 1834 bestanden (neu: Zoom-Grenzen auch für Fotos mit Rand, Doppeltippen, Zoom-Anzeige „2,3×“, Kamera-Bildausschnitt, Farbmatrizen für Helligkeit/Kontrast/Sättigung/Wärme/Filter, Dateiname der Kopie, `storeOnly` nur mit Apps) |
| Foto zoomen (Emulator) | ✅ | zwei Finger (simuliert, das Test-Handy hat keinen Touchscreen), verschieben, Doppeltippen rein/raus; verschieben stoppt jetzt am Fotorand statt am schwarzen Rand (Fehler gefunden und behoben) |
| Foto bearbeiten | ✅ | Sepia + rechts drehen → Kopie `nova_test_bearbeitet.jpg` 1200×1600 im Ordner DCIM/NovaOS, Original unverändert, Kopie erscheint in der Galerie; Leiste unten mittig mit gelb markiertem Werkzeug, Knöpfe „Rechts/Links/Spiegeln“ einzeilig (gefunden und behoben) |
| Video zoomen | ✅ | Video im Zoom mit Knöpfen und Zeitleiste |
| Video kürzen | ✅ | 0:03–0:09 ohne Ton → `nova_clip_gekürzt.mp4`: 6,04 s, nur Videospur (ffprobe); erstes Bild ist jetzt vor dem Abspielen sichtbar (vorher schwarz, behoben) |
| Kamera | ⚠️ | Test-Handy hat keine Kamera: „Keine Kamera gefunden“ ohne Absturz; Zoom-Knöpfe und Zoomen mit zwei Fingern **nicht** an einer echten Kamera getestet |
| Nur-Store-Update | ✅ | auf ghcr.io: `versionName=0.1.1.82`, `storeOnly=true`, `apps=camera,gallery` |

Bilder: `docs/screenshots/mobile/115-galerie-foto-zoom.jpg`, `116-foto-bearbeiten-filter.jpg`, `117-video-kuerzen.jpg`,
`118-video-zoom.jpg`.

**Hinweis:** NovaOS 0.1.1.80 kennt `storeOnly` noch nicht – auf so einem Handy kann dieses eine Update noch als
Software-Update angezeigt werden. Über Nova Store → Nova Kamera/Galerie → „Aktualisieren“ läuft es schon jetzt schnell
und ohne Start-Animation; ab 0.1.1.82 erscheinen solche Updates nur noch im Store.

## NovaOS TV (App für Google TV / Android TV)

Getestet am 18.11.2026 mit **Android-x86 9.0-r2** in QEMU (TCG, ohne Netzwerk), Bildschirm
1280×720 bei 213 dpi – das entspricht der Darstellung eines 720p-Fernsehers (960×540 dp).
Bedient wurde ausschließlich mit Tastenereignissen der Fernbedienung (`DPAD_UP/DOWN/LEFT/RIGHT`,
`DPAD_CENTER`, langes OK, `BACK`). Bilder: [docs/screenshots/tv/](screenshots/tv/).

| Test | Ergebnis | Details |
|---|---|---|
| Build ohne Gradle (`tv/build.sh`), 17 Logik-Tests | ✅ | lokal und in GitHub Actions (Release `tv-build-1`) |
| APK-Inhalt (`aapt2 dump badging`) | ✅ | `leanback-launchable-activity`, Banner, `minSdk 24`, `targetSdk 34`, nur `ACCESS_NETWORK_STATE` und `REQUEST_DELETE_PACKAGES`, **keine** Internet-Berechtigung |
| Installation (`pm install`) und Update über die alte Version (gleicher Schlüssel) | ✅ | |
| Startseite: Uhr, Datum, Netzwerkstatus, Favoriten, alle Apps (20), Einstellungen | ✅ | „Nicht verbunden“ korrekt (VM ohne Netzwerk) |
| Fokus mit der Fernbedienung | ✅ | weißer Rahmen + Vergrößerung; Seite scrollt mit |
| „Links/rechts“ am Reihenende | ❌ → behoben | sprang schräg in die nächste Reihe → Reihenenden gesperrt |
| Hinweiszeile unten | ❌ → behoben | lag über der Einstellungsreihe (fehlendes Abschneiden der Scrollfläche) |
| Hinweis-Kachel „noch keine Favoriten“ | ⚠️ → behoben | war anwählbar, ohne Funktion → nicht mehr fokussierbar, Startfokus auf „Apps suchen“ |
| Favoriten: hinzufügen (langes OK → Menü), verschieben, Speicherung | ✅ | nur lokal in `shared_prefs`; bleiben nach Sprachwechsel erhalten |
| App-Menü bei System-Apps | ✅ | ohne „Deinstallieren“ (richtig) |
| Suche (auch mit Umlaut-/Groß-Klein-Toleranz), Treffer starten, „Zurück“ | ✅ | „cal“ → 3 Apps; App startet; Zurück → Suche → Startseite |
| Fokussierte Kachel in der Suche | ❌ → behoben | links abgeschnitten (`clipToPadding`) |
| Einstellungs-Kacheln (Ton, Bild & Anzeige) | ✅ | öffnen die Android-Einstellungen |
| Über-Seite | ✅ | Version, Gerät, Datenschutz, Favoriten zurücksetzen |
| **Deutsch** (Systemsprache de-DE) | ✅ | 24-h-Uhr, „Mittwoch, 18. November“, alle Texte deutsch; „Bluetooth & Fernbedienungen“ war abgeschnitten → zweizeilig; „Apps · 20 Apps“ → „Alle Apps · 20“ |

Hinweise zur Testumgebung (keine App-Fehler): Die QEMU-Bildausgabe von Android-x86 vertauscht
Rot und Blau – mit Androids eigenem Bildspeicher (`screencap`) überprüft, die Screenshots sind
farbkorrigiert (`tests/qemu/fix_android_colors.py`). Unter reiner CPU-Emulation setzte sich die
VM mehrfach zurück, während Android Apps übersetzte (dex2oat); umgangen mit dem Filter
`extract`. Android-x86 ist kein Google TV: Die Anzeige von NovaOS TV in der App-Reihe eines
Fernsehers (Leanback-Banner) konnte daher nur über die APK-Daten geprüft werden.

**Nicht getestet:** echter Fernseher (Metz/Google TV), echte Fernbedienung, Spracheingabe,
„Deinstallieren“ einer Nutzer-App, 4K-Fernseher.

### Fernsehen und Nova Filme (19.11.2026)

Getestet mit **Android-x86 9.0-r2** in QEMU (TCG, CPU-Modell Nehalem, VESA-Grafik
1280×720 bei 213 dpi, Sprache Deutsch), diesmal **mit Internet**: Die VM ging über den
HTTPS-Proxy der Testumgebung ins Netz (`tests/qemu/android_vm.sh`). Der Ausgang dieses
Proxys liegt in den **USA** – deshalb waren nur die Sender ohne Geoblocking empfangbar.
Bedient wurde wieder nur mit Tasten der Fernbedienung (`DPAD_*`, OK, Zurück, Zifferntasten).
Bilder: [docs/screenshots/tv/](screenshots/tv/) (07–13).

| Test | Ergebnis | Details |
|---|---|---|
| Build mit Media3 ExoPlayer (Prüfsummen aus `tv/libs.lock`), 238 Logik-Tests | ✅ | APK 2,4 MB; Senderliste, Feed-Parser, Titel und Reihen werden mit getestet |
| Startseite: Reihen „Fernsehen · 30 Sender im Internet“ und „Nova Filme“ | ✅ | farbige Sender-Kacheln mit Nummer, Kategorie-Kacheln |
| Erster Tastendruck landete auf der leeren Favoriten-Reihe | ❌ → behoben | Reihen nehmen den Fokus nicht mehr selbst an |
| **Das Erste** im Player (HLS, 16:9 eingepasst) | ✅ | Livestream lief; ebenso **NDR Hamburg** und **tagesschau24** |
| Wiedergabe brach ab: VM ohne Soundkarte (`AUDIO_TRACK_INIT_FAILED`) | ⚠️ → abgefangen | Bild läuft weiter, Hinweis „ohne Ton“; auf dem Fernseher gibt es eine Tonausgabe |
| ▲/▼ zappen, Einblendung Nummer + Name | ✅ | |
| Zifferntasten (1 2 → NDR Hamburg, 6 → tagesschau24) | ✅ | Wartezeit auf die 2. Ziffer auf 2,5 s verlängert |
| Senderliste (OK): springt zum laufenden Sender, Auswahl startet Sender | ✅ | Nummern jetzt bündig |
| **ZDF** (Geoblocking, aus den USA) | ✅ | Meldung „… nur innerhalb Deutschlands“; vorher blieb das letzte Bild des alten Senders stehen → jetzt schwarz |
| **Nova Filme**: echte Feeds der 16 Kanäle | ✅ | 94 Videos mit Vorschaubildern: 28 Spielfilme, 6 Serienfolgen, 30 Dokus, 30 Kinder |
| Kategorie-Kachel öffnet die richtige Reihe, „Mehr von … auf YouTube“ am Reihenende | ✅ | |
| Video mit OK öffnen | ✅ (Ersatzweg) | keine YouTube-App in Android-x86 → Link öffnete sich in Chrome, wie vorgesehen |
| „Sender vom Fernseher“ ohne TV-Empfänger | ❌ → behoben | zeigte „ohne Erlaubnis …“, weil der Google-Viewer TV-Links annahm → strengere Erkennung, jetzt „Dieses Gerät hat keinen TV-Empfang“ |

Hinweise zur Testumgebung: Mit dem bochs-DRM-Grafiktreiber stürzte der Kernel der VM beim
Booten ab (`ttm_bo_vm_open`), mit VESA-Grafik lief sie stabil. Nach einem Neustart der
Android-Oberfläche (Sprachwechsel) musste das WLAN-HAL neu gestartet werden.

#### Nova Filme direkt in der App (eingebetteter YouTube-Player)

| Test | Ergebnis | Details |
|---|---|---|
| Einbett-Erlaubnis aller Kanäle (YouTube oEmbed) | ✅ | Die Biene Maja, ZDFtivi, KiKA verbieten das Einbetten → entfernt; einzelne Videos werden beim Laden aussortiert (Serien: 6 → 4) |
| Player-Seite mit aktuellem Chromium (Playwright), Referer | ✅ | YouTube erhält `Referer: https://org.novaos.tv/` (App-Kennung nach den YouTube-Regeln) |
| Weltweit freigegebenes Testvideo: Autostart, ▶ +30 s, OK = Pause, OK = weiter | ✅ | Zustände 1 → 1 (0:36) → 2 → 1, Zeit läuft weiter |
| Netzkino-, Moviedome-, Terra-X-, funk-Videos aus den USA | ⚠️ erwartet | YouTube: „Der Uploader stellt dieses Video in deinem Land nicht zur Verfügung“ (Fehler 150) – Freigabe nur für Deutschland; die App zeigt dazu einen Hinweis und bietet die YouTube-App an |
| Android-x86: Player öffnet sich mit NovaOS-Infoleiste **unter** dem Bild, Titel und Länge (43:40) | ✅ | Bild: `14-nova-filme-player.png` |
| Android-x86: Wiedergabe | ❌ (Testumgebung) | WebView 75 (2019) scheitert am TLS-Proxy der Testumgebung (`SSL error … net_error -213`); auf Google TV ist WebView aktuell |

#### NovaOS TV Web (Browser-Version für Samsung, Philips, LG)

Getestet mit Chromium (Playwright, 1280×720, Sprache Deutsch, Tastatur wie Fernbedienung),
Seite unter einer GitHub-Pages-Adresse simuliert. Bilder: `15-web-startseite.png`, `16-web-filme.jpg`.

| Test | Ergebnis | Details |
|---|---|---|
| Daten bauen (`tv/web/build.sh`) | ✅ | 30 Sender, 92 Videos (2 nicht einbettbar aussortiert), gleiche Logik wie die App |
| Startseite, Pfeiltasten-Navigation | ✅ | Fokus startet auf „Das Erste“, Rechts/Runter wie erwartet |
| Das Erste im Browser (hls.js) | ✅ | lief (Zeit lief weiter); die Sender-Streams erlauben das Abspielen auf fremden Seiten (CORS `*`) |
| Zifferntasten 1 2 → NDR Hamburg, OK → Senderliste, Zurück → Startseite mit Fokus auf dem Sender | ✅ | |
| Film (weltweit freigegebenes Testvideo): Start, 2 × ▶ = +20 s, OK = Pause, Zurück | ✅ | 2 × ▶ zählte zuerst nur einmal → behoben (auch in der Android-App) |
| Samsung-/Philips-/LG-Fernseher selbst | – | nicht getestet (kein Gerät); sehr alte TV-Browser können evtl. kein HLS |

**Nicht getestet:** echter Fernseher mit Tuner (Senderliste aus Antenne/Kabel/Satellit,
Erlaubnisdialog, Umschalten ins Live-TV der Hersteller-App), Nova-Filme-Wiedergabe aus Deutschland,
Sender mit Geoblocking aus Deutschland (ZDF, 3sat, ARTE, phoenix, ONE, MDR, rbb, SWR, Radio
Bremen, WELT – aus den USA alle mit HTTP 403, also erreichbar, aber gesperrt), Ton.

### Nicht getestet

* echte Hardware (WLAN-Chips, Bluetooth, Drucker, Webcam, Mehrere Monitore, Akku) – nur
  Treiber/Dienste sind enthalten
* Installation im BIOS-Modus und mit LUKS-Verschlüsselung, Dual-Boot neben Windows
* Secure Boot des **installierten** Systems
* Barrierefreiheit, Ton-Ausgabe (VM ohne Audio-Backend)
