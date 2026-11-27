"""Content of the NovaOS website: updates (newest first), guides and the start page.

Updates: one entry per published PC update. The newest one may have the version "auto":
build.py fills in the build number of the packages (0.1.1.<commits>) - when the next update
is written, give it its fixed number.
"""

ONLINE = "https://superali99-tech.github.io/Hanse-1-Betriebssystem-/"
GITHUB = "https://github.com/superali99-tech/Hanse-1-Betriebssystem-"
RELEASES = GITHUB + "/releases"

# icon keys -> files copied by build.py (Nova app icons of the repository)
ICONS = {
    "novaos": "packages/novaos-desktop/root/usr/share/icons/hicolor/scalable/apps/novaos.svg",
    "disk": "src/data/icons/nova-disk.svg",
    "store": "src/data/icons/hicolor/scalable/apps/nova-store.svg",
    "browser": "src/data/icons/hicolor/scalable/apps/nova-browser.svg",
    "updates": "src/data/icons/hicolor/scalable/apps/nova-updates.svg",
    "settings": "src/data/icons/hicolor/scalable/apps/nova-settings.svg",
    "welcome": "src/data/icons/hicolor/scalable/apps/nova-welcome.svg",
    "help": "src/data/icons/hicolor/scalable/apps/nova-help.svg",
    "install": "src/data/icons/hicolor/scalable/apps/novaos-install.svg",
    "files": "src/data/icons/hicolor/scalable/apps/nova-files.svg",
    "about": "src/data/icons/hicolor/scalable/apps/nova-about.svg",
    "claude": "src/data/icons/novaos-webapp-claude.svg",
}

# screenshots -> copied by build.py
SHOTS = {
    "card": "website/img/novaos2-card.jpg",
    "store": "docs/screenshots/nova2/04-nova-store-neu.png",
    "disk": "docs/screenshots/nova2/06-nova-disk-speicher-analysieren.png",
    "rescue": "docs/screenshots/nova2/08-nova-disk-dateien-retten.png",
    "dock": "docs/screenshots/nova2/09-runde-taskleiste-vorschau.png",
    "get2": "docs/screenshots/nova2/01-software-update-novaos2-holen.png",
    "updates": "docs/screenshots/glass/05-nova-updates-aktuell.jpg",
    "wifi": "docs/screenshots/glass/03-wlan-verbinden.jpg",
    "webapps": "docs/screenshots/glass/11-store-web-apps.jpg",
}

INSTALL_UPDATE = [
    "Öffne <b>Systemeinstellungen → System → Software-Update</b> (oder die App <b>Nova Updates</b>).",
    "Tippe auf <b>Nach Updates suchen</b>.",
    "Tippe auf <b>Alle Updates installieren</b>.",
    "Wenn NovaOS fragt: <b>Jetzt neu starten</b>.",
]

