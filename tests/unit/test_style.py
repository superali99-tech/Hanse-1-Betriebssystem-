"""Nova apps follow a light/dark switch while they are running."""
import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PyQt6.QtGui import QColor, QPalette  # noqa: E402
from PyQt6.QtWidgets import QApplication  # noqa: E402

from novaos.common import style  # noqa: E402


def _palette(window: str, text: str, base: str) -> QPalette:
    pal = QPalette()
    for group in (QPalette.ColorGroup.Active, QPalette.ColorGroup.Inactive, QPalette.ColorGroup.Disabled):
        pal.setColor(group, QPalette.ColorRole.Window, QColor(window))
        pal.setColor(group, QPalette.ColorRole.WindowText, QColor(text))
        pal.setColor(group, QPalette.ColorRole.Text, QColor(text))
        pal.setColor(group, QPalette.ColorRole.Base, QColor(base))
    return pal


def test_stylesheet_follows_palette_change():
    app = QApplication.instance() or QApplication([])
    app.setPalette(_palette("#f2f3f8", "#1a1b2e", "#ffffff"))
    style.apply(app)
    light = app.styleSheet()
    assert not style.is_dark()
    app.setPalette(_palette("#1b1e2b", "#eef0ff", "#141722"))
    app.processEvents()
    dark = app.styleSheet()
    assert style.is_dark()
    assert dark != light and style.card_color().name() in dark
