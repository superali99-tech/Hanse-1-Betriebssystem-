"""Render Nova Store pages offscreen and save screenshots (manual UI check).

Usage: QT_QPA_PLATFORM=offscreen python3 tests/ui/screenshot_store.py OUTDIR [--dark]
Requires a Flathub appstream catalog in /var/lib/flatpak (flatpak update --appstream).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "src"))
from PyQt6.QtCore import QTimer  # noqa: E402
from PyQt6.QtGui import QIcon, QPalette, QColor  # noqa: E402

from novaos.common.app import create_app  # noqa: E402
from novaos.common import style  # noqa: E402

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
app = create_app("org.novaos.Store", "Nova Store", "nova-store")
QIcon.setThemeName("breeze-dark" if "--dark" in sys.argv else "breeze")
if "--dark" in sys.argv:
    app.setStyle("Fusion")
    pal = QPalette()
    for role, col in [(QPalette.ColorRole.Window, "#1b1e2b"), (QPalette.ColorRole.WindowText, "#e6e8f0"),
                      (QPalette.ColorRole.Base, "#151826"), (QPalette.ColorRole.Text, "#e6e8f0"),
                      (QPalette.ColorRole.Button, "#262a3b"), (QPalette.ColorRole.ButtonText, "#e6e8f0"),
                      (QPalette.ColorRole.Highlight, "#7C5CFF"), (QPalette.ColorRole.HighlightedText, "#ffffff")]:
        pal.setColor(role, QColor(col))
    app.setPalette(pal)
    style.apply(app)

from novaos.store.controller import StoreController  # noqa: E402
from novaos.store.main_window import StoreWindow  # noqa: E402

c = StoreController()
w = StoreWindow(c)
w.resize(1280, 860)
w.show()
c.start()
suffix = "-dark" if "--dark" in sys.argv else ""
steps = []


def shot(name):
    w.grab().save(os.path.join(out, f"store-{name}{suffix}.png"))
    print("saved", name, flush=True)


def run_steps():
    if not steps:
        app.quit()
        return
    delay, fn = steps.pop(0)
    QTimer.singleShot(delay, lambda: (fn(), run_steps()))


def start():
    if not len(c.catalog):
        QTimer.singleShot(500, start)
        return
    steps.extend([
        (6000, lambda: shot("home")),
        (10, lambda: w.open_app("org.gimp.GIMP")),
        (9000, lambda: shot("details")),
        (10, lambda: w.details_page.scroll.verticalScrollBar().setValue(700)),
        (500, lambda: shot("details2")),
        (10, lambda: w.nav.setCurrentRow(2)),
        (1500, lambda: shot("category")),
        (10, lambda: w.search.setText("video editor")),
        (1500, lambda: shot("search")),
        (10, lambda: w.nav.setCurrentRow(w.nav.row(w.updates_item) - 1)),
        (1500, lambda: shot("installed")),
        (10, lambda: w.nav.setCurrentRow(w.nav.row(w.updates_item))),
        (4000, lambda: shot("updates")),
        (10, lambda: w.nav.setCurrentRow(w.nav.row(w.updates_item) + 1)),
        (800, lambda: shot("settings")),
    ])
    run_steps()


QTimer.singleShot(500, start)
QTimer.singleShot(120000, app.quit)
app.exec()
