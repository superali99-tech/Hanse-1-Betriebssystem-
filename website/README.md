# NovaOS-Website

Startseite, alle Updates mit allen Details (wie bei Samsung) und Anleitungen – modern, hell und dunkel,
für PC und Handy. Statisches HTML ohne Anfragen ins Internet (keine fremden Schriften, Skripte oder Cookies).

* **Im Nova Browser eingebaut:** Paket `novaos-browser` → `/usr/share/novaos/website/` (funktioniert ohne
  Internet). Startseite des Nova Browsers und Lesezeichen „NovaOS“ (Firefox-Richtlinien `Homepage`,
  `ManagedBookmarks`).
* **Software-Update:** unter „Neu in NovaOS“ führt **Alle Details ansehen** zu `updates/aktuell.html`
  (dem neuesten Update), sonst zur Online-Version.
* **Online (kostenlos):** `https://superali99-tech.github.io/Hanse-1-Betriebssystem-/` über GitHub Pages –
  Workflow `.github/workflows/tv-web.yml` („NovaOS website“, NovaOS TV Web unter `/tv/`). Einmalig nötig:
  *Settings → Pages → Source: GitHub Actions*; zum Veröffentlichen aus einem anderen als dem
  Standard-Branch den Branch unter *Settings → Environments → github-pages* erlauben.

## Ein neues Update eintragen

In `content.py` oben in `UPDATES` einen Eintrag ergänzen (Titel, Satz, Schlagworte, Highlights, alle
Änderungen, optional ein Bild aus `SHOTS`). Der neueste Eintrag darf `"version": "auto"` haben – dann
setzt `build.py` die Build-Nummer der Pakete ein; beim nächsten Update die feste Nummer eintragen.

```
python3 website/build.py /tmp/site --version 0.1.1.101   # Vorschau: /tmp/site/index.html
```

Seiten: `index.html`, `updates/index.html`, `updates/<version>.html`, `updates/aktuell.html`,
`anleitungen/index.html`, `anleitungen/<thema>.html`. Alle Links sind relativ und nennen ihre Datei, damit
die Seite auch über `file://` läuft. Test: `tests/unit/test_website.py` (alle Seiten, alle Links, nichts von
außen).