UPDATES = [
    {
        "version": "auto",
        "date": "27.11.2026",
        "title": "Die NovaOS-Website ist da",
        "tagline": "Neuigkeiten, Update-Details und Anleitungen – direkt im Nova Browser.",
        "tags": ["Neue Funktion", "Nova Browser"],
        "highlights": [
            ("browser", "Website im Nova Browser", "Die NovaOS-Website ist die Startseite des Nova Browsers und funktioniert auch ohne Internet."),
            ("updates", "Alle Details zu jedem Update", "In Software-Update führt „Alle Details ansehen“ direkt zur Seite des Updates – mit allem, was neu ist."),
            ("help", "Anleitungen", "Schritt für Schritt: NovaOS installieren, aktualisieren, NovaOS 2 holen, WLAN, Dateien retten und mehr."),
        ],
        "changes": [
            "Neue NovaOS-Website mit Startseite, allen Updates und Anleitungen",
            "Nova Browser: NovaOS-Website als Startseite und Lesezeichen „NovaOS“ in der Lesezeichenleiste",
            "Software-Update: „Alle Details ansehen“ unter „Neu in NovaOS“",
            "Die Website passt sich an Hell und Dunkel an und läuft auf PC und Handy",
        ],
    },
    {
        "version": "0.1.1.100",
        "date": "27.11.2026",
        "title": "Runde Taskleiste – jetzt sicher eingeschaltet",
        "tagline": "Auf manchen PCs blieb die alte, eckige Leiste. Jetzt nicht mehr.",
        "tags": ["Stabilität", "Design"],
        "highlights": [
            ("settings", "Schaltet sich sicher ein", "Das runde NovaOS-Design wird beim Anmelden gesetzt und danach geprüft – klappt ein Weg nicht, nimmt NovaOS den nächsten."),
            ("novaos", "Deine Wahl bleibt", "Hast du selbst ein anderes Design gewählt, bleibt es so."),
        ],
        "changes": [
            "Runde Taskleiste wird auch eingeschaltet, wenn vorher das KDE-Standarddesign eingestellt war",
            "Das Ergebnis wird geprüft, mit zwei Ersatzwegen",
        ],
    },
    {
        "version": "0.1.1.99",
        "date": "27.11.2026",
        "title": "Runde Taskleiste und neue NovaOS-2-Karte",
        "tagline": "Ein schwebendes Glas-Dock mit runden Ecken – und eine Begrüßung, die Spaß macht.",
        "tags": ["Design", "Neue Funktion"],
        "image": "dock",
        "highlights": [
            ("novaos", "Runde Taskleiste", "Große runde Ecken, weicher Schatten, Glas. Hinter den Apps runde Pillen – die aktive App leuchtet in Lila."),
            ("welcome", "Neue NovaOS-2-Karte", "Randlos, rund, mit dem Nebula-Bild und schnellen Animationen. Erscheint einmal nach dem Update."),
        ],
        "changes": [
            "Eigenes Plasma-Design „NovaOS“ für die Taskleiste",
            "Dock etwas höher, Symbole luftiger",
            "NovaOS-2-Willkommenskarte komplett neu, Animationen unter einer Sekunde",
        ],
    },
    {
        "version": "0.1.1.98",
        "date": "27.11.2026",
        "title": "NovaOS 2 „Nebula“",
        "tagline": "Das größte Update bisher – kostenlos, deine Dateien und Apps bleiben.",
        "tags": ["NovaOS 2", "Neue Funktion", "Design"],
        "image": "card",
        "highlights": [
            ("disk", "Nova Disk", "Sehen, was die Festplatte füllt, Laufwerke prüfen und gelöschte Fotos, Videos und Dokumente retten – auch von USB-Sticks und SD-Karten."),
            ("store", "Der neue Nova Store", "Startseite mit App des Tages und bunten Kategorien."),
            ("novaos", "Design „Nebula“", "Neues Hintergrundbild in Hell und Dunkel, neuer Start- und Anmeldebildschirm."),
            ("updates", "NovaOS 2 holen", "In Software-Update mit einem Klick – installiert beim nächsten Neustart, mit Fortschritt auf dem Startbildschirm."),
        ],
        "changes": [
            "„NovaOS 2 holen“ in Software-Update",
            "Neue App Nova Disk: Übersicht, Speicher analysieren, Papierkorb, Dateien retten",
            "Nova Store: App des Tages und Kategorie-Kacheln",
            "Hintergrundbild „Nova Nebula“, Startbildschirm und Anmeldebildschirm von NovaOS 2",
            "Willkommen bei NovaOS 2 nach dem Upgrade",
        ],
    },
    {
        "version": "0.1.1.97",
        "date": "27.11.2026",
        "title": "Modernes Dock",
        "tagline": "Die Taskleiste schwebt jetzt in der Mitte – aufgeräumt und größer.",
        "tags": ["Design"],
        "highlights": [
            ("novaos", "Schwebendes Glas-Dock", "In der Mitte, so breit wie sein Inhalt: Start, Apps, Symbole und Uhr."),
        ],
        "changes": [
            "Taskleiste mittig und schwebend, größere Symbole",
            "Ohne Platzhalter, Arbeitsflächen-Umschalter und „Desktop anzeigen“",
        ],
    },
    {
        "version": "0.1.1.96",
        "date": "27.11.2026",
        "title": "Nova Browser: Optimierung und Stabilität",
        "tagline": "Schneller, ruhiger, sparsamer – und jetzt fest im Nova Store.",
        "tags": ["Nova Browser", "Stabilität"],
        "highlights": [
            ("browser", "Schnellerer neuer Tab", "Ohne gesponserte Kacheln und Geschichten, keine „Trend“-Vorschläge beim Tippen."),
            ("store", "Im Nova Store aktualisieren", "Nova Browser steht unter den NovaOS-Apps – mit „Aktualisieren“, wenn es Neues gibt."),
        ],
        "changes": [
            "Inaktive Tabs werden bei knappem Speicher entladen",
            "Videos über die Grafikkarte (wo möglich), weicheres Scrollen",
            "Weniger Schreibzugriffe auf die Festplatte",
        ],
    },
    {
        "version": "0.1.1.91",
        "date": "27.11.2026",
        "title": "Claude, ChatGPT und WhatsApp",
        "tagline": "Die beliebten Dienste als Apps – mit eigenem Fenster und eigenem Symbol.",
        "tags": ["Apps", "Nova Store"],
        "image": "webapps",
        "highlights": [
            ("claude", "Web-Apps im Nova Store", "„Hinzufügen“ legt eine echte App ins Startmenü – sie öffnet die offizielle Webseite in einem eigenen Fenster."),
        ],
        "changes": [
            "Neuer Bereich „Web-Apps“ im Nova Store",
            "Claude, ChatGPT und WhatsApp Web – nur die offiziellen Webseiten",
        ],
    },
    {
        "version": "0.1.1.90",
        "date": "26.11.2026",
        "title": "Mein Konto",
        "tagline": "E-Mail-Adresse hinzufügen und mit einem Code bestätigen.",
        "tags": ["Neue Funktion"],
        "highlights": [
            ("settings", "E-Mail bestätigen", "In den Systemeinstellungen unter System → Mein Konto – mit einem echten Code per E-Mail."),
        ],
        "changes": ["Mein Konto in den Systemeinstellungen", "Bestätigungscode per E-Mail"],
    },
    {
        "version": "0.1.1.89",
        "date": "26.11.2026",
        "title": "Nova Updates im neuen Look",
        "tagline": "Version oben, „Alles ist aktuell“ und die Frage „Jetzt neu starten?“ – wie am Handy.",
        "tags": ["Design", "Neue Funktion"],
        "image": "updates",
        "highlights": [
            ("updates", "Neuer Look", "Version oben, klare Karte „Alles ist aktuell“, „Was ist neu“."),
            ("updates", "Jetzt neu starten?", "Nach dem Update fragt NovaOS, ob es gleich neu starten soll."),
        ],
        "changes": ["Nova Updates neu gestaltet", "Neustart-Frage nach der Installation"],
    },
    {
        "version": "0.1.1.87",
        "date": "26.11.2026",
        "title": "Glas-Design",
        "tagline": "Durchsichtige Taskleiste, weiche Schatten und ein neuer Anmeldebildschirm.",
        "tags": ["Design"],
        "highlights": [
            ("novaos", "Glas überall", "Schwebende, durchsichtige Taskleiste, leicht durchsichtige Menüs, große weiche Fensterschatten."),
            ("welcome", "Neue Anmeldung", "Neuer Anmeldebildschirm, Glaskarte „Willkommen“ und Leuchten beim Einschalten."),
        ],
        "changes": ["Glas-Design für Taskleiste, Menüs und Fenster", "Neuer Anmelde- und Startbildschirm"],
    },
    {
        "version": "0.1.1.86",
        "date": "26.11.2026",
        "title": "Nova WLAN",
        "tagline": "Netzwerk wählen, Passwort eingeben, fertig.",
        "tags": ["Neue Funktion"],
        "image": "wifi",
        "highlights": [
            ("settings", "WLAN wie am Handy", "Ohne IP-Einstellungen: Netz antippen, Passwort, verbunden. Auch in den Systemeinstellungen."),
            ("novaos", "Normaler Mauszeiger", "Der Zeiger wird beim schnellen Bewegen nicht mehr riesig."),
        ],
        "changes": ["Neue App Nova WLAN", "Mauszeiger wächst beim Schütteln nicht mehr"],
    },
    {
        "version": "0.1.1.84",
        "date": "26.11.2026",
        "title": "Software-Update",
        "tagline": "NovaOS aktualisiert sich jetzt selbst – sicher und beim Neustart.",
        "tags": ["Sicherheit", "Neue Funktion"],
        "highlights": [
            ("updates", "Software-Update", "In den Systemeinstellungen unter System. NovaOS sucht alle 14 Tage und installiert beim nächsten Neustart."),
            ("about", "Signiert", "Updates kommen über einen signierten Update-Kanal; Sicherheitsupdates weiter direkt von Debian."),
        ],
        "changes": ["Signierter NovaOS-Update-Kanal", "Software-Update in den Systemeinstellungen", "Installation beim Neustart mit Fortschritt"],
    },
]

