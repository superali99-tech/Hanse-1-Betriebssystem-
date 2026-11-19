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
