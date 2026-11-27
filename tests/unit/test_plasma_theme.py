"""The NovaOS Plasma theme (round dock): every element Plasma 6.3 looks up is there."""

import configparser
import json
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
THEME = os.path.join(ROOT, "packages", "novaos-desktop", "root", "usr", "share", "plasma", "desktoptheme", "novaos")
PARTS = ("topleft", "top", "topright", "left", "center", "right", "bottomleft", "bottom", "bottomright")
SIDES = ("top", "bottom", "left", "right")


def ids(rel):
    with open(os.path.join(THEME, rel), encoding="utf-8") as fh:
        return set(re.findall(r'\sid="([^"]+)"', fh.read()))


def test_metadata_and_fallback():
    with open(os.path.join(THEME, "metadata.json"), encoding="utf-8") as fh:
        meta = json.load(fh)
    assert meta["KPlugin"]["Id"] == "novaos" == os.path.basename(THEME)
    cfg = configparser.ConfigParser()
    cfg.read(os.path.join(THEME, "plasmarc"))
    # libplasma reads FallbackTheme, KSvg FallbackImageSet: everything else comes from Breeze
    assert cfg["Settings"]["FallbackTheme"] == "default" and cfg["Settings"]["FallbackImageSet"] == "default"


def test_panel_background_elements():
    for rel in ("widgets/panel-background.svg", "solid/widgets/panel-background.svg"):
        have = ids(rel)
        need = {p + part for p in ("", "mask-", "shadow-") for part in PARTS}
        need |= {f"hint-{s}-margin" for s in SIDES} | {f"shadow-hint-{s}-margin" for s in SIDES}
        need |= {"thick-center"} | {f"thick-hint-{s}-margin" for s in SIDES}
        assert need <= have, sorted(need - have)


def test_task_elements():
    have = ids("widgets/tasks.svg")
    for state in ("normal", "focus", "hover", "attention", "minimized", "progress"):
        assert {f"{state}-{part}" for part in PARTS} <= have, state
    assert {f"normal-hint-{s}-margin" for s in SIDES} <= have
    assert {f"group-expander-{s}" for s in SIDES} <= have


def test_theme_is_used():
    d = os.path.join(ROOT, "packages", "novaos-desktop", "root")
    for rel in ("etc/xdg/plasmarc", "usr/share/plasma/look-and-feel/org.novaos.desktop/contents/defaults",
                "usr/share/plasma/look-and-feel/org.novaos.desktop.dark/contents/defaults"):
        with open(os.path.join(d, rel), encoding="utf-8") as fh:
            assert "name=novaos" in fh.read(), rel


def test_generated_files_are_current():
    import importlib.util

    spec = importlib.util.spec_from_file_location("gen", os.path.join(ROOT, "artwork", "generate_plasma_theme.py"))
    gen = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gen)
    with open(os.path.join(THEME, "widgets", "tasks.svg"), encoding="utf-8") as fh:
        assert fh.read() == gen.tasks()
    with open(os.path.join(THEME, "widgets", "panel-background.svg"), encoding="utf-8") as fh:
        assert fh.read() == gen.panel(opaque=False)
