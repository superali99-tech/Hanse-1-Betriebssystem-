#!/usr/bin/env python3
"""Generate the offline help pages of Nova Help (de + en) from this file.

The content lives here as structured data so both languages stay in sync.
Run: python3 src/data/help/generate_help.py   (writes de/*.html, en/*.html)
"""
import html
import os

HERE = os.path.dirname(os.path.abspath(__file__))

CSS = """
body { font-family: Inter, sans-serif; font-size: 11pt; line-height: 1.5; }
h1 { font-size: 22pt; color: #7C5CFF; margin-bottom: 4px; }
h2 { font-size: 14pt; margin-top: 18px; }
code, kbd { font-family: Hack, monospace; background: rgba(124,92,255,0.12); padding: 1px 4px; }
.tip { border-left: 4px solid #7C5CFF; padding: 6px 10px; background: rgba(124,92,255,0.08); }
"""

# (file, {lang: (title, [(heading, html_body), ...])})
PAGES = [
    ("01-erste-schritte", {
        "de": ("Erste Schritte", [
            ("Willkommen bei NovaOS", "<p>NovaOS ist ein modernes Betriebssystem auf Basis von <b>Debian 13 „trixie“</b> und dem Desktop <b>KDE Plasma 6</b>. Es ist kostenlos, quelloffen und sammelt keine Daten.</p>"),
            ("Der Desktop", "<ul><li>Unten befindet sich die <b>schwebende Leiste</b> mit Startmenü, angehefteten Apps, Infobereich (WLAN, Bluetooth, Lautstärke, Akku), Uhr und Kalender.</li><li>Rechts auf dem Desktop zeigen <b>Widgets</b> Kalender, Prozessor- und Speicherauslastung und das Wetter (Ort per Rechtsklick → <i>Wetterbericht einrichten</i> festlegen).</li><li>Fenster lassen sich frei verschieben, an Bildschirmränder andocken, minimieren, maximieren und schließen.</li></ul>"),
            ("Apps starten", "<p>Klicken Sie auf das NovaOS-Logo unten oder drücken Sie die <kbd>Meta</kbd>-Taste (Windows-Taste) und tippen Sie den Namen der App. Mit Rechtsklick → <i>An Fensterleiste anheften</i> legen Sie Apps dauerhaft in die Leiste.</p>"),
            ("Live-System und Installation", "<p>Im Live-System können Sie NovaOS gefahrlos ausprobieren; Änderungen gehen beim Ausschalten verloren. Über <b>„NovaOS installieren“</b> auf dem Desktop starten Sie den Installationsassistenten. Datenträger werden erst nach Ihrer ausdrücklichen Bestätigung verändert.</p>"),
        ]),
        "en": ("Getting started", [
            ("Welcome to NovaOS", "<p>NovaOS is a modern operating system based on <b>Debian 13 “trixie”</b> and the <b>KDE Plasma 6</b> desktop. It is free, open source and collects no data.</p>"),
            ("The desktop", "<ul><li>The <b>floating panel</b> at the bottom contains the start menu, pinned apps, the system tray (Wi-Fi, Bluetooth, volume, battery), clock and calendar.</li><li><b>Widgets</b> on the right show a calendar, CPU and memory usage and the weather (right-click → <i>Configure</i> to set your location).</li><li>Windows can be moved, snapped to screen edges, minimized, maximized and closed.</li></ul>"),
            ("Starting apps", "<p>Click the NovaOS logo or press the <kbd>Meta</kbd> (Windows) key and type the app name. Right-click → <i>Pin to Task Manager</i> keeps an app in the panel.</p>"),
            ("Live system and installation", "<p>The live system lets you try NovaOS safely; changes are lost on shutdown. <b>“Install NovaOS”</b> on the desktop starts the installer. Disks are only modified after your explicit confirmation.</p>"),
        ]),
    }),
    ("02-suche-und-tastenkuerzel", {
        "de": ("Suche und Tastenkürzel", [
            ("Zentrale Suche", "<p>Drücken Sie <kbd>Alt+Leertaste</kbd> oder beginnen Sie einfach auf dem leeren Desktop zu tippen. Die Suche findet <b>Apps, Dateien, Einstellungen</b>, rechnet (<code>12*7</code>) und rechnet Einheiten um (<code>10 km in mi</code>).</p>"),
            ("Wichtige Tastenkürzel", "<ul><li><kbd>Meta</kbd> – Startmenü</li><li><kbd>Alt+Leertaste</kbd> – zentrale Suche</li><li><kbd>Meta+W</kbd> – Übersicht aller Fenster und virtuellen Desktops</li><li><kbd>Meta+Strg+←/→</kbd> – virtuellen Desktop wechseln</li><li><kbd>Meta+←/→</kbd> – Fenster an linke/rechte Hälfte andocken</li><li><kbd>Meta+Bild↑</kbd> – Fenster maximieren</li><li><kbd>Meta+L</kbd> – Bildschirm sperren</li><li><kbd>Strg+Esc</kbd> – Aufgabenverwaltung</li><li><kbd>Druck</kbd> – Bildschirmfoto</li><li><kbd>Meta+V</kbd> – Zwischenablage-Verlauf</li></ul>"),
        ]),
        "en": ("Search and shortcuts", [
            ("Central search", "<p>Press <kbd>Alt+Space</kbd> or just start typing on the empty desktop. Search finds <b>apps, files and settings</b>, calculates (<code>12*7</code>) and converts units (<code>10 km in mi</code>).</p>"),
            ("Important shortcuts", "<ul><li><kbd>Meta</kbd> – start menu</li><li><kbd>Alt+Space</kbd> – central search</li><li><kbd>Meta+W</kbd> – overview of windows and virtual desktops</li><li><kbd>Meta+Ctrl+←/→</kbd> – switch virtual desktop</li><li><kbd>Meta+←/→</kbd> – snap window to left/right half</li><li><kbd>Meta+PgUp</kbd> – maximize</li><li><kbd>Meta+L</kbd> – lock screen</li><li><kbd>Ctrl+Esc</kbd> – task manager</li><li><kbd>Print</kbd> – screenshot</li><li><kbd>Meta+V</kbd> – clipboard history</li></ul>"),
        ]),
    }),
    ("03-apps-installieren", {
        "de": ("Apps installieren (Nova Store)", [
            ("Nova Store", "<p>Der Nova Store zeigt tausende Apps von <b>Flathub</b>. Suchen Sie eine App, öffnen Sie die Detailseite und klicken Sie auf <b>Installieren</b>. Der Fortschritt wird in Prozent angezeigt; danach erscheint die App im Startmenü.</p>"),
            ("Berechtigungen", "<p>Flatpak-Apps laufen in einer <b>Sandbox</b>. Vor der Installation zeigt der Store, worauf eine App zugreifen darf (Netzwerk, Ordner, Geräte …). Bei weitreichenden Rechten fragt der Store nach. Berechtigungen können später unter <i>Nova Settings → Anwendungen → Flatpak-Berechtigungen</i> geändert werden.</p>"),
            ("Aktualisieren und Entfernen", "<p>Unter <b>Installiert</b> können Apps geöffnet oder deinstalliert werden, unter <b>Updates</b> aktualisiert. Persönliche Daten einer App bleiben in <code>~/.var/app</code> erhalten.</p>"),
            ("Bewertungen", "<p>Bewertungen stammen vom <i>Open Desktop Ratings Service</i> (odrs.gnome.org) und können in den Store-Einstellungen abgeschaltet werden.</p>"),
            ("Kommandozeile", "<p>Fortgeschrittene können auch <code>flatpak install flathub org.gimp.GIMP</code> oder <code>sudo apt install paketname</code> verwenden.</p>"),
        ]),
        "en": ("Installing apps (Nova Store)", [
            ("Nova Store", "<p>Nova Store lists thousands of apps from <b>Flathub</b>. Search for an app, open its page and click <b>Install</b>. Progress is shown in percent; afterwards the app appears in the start menu.</p>"),
            ("Permissions", "<p>Flatpak apps run in a <b>sandbox</b>. Before installing, the store shows what an app may access (network, folders, devices …) and asks for confirmation for extended permissions. Change them later in <i>Nova Settings → Applications → Flatpak Permissions</i>.</p>"),
            ("Updating and removing", "<p><b>Installed</b> lets you open or uninstall apps, <b>Updates</b> updates them. Personal app data stays in <code>~/.var/app</code>.</p>"),
            ("Ratings", "<p>Ratings come from the <i>Open Desktop Ratings Service</i> (odrs.gnome.org) and can be disabled in the store settings.</p>"),
            ("Command line", "<p>Advanced users can use <code>flatpak install flathub org.gimp.GIMP</code> or <code>sudo apt install package</code>.</p>"),
        ]),
    }),
    ("04-updates-und-sicherheit", {
        "de": ("Updates und Sicherheit", [
            ("Nova Updates", "<p><b>Nova Updates</b> zeigt System-Updates (Debian-Pakete, Kernel, Treiber) und App-Updates (Flatpak) gemeinsam an. <i>Alle Updates installieren</i> fragt einmal nach Ihrem Passwort.</p>"),
            ("Automatische Sicherheitsupdates", "<p>Sicherheitsupdates werden täglich automatisch im Hintergrund installiert (unattended-upgrades). Das lässt sich in Nova Updates abschalten. Sie erhalten eine Benachrichtigung, wenn weitere Updates bereitstehen.</p>"),
            ("Firewall", "<p>Die Firewall (ufw) ist aktiv: eingehende Verbindungen werden blockiert, ausgehende erlaubt. Einstellungen: <i>Nova Settings → Netzwerk → Firewall</i>.</p>"),
            ("Verschlüsselung", "<p>Bei der Installation kann die Festplatte vollständig verschlüsselt werden (LUKS). Das Passwort wird dann bei jedem Start abgefragt.</p>"),
            ("Secure Boot", "<p>NovaOS verwendet den von Microsoft signierten <i>shim</i> sowie den signierten GRUB und Kernel von Debian. Auf den meisten PCs startet NovaOS daher auch mit aktiviertem Secure Boot. Eigene Kernelmodule (z. B. DKMS-Treiber) müssen dann per MOK signiert werden.</p>"),
        ]),
        "en": ("Updates and security", [
            ("Nova Updates", "<p><b>Nova Updates</b> shows system updates (Debian packages, kernel, drivers) and app updates (Flatpak) together. <i>Install all updates</i> asks for your password once.</p>"),
            ("Automatic security updates", "<p>Security updates are installed automatically every day (unattended-upgrades); this can be disabled in Nova Updates. You are notified when other updates are available.</p>"),
            ("Firewall", "<p>The firewall (ufw) is enabled: incoming connections are blocked, outgoing allowed. Settings: <i>Nova Settings → Network → Firewall</i>.</p>"),
            ("Encryption", "<p>The installer can fully encrypt the disk (LUKS). The password is then asked at every boot.</p>"),
            ("Secure Boot", "<p>NovaOS uses the Microsoft-signed <i>shim</i> and Debian's signed GRUB and kernel, so it boots on most PCs with Secure Boot enabled. Custom kernel modules (e.g. DKMS drivers) must then be signed with a MOK.</p>"),
        ]),
    }),
    ("05-internet-und-geraete", {
        "de": ("Internet, WLAN und Geräte", [
            ("WLAN und Ethernet", "<p>Klicken Sie im Infobereich auf das Netzwerksymbol, wählen Sie ein WLAN und geben Sie das Passwort ein. Kabelverbindungen werden automatisch hergestellt.</p>"),
            ("Bluetooth", "<p>Bluetooth-Symbol im Infobereich → <i>Gerät hinzufügen</i>. Kopfhörer, Mäuse und Tastaturen werden gekoppelt und künftig automatisch verbunden.</p>"),
            ("Ton und Mikrofon", "<p>Lautstärke-Symbol im Infobereich: Lautstärke, Ausgabegerät und Mikrofon wählen. NovaOS verwendet PipeWire.</p>"),
            ("Drucker", "<p>Die meisten Netzwerk- und USB-Drucker werden automatisch erkannt (CUPS, IPP Everywhere). Manuell: <i>Nova Settings → Drucker</i>.</p>"),
            ("Bildschirme", "<p>Zweiten Monitor anschließen – Anordnung und Auflösung unter <i>Nova Settings → Anzeige und Monitor</i>. <kbd>Meta+P</kbd> wechselt schnell zwischen Modi.</p>"),
            ("USB-Sticks und Festplatten", "<p>Angeschlossene Laufwerke erscheinen im Infobereich und in <b>Nova Files</b> links unter <i>Geräte</i>. Vor dem Abziehen bitte <i>Sicher entfernen</i> wählen.</p>"),
            ("Energie", "<p>Akku-Symbol: Ladezustand und Energieprofil (Leistung, Ausgeglichen, Energiesparen). Bereitschaft, Neustart und Herunterfahren finden Sie im Startmenü unten rechts.</p>"),
        ]),
        "en": ("Internet, Wi-Fi and devices", [
            ("Wi-Fi and Ethernet", "<p>Click the network icon in the system tray, choose a network and enter the password. Wired connections are set up automatically.</p>"),
            ("Bluetooth", "<p>Bluetooth icon → <i>Add device</i>. Headphones, mice and keyboards are paired and reconnected automatically.</p>"),
            ("Sound and microphone", "<p>Volume icon: volume, output device and microphone. NovaOS uses PipeWire.</p>"),
            ("Printers", "<p>Most network and USB printers are detected automatically (CUPS, IPP Everywhere). Manually: <i>Nova Settings → Printers</i>.</p>"),
            ("Displays", "<p>Connect a second monitor and arrange it in <i>Nova Settings → Display and Monitor</i>. <kbd>Meta+P</kbd> switches modes.</p>"),
            ("USB drives", "<p>Drives appear in the tray and in <b>Nova Files</b> under <i>Devices</i>. Use <i>Safely remove</i> before unplugging.</p>"),
            ("Power", "<p>Battery icon: charge level and power profile. Sleep, restart and shut down are in the start menu.</p>"),
        ]),
    }),
    ("06-dateien", {
        "de": ("Dateien und Netzlaufwerke", [
            ("Nova Files", "<p><b>Nova Files</b> ist der Dateimanager: Kopieren (<kbd>Strg+C</kbd>), Einfügen (<kbd>Strg+V</kbd>), Ausschneiden (<kbd>Strg+X</kbd>), Umbenennen (<kbd>F2</kbd>), Löschen in den Papierkorb (<kbd>Entf</kbd>). Dateien können per Drag-and-drop verschoben werden.</p>"),
            ("Netzlaufwerke", "<p>In die Adressleiste eintippen: <code>smb://server/freigabe</code> (Windows/NAS), <code>sftp://benutzer@server</code> oder <code>nfs://server/pfad</code>. Über <i>Netzwerk</i> in der Seitenleiste werden Freigaben im lokalen Netz angezeigt.</p>"),
            ("Datensicherung", "<p><b>Timeshift</b> erstellt Systemschnappschüsse, mit denen das System nach einem fehlerhaften Update zurückgesetzt werden kann. Für persönliche Dateien empfiehlt sich zusätzlich eine Sicherung auf einem externen Laufwerk.</p>"),
        ]),
        "en": ("Files and network drives", [
            ("Nova Files", "<p><b>Nova Files</b> is the file manager: copy (<kbd>Ctrl+C</kbd>), paste (<kbd>Ctrl+V</kbd>), cut (<kbd>Ctrl+X</kbd>), rename (<kbd>F2</kbd>), move to trash (<kbd>Del</kbd>), drag and drop.</p>"),
            ("Network drives", "<p>Type into the location bar: <code>smb://server/share</code>, <code>sftp://user@server</code> or <code>nfs://server/path</code>. <i>Network</i> in the sidebar lists local shares.</p>"),
            ("Backups", "<p><b>Timeshift</b> creates system snapshots to roll back after a broken update. Back up personal files to an external drive as well.</p>"),
        ]),
    }),
    ("07-nova-apps", {
        "de": ("Die Nova-Apps", [
            ("Übersicht", "<ul><li><b>Nova Browser</b> – Webbrowser (Firefox ESR) mit Tracking-Schutz, Passwortverwaltung, privatem Modus und PDF-Anzeige</li><li><b>Nova Files</b> – Dateien verwalten</li><li><b>Nova Store</b> – Apps installieren</li><li><b>Nova Updates</b> – System- und App-Updates</li><li><b>Nova Settings</b> – alle Einstellungen</li><li><b>Nova Terminal</b> – Linux-Terminal</li><li><b>Nova Notes</b> – Notizen (Markdown, automatisch gespeichert in <i>Dokumente/Notizen</i>)</li><li><b>Nova Calendar</b> – lokale Termine mit Erinnerungen (iCalendar-Import/-Export)</li><li><b>Nova Calculator</b> – Taschenrechner</li><li><b>Nova Media</b> – Videos und Musik</li><li><b>Nova Photos</b> – Bilder ansehen, zuschneiden, drehen, Größe ändern</li><li><b>Nova Task Manager</b> – Prozesse, Leistung, Fehlerprotokolle</li><li><b>Nova Help</b> – diese Hilfe</li></ul>"),
        ]),
        "en": ("The Nova apps", [
            ("Overview", "<ul><li><b>Nova Browser</b> – web browser (Firefox ESR) with tracking protection, password manager, private mode and PDF viewer</li><li><b>Nova Files</b> – file manager</li><li><b>Nova Store</b> – install apps</li><li><b>Nova Updates</b> – system and app updates</li><li><b>Nova Settings</b> – all settings</li><li><b>Nova Terminal</b> – Linux terminal</li><li><b>Nova Notes</b> – notes (Markdown, saved automatically in <i>Documents/Notes</i>)</li><li><b>Nova Calendar</b> – local appointments with reminders (iCalendar import/export)</li><li><b>Nova Calculator</b> – calculator</li><li><b>Nova Media</b> – video and music</li><li><b>Nova Photos</b> – view, crop, rotate and resize pictures</li><li><b>Nova Task Manager</b> – processes, performance, error logs</li><li><b>Nova Help</b> – this help</li></ul>"),
        ]),
    }),
    ("08-benutzer-und-datenschutz", {
        "de": ("Benutzer und Datenschutz", [
            ("Benutzerkonten", "<p>Weitere Benutzer, Passwörter und Administratorrechte verwalten Sie unter <i>Nova Settings → Benutzer</i>. Passwörter werden ausschließlich als sichere Hashes in <code>/etc/shadow</code> gespeichert.</p>"),
            ("Bildschirmsperre", "<p><kbd>Meta+L</kbd> sperrt den Bildschirm; nach 10 Minuten Inaktivität geschieht das automatisch.</p>"),
            ("Gastzugang", "<p>Für Gäste empfiehlt sich ein eigenes Standardkonto ohne Administratorrechte (<i>Nova Settings → Benutzer → Neuen Benutzer hinzufügen</i>).</p>"),
            ("Datenschutz", "<p>NovaOS enthält keine Telemetrie. Netzwerkverbindungen entstehen nur durch Ihre Nutzung oder durch Updates (Debian, Flathub). Der Nova Store lädt Bewertungen von odrs.gnome.org (abschaltbar).</p>"),
        ]),
        "en": ("Users and privacy", [
            ("User accounts", "<p>Manage users, passwords and administrator rights in <i>Nova Settings → Users</i>. Passwords are only stored as secure hashes in <code>/etc/shadow</code>.</p>"),
            ("Screen lock", "<p><kbd>Meta+L</kbd> locks the screen; it locks automatically after 10 minutes.</p>"),
            ("Guests", "<p>Create a standard account without administrator rights for guests (<i>Nova Settings → Users → Add New User</i>).</p>"),
            ("Privacy", "<p>NovaOS contains no telemetry. Network connections only happen through your usage or updates (Debian, Flathub). Nova Store fetches ratings from odrs.gnome.org (can be disabled).</p>"),
        ]),
    }),
    ("09-problemloesung", {
        "de": ("Problemlösung", [
            ("Das System reagiert nicht", "<p><kbd>Strg+Esc</kbd> öffnet die Aufgabenverwaltung; hängende Programme können dort beendet werden.</p>"),
            ("Nach einem Update startet etwas nicht", "<p>Im Startmenü des Rechners (GRUB) unter <i>Erweiterte Optionen</i> einen älteren Kernel wählen oder mit <b>Timeshift</b> einen Schnappschuss wiederherstellen.</p>"),
            ("Grafikprobleme beim Start", "<p>Im Startmenü des Live-Mediums <i>sicherer Grafikmodus (nomodeset)</i> wählen.</p>"),
            ("Fehlerprotokolle", "<p>Nova Task Manager → <i>Fehlerprotokolle</i> zeigt Fehler des aktuellen Starts. Im Terminal: <code>journalctl -b -p err</code>.</p>"),
        ]),
        "en": ("Troubleshooting", [
            ("The system does not respond", "<p><kbd>Ctrl+Esc</kbd> opens the task manager to end hanging programs.</p>"),
            ("Something fails after an update", "<p>Choose an older kernel in the boot menu under <i>Advanced options</i> or restore a <b>Timeshift</b> snapshot.</p>"),
            ("Graphics problems at boot", "<p>Choose <i>safe graphics mode (nomodeset)</i> in the live medium boot menu.</p>"),
            ("Error logs", "<p>Nova Task Manager → <i>Error logs</i> shows errors of the current boot. In a terminal: <code>journalctl -b -p err</code>.</p>"),
        ]),
    }),
]


def render(title: str, sections) -> str:
    body = f"<h1>{html.escape(title)}</h1>\n"
    for heading, content in sections:
        body += f"<h2>{html.escape(heading)}</h2>\n{content}\n"
    return (f"<!DOCTYPE html>\n<html><head><meta charset='utf-8'><title>{html.escape(title)}</title>"
            f"<style>{CSS}</style></head><body>\n{body}</body></html>\n")


def main() -> None:
    for lang in ("de", "en"):
        os.makedirs(os.path.join(HERE, lang), exist_ok=True)
    for name, langs in PAGES:
        for lang, (title, sections) in langs.items():
            with open(os.path.join(HERE, lang, name + ".html"), "w", encoding="utf-8") as fh:
                fh.write(render(title, sections))
    print("help pages written")


if __name__ == "__main__":
    main()
