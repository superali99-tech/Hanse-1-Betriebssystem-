#!/usr/bin/env python3
"""Nova Store details for the F-Droid apps of res/raw/store.txt, taken from F-Droid's index.

    curl -o index-v2.json https://f-droid.org/repo/index-v2.json
    python3 -I tools/store_meta.py index-v2.json res/raw/store.txt > res/raw/store_meta.json

Per app: author, licence, website, description (German and English), size and minimum
Android version of the current APK, anti-features and the names of the phone screenshots
(loaded from f-droid.org when the details page is opened). Run again to refresh.
"""
import html
import json
import re
import sys


def pick(d, langs=("de-DE", "de", "en-US", "en", "en-GB")):
    if not isinstance(d, dict):
        return d or ""
    for l in langs:
        if d.get(l):
            return d[l]
    return next(iter(d.values()), "") if d else ""


def plain(text, limit=2400):
    t = text or ""
    t = re.sub(r"(?i)<li>\s*", "\n• ", t)
    t = re.sub(r"(?i)</p>|<br\s*/?>|</ul>|</ol>|</h\d>", "\n", t)
    t = re.sub(r"<[^>]+>", "", t)
    t = html.unescape(t)
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r"\n\s*\n\s*\n+", "\n\n", t).strip()
    if len(t) > limit:
        t = t[:limit].rsplit(" ", 1)[0] + " …"
    return t


def main():
    index = json.load(open(sys.argv[1], encoding="utf-8"))
    pkgs = [l.split("|")[1].strip() for l in open(sys.argv[2], encoding="utf-8") if l.startswith("fdroid|")]
    out = {}
    for p in pkgs:
        e = index["packages"].get(p)
        if not e:
            continue
        m = e["metadata"]
        versions = sorted(e["versions"].values(), key=lambda v: v["manifest"]["versionCode"], reverse=True)
        stable = [x for x in versions if not x.get("releaseChannels")]  # no beta versions
        v = (stable or versions)[0]
        shots = (m.get("screenshots") or {}).get("phone") or {}
        loc = next((l for l in ("de-DE", "de", "en-US", "en") if l in shots), next(iter(shots), None))
        anti = [pick(t, ("de", "de-DE", "en-US")) or k for k, t in (v.get("antiFeatures") or {}).items()]
        out[p] = {
            "author": m.get("authorName", ""),
            "license": m.get("license", ""),
            "web": m.get("webSite", "") or m.get("sourceCode", ""),
            "source": m.get("sourceCode", ""),
            "summary_de": pick(m.get("summary"), ("de-DE", "de")),
            "summary_en": pick(m.get("summary"), ("en-US", "en", "en-GB")),
            "desc_de": plain(pick(m.get("description"), ("de-DE", "de"))),
            "desc_en": plain(pick(m.get("description"), ("en-US", "en", "en-GB"))),
            "added": m.get("added", 0),
            "updated": m.get("lastUpdated", 0),
            "code": v["manifest"]["versionCode"],
            "version": v["manifest"].get("versionName", ""),
            "size": v["file"]["size"],
            "minSdk": v["manifest"].get("usesSdk", {}).get("minSdkVersion", 0),
            "permissions": len(v["manifest"].get("usesPermission") or []),
            "anti": [plain(a, 300) for a in anti],
            "shots": [s["name"] for s in (shots.get(loc) or [])][:8] if loc else [],
            # "Neuerungen": what changed in this version (if the developer wrote it), and when it came out
            "news_de": plain(pick(v.get("whatsNew"), ("de-DE", "de")), 1200),
            "news_en": plain(pick(v.get("whatsNew"), ("en-US", "en", "en-GB")), 1200),
            "news_date": v.get("added", 0),
            "changelog": m.get("changelog", "") if str(m.get("changelog", "")).startswith("https://") else "",
        }
    json.dump(out, sys.stdout, ensure_ascii=False, indent=0, sort_keys=True)
    print()


if __name__ == "__main__":
    main()
