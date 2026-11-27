#!/usr/bin/env python3
"""Builds the NovaOS website (static HTML, no external requests, works offline and online).

    python3 website/build.py OUTDIR [--version 0.1.1.101]

Pages: index.html, updates/index.html, updates/<version>.html, updates/aktuell.html (the
newest update), anleitungen/index.html, anleitungen/<slug>.html. Every link is relative and
names its file (…/index.html), so the site works from file:// in Nova Browser
(/usr/share/novaos/website, package novaos-browser) and on GitHub Pages.
"""

from __future__ import annotations

import argparse
import html
import os
import random
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import content as C  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SITE = os.path.dirname(os.path.abspath(__file__))
E = html.escape


# ------------------------------------------------------------------ helpers
def edition_of(version: str) -> str:
    try:
        n = int(version.split(".")[-1])
    except ValueError:
        return "NovaOS 2"
    return "NovaOS 2" if n >= 98 else "NovaOS"


def resolve_updates(version: str) -> list[dict]:
    out = []
    for u in C.UPDATES:
        u = dict(u)
        if u["version"] == "auto":
            u["version"] = version or "neu"
        out.append(u)
    return out


def icon(key: str, rel: str, cls: str = "ico") -> str:
    return f'<img class="{cls}" src="{rel}assets/icons/{key}.svg" alt="" loading="lazy">'


def shot(key: str, rel: str, alt: str, cls: str = "shot") -> str:
    ext = os.path.splitext(C.SHOTS[key])[1]
    return f'<img class="{cls}" src="{rel}assets/shots/{key}{ext}" alt="{E(alt)}" loading="lazy">'


def chips(tags: list[str]) -> str:
    return "".join(f'<span class="chip">{E(t)}</span>' for t in tags)


def page(title: str, body: str, rel: str, active: str, description: str) -> str:
    nav = [("index.html", "Start", "start"), ("updates/index.html", "Neuigkeiten", "updates"),
           ("anleitungen/index.html", "Anleitungen", "guides"), ("index.html#download", "Download", "download")]
    links = "".join(f'<a href="{rel}{h}" class="{"on" if k == active else ""}">{t}</a>' for h, t, k in nav)
    return f"""<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{E(title)}</title>
<meta name="description" content="{E(description)}">
<meta name="color-scheme" content="dark light">
<link rel="icon" href="{rel}assets/icons/novaos.svg">
<link rel="stylesheet" href="{rel}assets/style.css">
<script>try{{var t=localStorage.getItem("nova-theme");if(t)document.documentElement.dataset.theme=t}}catch(e){{}}</script>
</head>
<body>
<header class="nav" id="nav">
  <a class="brand" href="{rel}index.html">{icon("novaos", rel, "brand-ico")}<span>Nova<b>OS</b></span></a>
  <nav class="links" id="links">{links}</nav>
  <div class="nav-actions">
    <a class="nav-install nova-only" href="novaos-app:install-website" title="NovaOS als App installieren – immer in der Taskleiste">Als App installieren</a>
    <button class="theme" id="theme" type="button" aria-label="Hell oder dunkel">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3a9 9 0 1 0 9 9 7 7 0 0 1-9-9z"/></svg>
    </button>
    <button class="burger" id="burger" type="button" aria-label="Menü"><span></span><span></span></button>
  </div>
</header>
<main>
{body}
</main>
<footer class="foot">
  <div class="foot-in">
    <div class="foot-brand">{icon("novaos", rel, "brand-ico")}<div><b>NovaOS</b><br><span>Schnell, schön und frei – auf Basis von Debian 13 und KDE Plasma.</span></div></div>
    <div class="foot-links">
      <a href="{rel}updates/index.html">Neuigkeiten</a>
      <a href="{rel}anleitungen/index.html">Anleitungen</a>
      <a href="{C.RELEASES}">Download</a>
      <a href="{C.GITHUB}">Quelltext</a>
      <a href="{C.ONLINE}">Online-Version</a>
    </div>
  </div>
  <p class="fine">© 2026 NovaOS Project · Diese Seite lädt nichts aus dem Internet nach und setzt keine Cookies.</p>
</footer>
<script src="{rel}assets/site.js"></script>
</body>
</html>
"""


