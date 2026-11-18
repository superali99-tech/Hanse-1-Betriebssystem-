#!/usr/bin/env python3
"""Edit decision list of the NovaOS trailer (2:00).

Every entry: start time (s) -> scene. Cuts sit on the beat of the soundtrack
(96 BPM: one beat = 0.625 s, a bar = 2.5 s; drums enter at 30 s, break at
85 s, final lift at 95 s, outro from 112.5 s). The cross fade into a scene
starts at its time, so times are "beat - 0.25".

All captions describe things that are visible in the recording or that the
system really does (see docs/TESTBERICHT.md). Writes scenes.json for
make_trailer.py:  python3 scenes.py build/trailer/scenes.json
"""
import json
import sys

END = 120.0
S = "shots/"


def cap(title, sub=None):
    return [title, sub] if sub else [title]


TIMELINE = [
    (0.0, {"type": "intro", "tagline": "Schön. Sicher. Frei."}),
    (7.25, {"type": "image", "src": S + "01-grub.png", "zoom": [1.0, 1.08],
            "caption": cap("Startet vom USB-Stick", "BIOS und UEFI · Secure Boot")}),
    (9.75, {"type": "seq", "srcs": [S + "02-splash-a.png", S + "02-splash-b.png"] * 4, "fps": 3.2,
            "zoom": [1.0, 1.05]}),
    (12.25, {"type": "image", "src": S + "03-welcome.png", "zoom": [1.0, 1.08], "focus": [[0.5, 0.5], [0.5, 0.45]],
             "caption": cap("Willkommen bei NovaOS", "Der Assistent hilft beim Einrichten")}),
    (16.0, {"type": "image", "src": S + "04-design-choice.png", "zoom": [1.04, 1.12]}),
    (19.75, {"type": "image", "src": S + "03c-desktop-clean.png", "zoom": [1.12, 1.0],
             "caption": cap("Dein neuer Desktop", "KDE Plasma 6 im NovaOS-Design")}),
    (24.75, {"type": "image", "src": S + "05-startmenu.png", "zoom": [1.0, 1.25], "focus": [[0.5, 0.6], [0.42, 0.72]],
             "caption": cap("Startmenü", "Alle Apps auf einen Blick")}),
    (29.75, {"type": "image", "src": S + "06-search.png", "zoom": [1.3, 1.4], "focus": [[0.42, 0.7], [0.42, 0.7]],
             "caption": cap("Tippen und finden", "Apps, Dateien und Einstellungen")}),
    (32.25, {"type": "image", "src": S + "20-calendar.png", "zoom": [1.0, 1.08]}),
    (34.75, {"type": "image", "src": S + "10-store-home.png", "zoom": [1.0, 1.1], "focus": [[0.5, 0.4], [0.55, 0.45]],
             "caption": cap("Nova Store", "3415 Apps von Flathub – kostenlos")}),
    (39.75, {"type": "image", "src": S + "11-store-detail.png", "zoom": [1.15, 1.05], "focus": [[0.4, 0.3], [0.5, 0.5]],
             "caption": cap("Alle Infos vor dem Download", "Screenshots, Version, echte Bewertungen")}),
    (42.25, {"type": "image", "src": S + "12-store-search.png", "zoom": [1.2, 1.3], "focus": [[0.4, 0.25], [0.4, 0.25]]}),
    (44.75, {"type": "image", "src": "shots/p03.png", "zoom": [1.0, 1.35], "focus": [[0.6, 0.35], [0.72, 0.25]],
             "caption": cap("Ein Klick: Installieren", "Mit Fortschritt in Prozent")}),
    (49.75, {"type": "image", "src": S + "14-tuxpaint-splash.png", "zoom": [1.0, 1.06],
             "caption": cap("Fertig – und gleich startklar")}),
    (52.25, {"type": "features", "title": "Alles dabei", "sub": "Vom ersten Start an",
             "items": ["Nova Browser – Firefox ESR", "Nova Files, Notes und Calendar",
                       "Nova Terminal und Task Manager", "Nova Updates und Nova Hilfe"],
             "bg": S + "03b-desktop.png"}),
    (59.75, {"type": "image", "src": S + "27-browser.png", "zoom": [1.0, 1.1], "focus": [[0.5, 0.4], [0.62, 0.38]],
             "caption": cap("Nova Browser", "Tracking-Schutz und HTTPS-only ab Werk")}),
    (64.75, {"type": "image", "src": S + "25-files.png", "zoom": [1.05, 1.18], "focus": [[0.5, 0.45], [0.5, 0.4]],
             "caption": cap("Nova Files")}),
    (67.25, {"type": "image", "src": S + "22-notes.png", "zoom": [1.05, 1.2], "focus": [[0.5, 0.4], [0.5, 0.33]],
             "caption": cap("Nova Notes")}),
    (69.75, {"type": "image", "src": S + "24-taskmanager-perf.png", "zoom": [1.05, 1.2], "focus": [[0.5, 0.4], [0.5, 0.35]],
             "caption": cap("Nova Task Manager")}),
    (72.25, {"type": "image", "src": S + "26-terminal.png", "zoom": [1.1, 1.25], "focus": [[0.45, 0.4], [0.4, 0.35]],
             "caption": cap("Nova Terminal")}),
    (74.75, {"type": "image", "src": S + "21-updates.png", "zoom": [1.05, 1.2], "focus": [[0.5, 0.45], [0.5, 0.4]],
             "caption": cap("Nova Updates", "Sicherheitsupdates automatisch")}),
    (77.25, {"type": "image", "src": S + "28-settings.png", "zoom": [1.05, 1.15], "focus": [[0.5, 0.45], [0.55, 0.45]],
             "caption": cap("Nova Settings")}),
    (79.75, {"type": "image", "src": S + "30-dark-settings.png", "zoom": [1.15, 1.0], "focus": [[0.55, 0.45], [0.5, 0.5]],
             "caption": cap("Hell oder dunkel?", "Ein Klick – das ganze System wechselt")}),
    (84.75, {"type": "image", "src": S + "35-dark-desktop.png", "zoom": [1.0, 1.08]}),
    (87.25, {"type": "image", "src": S + "31-dark-startmenu.png", "zoom": [1.1, 1.25], "focus": [[0.45, 0.65], [0.42, 0.72]]}),
    (89.75, {"type": "image", "src": S + "32-dark-store.png", "zoom": [1.05, 1.15], "focus": [[0.5, 0.45], [0.55, 0.5]]}),
    (92.25, {"type": "image", "src": S + "33-about-dark.png", "zoom": [1.1, 1.25], "focus": [[0.5, 0.42], [0.5, 0.4]],
             "caption": cap("Über NovaOS", "Debian 13 · Linux 6.12 · KDE Plasma 6")}),
    (94.75, {"type": "features", "title": "Sicher und privat", "sub": "Ab Werk eingebaut",
             "items": ["Keine Telemetrie", "Firewall ab Werk aktiv", "Automatische Sicherheitsupdates",
                       "Apps in der Sandbox (Flatpak)", "Optionale Vollverschlüsselung"],
             "bg": S + "35-dark-desktop.png"}),
    (102.25, {"type": "image", "src": S + "40-installer-start.png", "zoom": [1.0, 1.08],
              "caption": cap("In wenigen Minuten installiert", "Grafischer Installer auf Deutsch")}),
    (104.75, {"type": "image", "src": S + "42-installer-confirm.png", "zoom": [1.05, 1.3], "focus": [[0.5, 0.45], [0.5, 0.48]],
              "caption": cap("Du behältst die Kontrolle", "Nichts wird ohne Bestätigung gelöscht")}),
    (107.25, {"type": "image", "src": S + "43-installer-progress.png", "zoom": [1.0, 1.1]}),
    (109.75, {"type": "image", "src": S + "44-installer-done.png", "zoom": [1.0, 1.1]}),
    (111.75, {"type": "image", "src": S + "45-login.png", "zoom": [1.0, 1.06]}),
    (113.5, {"type": "outro", "lines": [
        ["NovaOS 0.1 „Aurora“", "Bold", 66, [255, 255, 255]],
        ["Basierend auf Debian 13 und KDE Plasma 6", "Regular", 36, [200, 206, 240]],
        ["Kostenlos · Open Source · Keine Telemetrie", "Medium", 36, [200, 206, 240]],
        ["Download: GitHub › Releases", "SemiBold", 42, [180, 165, 255]],
        ["Alle Aufnahmen zeigen das echte NovaOS 0.1, aufgenommen in einer virtuellen Maschine.",
         "Regular", 22, [150, 156, 190]],
    ]}),
]


def build():
    scenes = []
    for i, (at, sc) in enumerate(TIMELINE):
        nxt = TIMELINE[i + 1][0] if i + 1 < len(TIMELINE) else None
        sc = dict(sc)
        sc["dur"] = round((nxt - at + 0.5) if nxt is not None else END - at, 3)
        scenes.append(sc)
    return scenes


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "scenes.json"
    scenes = build()
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(scenes, fh, ensure_ascii=False, indent=1)
    total = sum(s["dur"] for s in scenes) - 0.5 * (len(scenes) - 1)
    print(f"{len(scenes)} scenes, {total:.2f} s -> {out}")
