"""The NovaOS website: every page builds, every link inside works, nothing is loaded from the internet."""

import importlib.util
import json
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")


def load_builder():
    spec = importlib.util.spec_from_file_location("site_build", os.path.join(ROOT, "website", "build.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_site_builds_and_links_work(tmp_path):
    b = load_builder()
    out = tmp_path / "site"
    files = b.build(str(out), "0.1.1.200")
    pages = [f for f in files if f.endswith(".html")]
    assert {"index.html", "updates/index.html", "updates/aktuell.html", "anleitungen/index.html"} <= set(pages)
    assert "updates/0.1.1.200.html" in pages          # the newest update gets the build number
    for u in b.C.UPDATES[1:]:
        assert f"updates/{u['version']}.html" in pages
    for g in b.C.GUIDES:
        assert f"anleitungen/{g['slug']}.html" in pages
    for page in pages:
        text = (out / page).read_text(encoding="utf-8")
        # no scripts, styles, images or fonts from elsewhere (works offline, nothing tracked)
        assert not re.search(r'<(script|link|img)[^>]+(src|href)="https?:', text), page
        for ref in re.findall(r'(?:href|src)="([^"]+)"', text):
            if ref.startswith(("http://", "https://", "#", "mailto:", "novaos-app:")):
                continue
            target = os.path.normpath(os.path.join(out, os.path.dirname(page), ref.split("#")[0]))
            assert os.path.isfile(target), f"{page}: broken link {ref}"
        assert "auto" not in re.findall(r"Build ([\w.]+)", text)


def test_browser_start_page_points_into_the_site(tmp_path):
    with open(os.path.join(ROOT, "packages", "novaos-browser", "root", "etc", "firefox", "policies",
                           "policies.json"), encoding="utf-8") as fh:
        pol = json.load(fh)["policies"]
    prefix = "file:///usr/share/novaos/website/"
    urls = [pol["Homepage"]["URL"]] + [m["url"] for m in pol["ManagedBookmarks"]
                                       if "url" in m and not m["url"].startswith("novaos-app:")]
    assert pol["Handlers"]["schemes"]["novaos-app"]["ask"] is False   # "als App installieren" without a question
    b = load_builder()
    out = tmp_path / "site"
    b.build(str(out), "")
    for url in urls:
        assert url.startswith(prefix)
        assert (out / url[len(prefix):]).is_file(), url


def test_update_details_url(tmp_path):
    import pytest

    um = pytest.importorskip("novaos.updates.main")
    assert um.update_details_url(str(tmp_path)).startswith("https://")      # not installed: online
    (tmp_path / "updates").mkdir()
    (tmp_path / "updates" / "aktuell.html").write_text("<html></html>")
    assert um.update_details_url(str(tmp_path)) == f"file://{tmp_path}/updates/aktuell.html"