# ------------------------------------------------------------------ start page
def build_index(updates: list[dict]) -> str:
    rel = ""
    latest = updates[0]
    hl = "".join(f'<li>{icon(k, rel)}<div><b>{E(t)}</b><span>{E(d)}</span></div></li>' for k, t, d in latest["highlights"][:3])
    guides = "".join(f'''<a class="guide-card reveal" href="anleitungen/{g["slug"]}.html">
        {icon(g["icon"], rel, "ico-lg")}<h3>{E(g["title"])}</h3><p>{E(g["summary"])}</p><span class="more">Lesen →</span></a>'''
                     for g in C.GUIDES[:4])
    faq = "".join(f'<details class="faq reveal"><summary>{E(q)}</summary><p>{E(a)}</p></details>' for q, a in C.FAQ)
    body = f"""
<section class="hero">
  <div class="stars s1"></div><div class="stars s2"></div><div class="orb o1"></div><div class="orb o2"></div><div class="orb o3"></div>
  <div class="hero-in">
    <div class="hero-text">
      <span class="eyebrow reveal">Jetzt verfügbar · kostenlos</span>
      <h1 class="reveal">NovaOS <span class="grad">2</span></h1>
      <p class="lead reveal">„Nebula“ – schneller, schöner und voller neuer Ideen. Mit Nova Disk, dem neuen Nova Store
      und einer runden Glas-Taskleiste.</p>
      <div class="cta reveal">
        <a class="btn primary" href="updates/aktuell.html">Was ist neu</a>
        <a class="btn ghost" href="anleitungen/novaos-2.html">NovaOS 2 holen</a>
      </div>
      <a class="install-app nova-only reveal" href="novaos-app:install-website">{icon("novaos", rel)}<span><b>Als App installieren</b>
        Immer in der Taskleiste – ein Klick, fertig</span></a>
    </div>
    <div class="hero-visual reveal">
      <div class="window">{shot("card", rel, "Willkommen bei NovaOS 2")}</div>
    </div>
  </div>
</section>

<section class="sec">
  <div class="sec-head reveal"><span class="eyebrow">NovaOS 2</span><h2>Alles neu. Alles schöner.</h2>
  <p>Die wichtigsten Neuerungen auf einen Blick.</p></div>
  <div class="bento">
    <article class="tile t-wide reveal">
      <div class="tile-text">{icon("disk", rel, "ico-lg")}<h3>Nova Disk</h3>
      <p>Sieh, was deine Festplatte füllt, prüfe deine Laufwerke und rette gelöschte Fotos, Videos und Dokumente –
      auch von USB-Sticks und SD-Karten.</p><a class="more" href="anleitungen/dateien-retten.html">So rettest du Dateien →</a></div>
      {shot("disk", rel, "Nova Disk", "tile-shot")}
    </article>
    <article class="tile t-tall reveal">
      <div class="tile-text">{icon("store", rel, "ico-lg")}<h3>Der neue Nova Store</h3>
      <p>App des Tages, bunte Kategorien und Web-Apps wie Claude, ChatGPT und WhatsApp.</p></div>
      {shot("store", rel, "Nova Store", "tile-shot")}
    </article>
    <article class="tile t-half reveal">
      <div class="tile-text">{icon("novaos", rel, "ico-lg")}<h3>Runde Taskleiste</h3>
      <p>Ein schwebendes Glas-Dock mit runden Ecken – die aktive App leuchtet.</p></div>
      {shot("dock", rel, "Runde Taskleiste", "tile-shot")}
    </article>
    <article class="tile t-half nebula reveal">
      <div class="tile-text"><h3>Design „Nebula“</h3>
      <p>Neues Hintergrundbild in Hell und Dunkel, neuer Start- und Anmeldebildschirm.</p></div>
      <div class="nebula-art"><div class="stars s1"></div><span class="planet"></span></div>
    </article>
    <article class="tile reveal"><div class="tile-text">{icon("browser", rel, "ico-lg")}<h3>Nova Browser</h3>
      <p>Privat und schnell: blockiert Tracker, nur HTTPS – mit dieser Website eingebaut.</p></div></article>
    <article class="tile reveal"><div class="tile-text">{icon("updates", rel, "ico-lg")}<h3>Software-Update</h3>
      <p>Updates beim Neustart, mit Fortschritt – und zu jedem Update alle Details.</p></div></article>
    <article class="tile reveal"><div class="tile-text">{icon("about", rel, "ico-lg")}<h3>Sicher &amp; frei</h3>
      <p>Debian 13 als Fundament, keine Werbung, keine Telemetrie.</p></div></article>
  </div>
</section>

<section class="sec">
  <a class="latest reveal" href="updates/aktuell.html">
    <div class="latest-head"><span class="badge">Neuestes Update</span><span class="ver">{E(edition_of(latest["version"]))} · Build {E(latest["version"])}</span></div>
    <h2>{E(latest["title"])}</h2>
    <p>{E(latest["tagline"])}</p>
    <ul class="hl">{hl}</ul>
    <span class="btn primary">Alle Details ansehen</span>
  </a>
</section>

<section class="sec">
  <div class="sec-head reveal"><span class="eyebrow">Hilfe</span><h2>Anleitungen</h2><p>Schritt für Schritt erklärt.</p></div>
  <div class="guides">{guides}</div>
  <p class="center reveal"><a class="btn ghost" href="anleitungen/index.html">Alle Anleitungen</a></p>
</section>

<section class="sec" id="download">
  <div class="sec-head reveal"><span class="eyebrow">Download</span><h2>Hol dir NovaOS</h2></div>
  <div class="dl">
    <a class="dl-card reveal" href="{C.RELEASES}">{icon("install", rel, "ico-lg")}<h3>NovaOS für deinen PC</h3>
      <p>ISO-Datei für einen USB-Stick – 64-Bit-PCs mit UEFI oder BIOS.</p><span class="more">Zum Download →</span></a>
    <a class="dl-card reveal" href="anleitungen/aktualisieren.html">{icon("updates", rel, "ico-lg")}<h3>Schon installiert?</h3>
      <p>Hol dir das neueste Update über Software-Update.</p><span class="more">So geht's →</span></a>
  </div>
</section>

<section class="sec narrow">
  <div class="sec-head reveal"><h2>Häufige Fragen</h2></div>
  {faq}
</section>
"""
    return page("NovaOS 2 – schnell, schön und frei", body, rel, "start",
                "NovaOS 2 „Nebula“: das freie Betriebssystem mit Nova Disk, Nova Store und runder Glas-Taskleiste.")