GUIDES = [
    {
        "slug": "installieren",
        "icon": "install",
        "title": "NovaOS installieren",
        "summary": "Vom USB-Stick auf deinen PC – in etwa 20 Minuten.",
        "time": "ca. 20 Min.",
        "warning": "„Festplatte löschen“ löscht alles auf der Festplatte – auch Windows, deine Dateien und Apps. "
                   "Sichere wichtige Dateien vorher auf einem USB-Stick oder in der Cloud.",
        "steps": [
            ("NovaOS herunterladen", f"Lade die Datei <b>novaos-…-amd64.iso</b> von der <a href=\"{RELEASES}\">Download-Seite</a> herunter."),
            ("USB-Stick vorbereiten", "Nimm einen USB-Stick mit mindestens 8 GB und schreibe die ISO-Datei mit <b>Rufus</b> oder <b>balenaEtcher</b> darauf. Alles auf dem Stick wird gelöscht."),
            ("Vom USB-Stick starten", "Stecke den Stick ein. Unter Windows: <b>Einstellungen → System → Wiederherstellung → Erweiterter Start → Jetzt neu starten → Ein Gerät verwenden → USB-Stick</b> (bei Lenovo „USB HDD“). Andere PCs: direkt nach dem Einschalten <b>F12</b>, <b>F10</b> oder <b>Esc</b> drücken."),
            ("NovaOS ausprobieren", "NovaOS startet vom Stick. Du kannst alles ausprobieren – auf der Festplatte ändert sich noch nichts."),
            ("Installieren", "Klicke auf <b>NovaOS installieren</b>, wähle Sprache und Tastatur, lege deinen Benutzer an und wähle <b>Festplatte löschen</b>."),
            ("Fertig", "Nach der Installation neu starten und den USB-Stick abziehen. Willkommen bei NovaOS!"),
        ],
        "tips": ["Windows-Dateien und -Apps werden nicht übernommen.", "Mit Netzwerkkabel oder WLAN geht die Installation am schnellsten."],
    },
    {
        "slug": "aktualisieren",
        "icon": "updates",
        "title": "NovaOS aktualisieren",
        "summary": "Neue Funktionen und Sicherheitsupdates in wenigen Klicks.",
        "time": "ca. 5 Min.",
        "steps": [
            ("Software-Update öffnen", "<b>Systemeinstellungen → System → Software-Update</b> – oder die App <b>Nova Updates</b>."),
            ("Nach Updates suchen", "Tippe auf <b>Nach Updates suchen</b>. Oben siehst du deine Version, z. B. „Build 0.1.1.100“."),
            ("Installieren", "Tippe auf <b>Alle Updates installieren</b> und gib dein Passwort ein."),
            ("Neu starten", "Wenn NovaOS fragt: <b>Jetzt neu starten</b>. Danach ist alles Neue da."),
        ],
        "tips": ["NovaOS sucht auch selbst alle 14 Tage und installiert beim nächsten Neustart.",
                 "Unter „Neu in NovaOS“ führt <b>Alle Details ansehen</b> zur Seite des Updates."],
    },
    {
        "slug": "novaos-2",
        "icon": "welcome",
        "title": "NovaOS 2 holen",
        "summary": "Das große Upgrade – kostenlos, deine Dateien bleiben.",
        "time": "ca. 15–30 Min.",
        "image": "get2",
        "steps": [
            ("Software-Update öffnen", "<b>Systemeinstellungen → System → Software-Update</b>. Installiere zuerst alle Updates und starte neu."),
            ("NovaOS 2 holen", "Auf der lila Karte <b>NovaOS 2</b> tippst du auf <b>NovaOS 2 holen</b> und bestätigst."),
            ("Warten", "NovaOS lädt alles im Hintergrund herunter. Du kannst weiterarbeiten."),
            ("Neu starten und installieren", "Bei „NovaOS 2 ist bereit“: <b>Neu starten und installieren</b>. Auf dem Startbildschirm siehst du den Fortschritt."),
            ("Willkommen bei NovaOS 2", "Nach dem Anmelden begrüßt dich die NovaOS-2-Karte."),
        ],
        "warning": "Den PC während der Installation nicht ausschalten.",
        "tips": ["Speichere vor dem Neustart deine Arbeit."],
    },
    {
        "slug": "wlan",
        "icon": "settings",
        "title": "Mit dem WLAN verbinden",
        "summary": "Wie am Handy: Netz wählen, Passwort, fertig.",
        "time": "1 Min.",
        "image": "wifi",
        "steps": [
            ("Netzwerk öffnen", "Klicke unten rechts in der Taskleiste auf das <b>WLAN-Symbol</b> – oder öffne <b>Nova WLAN</b>."),
            ("Netz wählen", "Tippe auf den Namen deines WLANs."),
            ("Passwort eingeben", "Gib das WLAN-Passwort ein und tippe auf <b>Verbinden</b>."),
        ],
        "tips": ["NovaOS merkt sich das Netz und verbindet sich beim nächsten Mal selbst."],
    },
    {
        "slug": "dateien-retten",
        "icon": "disk",
        "title": "Gelöschte Dateien retten",
        "summary": "Fotos, Videos und Dokumente zurückholen – mit Nova Disk.",
        "time": "10 Min. bis Stunden",
        "image": "rescue",
        "warning": "Benutze das Laufwerk so wenig wie möglich, bis die Dateien gerettet sind – jede neue Datei kann gelöschte überschreiben.",
        "steps": [
            ("Nova Disk öffnen", "Starte <b>Nova Disk</b> (gibt es mit NovaOS 2) und wähle <b>Dateien retten</b>."),
            ("Laufwerk wählen", "Wähle das Laufwerk, auf dem die Dateien waren. USB-Sticks und Speicherkarten stehen oben."),
            ("Dateiarten wählen", "Fotos, Videos, Dokumente, Musik oder Archive."),
            ("Ziel wählen", "Wähle einen Ordner auf einem <b>anderen</b> Laufwerk, z. B. einem zweiten USB-Stick."),
            ("Suche starten", "Tippe auf <b>Suche starten</b> und gib dein Passwort ein. Die Funde erscheinen nach und nach."),
            ("Dateien ansehen", "Mit <b>Gerettete Dateien öffnen</b> siehst du alles, was gefunden wurde."),
        ],
        "tips": ["Gerettete Dateien bekommen neue Namen (z. B. f1234567.jpg) – Fotos behalten Datum und Kameradaten."],
    },
    {
        "slug": "speicher",
        "icon": "files",
        "title": "Speicherplatz frei machen",
        "summary": "Sehen, was die Festplatte füllt – und aufräumen.",
        "time": "5 Min.",
        "image": "disk",
        "steps": [
            ("Speicher analysieren", "Öffne <b>Nova Disk → Speicher analysieren</b> und wähle <b>Persönlicher Ordner</b>."),
            ("Größte Dateien finden", "Nova Disk zeigt, welche Dateiarten, Ordner und Dateien am meisten Platz brauchen."),
            ("Aufräumen", "Verschiebe, was du nicht mehr brauchst, in den <b>Papierkorb</b>."),
            ("Papierkorb", "Unter <b>Papierkorb</b> kannst du Dateien wiederherstellen."),
        ],
    },
    {
        "slug": "apps",
        "icon": "store",
        "title": "Apps installieren",
        "summary": "Mit dem Nova Store – inklusive Claude, ChatGPT und WhatsApp.",
        "time": "2 Min.",
        "image": "webapps",
        "steps": [
            ("Nova Store öffnen", "Starte den <b>Nova Store</b> aus dem Startmenü."),
            ("App finden", "Stöbere in den Kategorien oder suche nach einem Namen."),
            ("Installieren", "Tippe auf <b>Installieren</b> – danach auf <b>Öffnen</b>."),
            ("Web-Apps", "Unter <b>Web-Apps</b> fügst du Claude, ChatGPT und WhatsApp mit <b>Hinzufügen</b> hinzu."),
        ],
        "tips": ["Unter <b>Updates</b> im Nova Store aktualisierst du Apps und den Nova Browser."],
    },
    {
        "slug": "konto",
        "icon": "about",
        "title": "E-Mail im Konto bestätigen",
        "summary": "Deine E-Mail-Adresse in „Mein Konto“ hinzufügen.",
        "time": "3 Min.",
        "steps": [
            ("Mein Konto öffnen", "<b>Systemeinstellungen → System → Mein Konto</b>."),
            ("E-Mail hinzufügen", "Gib deine E-Mail-Adresse und das Passwort deines E-Mail-Kontos ein – NovaOS schickt dir darüber einen Code."),
            ("Code eingeben", "Gib den Code aus der E-Mail ein. Fertig: „✓ bestätigt“."),
        ],
        "tips": ["Das Passwort wird nicht gespeichert."],
    },
]

FAQ = [
    ("Werden meine Windows-Dateien übernommen?",
     "Nein. Bei „Festplatte löschen“ wird alles gelöscht. Sichere wichtige Dateien vorher."),
    ("Kostet NovaOS etwas?", "Nein. NovaOS und alle Updates – auch NovaOS 2 – sind kostenlos."),
    ("Wie bekomme ich Updates?", "Über Software-Update in den Systemeinstellungen. NovaOS sucht auch selbst alle 14 Tage."),
    ("Bleiben meine Dateien bei NovaOS 2?", "Ja. Dateien, Einstellungen und Apps bleiben."),
    ("Worauf basiert NovaOS?", "Auf Debian 13 und KDE Plasma 6 – stabil, sicher und frei."),
]