# ------------------------------------------------------------------ updates
def build_updates_index(updates: list[dict]) -> str:
    rel = "../"
    items = []
    for i, u in enumerate(updates):
        new = '<span class="badge">Neu</span>' if i == 0 else ""
        items.append(f"""<a class="tl-item reveal" href="{E(u['version'])}.html">
      <div class="tl-dot"></div>
      <div class="tl-meta"><span class="ver">Build {E(u['version'])}</span><span class="date">{E(u['date'])}</span></div>
      <div class="tl-card"><div class="tl-top">{new}{chips(u['tags'])}</div>
        <h3>{E(u['title'])}</h3><p>{E(u['tagline'])}</p><span class="more">Alle Details →</span></div>
    </a>""")
    body = f"""
<section class="page-hero">
  <div class="stars s1"></div><div class="orb o1"></div><div class="orb o2"></div>
  <div class="page-hero-in"><span class="eyebrow reveal">Neuigkeiten</span><h1 class="reveal">Was ist neu in NovaOS</h1>
  <p class="lead reveal">Alle Updates mit allen Details – das neueste zuerst.</p></div>
</section>
<section class="sec narrow"><div class="timeline">{''.join(items)}</div></section>
"""
    return page("Neuigkeiten – NovaOS", body, rel, "updates", "Alle NovaOS-Updates mit allen Details.")


def build_update(u: dict, prev_u: dict | None, next_u: dict | None) -> str:
    rel = "../"
    hl = "".join(f'<div class="hl-card reveal">{icon(k, rel, "ico-lg")}<h3>{E(t)}</h3><p>{E(d)}</p></div>'
                 for k, t, d in u["highlights"])
    changes = "".join(f'<li>{E(c)}</li>' for c in u["changes"])
    steps = "".join(f'<li><span class="num">{i}</span><p>{s}</p></li>' for i, s in enumerate(C.INSTALL_UPDATE, 1))
    img = f'<div class="update-shot reveal">{shot(u["image"], rel, u["title"])}</div>' if u.get("image") else ""
    pager = '<nav class="pager">'
    if next_u:
        pager += f'<a href="{E(next_u["version"])}.html"><span>← Neueres Update</span><b>{E(next_u["title"])}</b></a>'
    else:
        pager += "<span></span>"
    if prev_u:
        pager += f'<a class="r" href="{E(prev_u["version"])}.html"><span>Älteres Update →</span><b>{E(prev_u["title"])}</b></a>'
    pager += "</nav>"
    body = f"""
<section class="page-hero update-hero">
  <div class="stars s1"></div><div class="orb o1"></div><div class="orb o3"></div>
  <div class="page-hero-in">
    <a class="crumb reveal" href="index.html">← Alle Neuigkeiten</a>
    <span class="eyebrow reveal">{E(edition_of(u['version']))} · Build {E(u['version'])} · {E(u['date'])}</span>
    <h1 class="reveal">{E(u['title'])}</h1>
    <p class="lead reveal">{E(u['tagline'])}</p>
    <div class="tags reveal">{chips(u['tags'])}</div>
  </div>
</section>
<section class="sec">
  {img}
  <div class="sec-head reveal"><h2>Highlights</h2></div>
  <div class="hl-grid">{hl}</div>
</section>
<section class="sec narrow">
  <div class="two">
    <div class="panel reveal"><h2>Alle Änderungen</h2><ul class="checks">{changes}</ul></div>
    <div class="panel reveal"><h2>So installierst du das Update</h2><ol class="steps compact">{steps}</ol>
      <a class="more" href="../anleitungen/aktualisieren.html">Ausführliche Anleitung →</a></div>
  </div>
  {pager}
</section>
"""
    return page(f"{u['title']} – NovaOS Build {u['version']}", body, rel, "updates", u["tagline"])


# ------------------------------------------------------------------ guides
def build_guides_index() -> str:
    rel = "../"
    cards = "".join(f'''<a class="guide-card reveal" href="{g["slug"]}.html">{icon(g["icon"], rel, "ico-lg")}
      <h3>{E(g["title"])}</h3><p>{E(g["summary"])}</p><span class="time">{E(g["time"])}</span></a>''' for g in C.GUIDES)
    faq = "".join(f'<details class="faq reveal"><summary>{E(q)}</summary><p>{E(a)}</p></details>' for q, a in C.FAQ)
    body = f"""
<section class="page-hero">
  <div class="stars s1"></div><div class="orb o2"></div><div class="orb o3"></div>
  <div class="page-hero-in"><span class="eyebrow reveal">Hilfe</span><h1 class="reveal">Anleitungen</h1>
  <p class="lead reveal">Von der Installation bis zur Datenrettung – Schritt für Schritt.</p></div>
</section>
<section class="sec"><div class="guides all">{cards}</div></section>
<section class="sec narrow"><div class="sec-head reveal"><h2>Häufige Fragen</h2></div>{faq}</section>
"""
    return page("Anleitungen – NovaOS", body, rel, "guides", "Anleitungen für NovaOS: installieren, aktualisieren, NovaOS 2, WLAN, Dateien retten.")


def build_guide(g: dict) -> str:
    rel = "../"
    steps = "".join(f'<li class="reveal"><span class="num">{i}</span><div><h3>{E(t)}</h3><p>{d}</p></div></li>'
                    for i, (t, d) in enumerate(g["steps"], 1))
    warn = f'<div class="callout warn reveal"><b>Wichtig</b><p>{E(g["warning"])}</p></div>' if g.get("warning") else ""
    tips = ("<div class=\"callout tip reveal\"><b>Tipps</b><ul>" + "".join(f"<li>{t}</li>" for t in g["tips"]) + "</ul></div>"
            if g.get("tips") else "")
    img = f'<div class="update-shot reveal">{shot(g["image"], rel, g["title"])}</div>' if g.get("image") else ""
    others = "".join(f'<a class="mini reveal" href="{o["slug"]}.html">{icon(o["icon"], rel)}<span>{E(o["title"])}</span></a>'
                     for o in C.GUIDES if o["slug"] != g["slug"])[:4000]
    body = f"""
<section class="page-hero guide-hero">
  <div class="stars s1"></div><div class="orb o1"></div><div class="orb o2"></div>
  <div class="page-hero-in">
    <a class="crumb reveal" href="index.html">← Alle Anleitungen</a>
    <div class="guide-title reveal">{icon(g["icon"], rel, "ico-xl")}<div><h1>{E(g["title"])}</h1>
    <p class="lead">{E(g["summary"])}</p><span class="chip">⏱ {E(g["time"])}</span></div></div>
  </div>
</section>
<section class="sec narrow">
  {warn}
  <ol class="steps">{steps}</ol>
  {img}
  {tips}
  <div class="sec-head reveal"><h2>Weitere Anleitungen</h2></div>
  <div class="minis">{others}</div>
</section>
"""
    return page(f"{g['title']} – Anleitung – NovaOS", body, rel, "guides", g["summary"])


# ------------------------------------------------------------------ assets
def stars(n: int, w: int, h: int, seed: int) -> str:
    rnd = random.Random(seed)
    return ", ".join(f"{rnd.randint(0, w)}px {rnd.randint(0, h)}px rgba(255,255,255,{rnd.choice((0.35, 0.55, 0.8, 1))})"
                     for _ in range(n))


def write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def build(out: str, version: str = "") -> list[str]:
    updates = resolve_updates(version)
    if os.path.isdir(out):
        shutil.rmtree(out)
    files = []

    def put(rel: str, text: str) -> None:
        write(os.path.join(out, rel), text)
        files.append(rel)

    put("index.html", build_index(updates))
    put("updates/index.html", build_updates_index(updates))
    for i, u in enumerate(updates):
        put(f"updates/{u['version']}.html", build_update(u, updates[i + 1] if i + 1 < len(updates) else None,
                                                        updates[i - 1] if i > 0 else None))
    put("updates/aktuell.html", build_update(updates[0], updates[1] if len(updates) > 1 else None, None))
    put("anleitungen/index.html", build_guides_index())
    for g in C.GUIDES:
        put(f"anleitungen/{g['slug']}.html", build_guide(g))

    with open(os.path.join(SITE, "style.css"), encoding="utf-8") as fh:
        css = fh.read()
    css = css.replace("/*STARS1*/", stars(140, 2400, 1400, 1)).replace("/*STARS2*/", stars(60, 2400, 1400, 2))
    put("assets/style.css", css)
    shutil.copyfile(os.path.join(SITE, "site.js"), os.path.join(out, "assets", "site.js"))
    files.append("assets/site.js")
    for key, src in C.ICONS.items():
        dst = os.path.join(out, "assets", "icons", key + ".svg")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(os.path.join(ROOT, src), dst)
        files.append(f"assets/icons/{key}.svg")
    for key, src in C.SHOTS.items():
        ext = os.path.splitext(src)[1]
        dst = os.path.join(out, "assets", "shots", key + ext)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(os.path.join(ROOT, src), dst)
        files.append(f"assets/shots/{key}{ext}")
    return files


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--version", default=os.environ.get("NOVAOS_DEB_VERSION", ""))
    args = ap.parse_args()
    files = build(args.out, args.version)
    print(f"NovaOS website: {len(files)} files in {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
