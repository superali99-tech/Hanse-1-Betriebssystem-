"""Visual style for NovaOS apps.

The apps use the platform style (Breeze under Plasma) and the system colour
scheme, so light and dark mode work automatically. This module only adds
styling for NovaOS specific widgets (cards, chips, hero banners); colours are
taken from the active palette via ``palette(...)`` or derived from it.
"""

from __future__ import annotations

from PyQt6.QtCore import QEvent, QObject
from PyQt6.QtGui import QColor, QPalette
from PyQt6.QtWidgets import QApplication

ACCENT = "#7C5CFF"       # Nova violet
ACCENT_2 = "#3D7BFF"     # Nova blue
SUCCESS = "#22A06B"
WARNING = "#D9822B"
DANGER = "#E5484D"
RADIUS = 14


def is_dark(palette: QPalette | None = None) -> bool:
    palette = palette or QApplication.palette()
    return palette.color(QPalette.ColorRole.Window).lightness() < 128


def mix(c1: QColor, c2: QColor, ratio: float) -> QColor:
    r = c1.redF() * (1 - ratio) + c2.redF() * ratio
    g = c1.greenF() * (1 - ratio) + c2.greenF() * ratio
    b = c1.blueF() * (1 - ratio) + c2.blueF() * ratio
    return QColor.fromRgbF(r, g, b)


def card_color() -> QColor:
    pal = QApplication.palette()
    base = pal.color(QPalette.ColorRole.Base)
    text = pal.color(QPalette.ColorRole.Text)
    return mix(base, text, 0.04 if not is_dark(pal) else 0.06)


def stylesheet() -> str:
    """NovaOS glass design: soft glassy cards (light top edge), violet-blue gradients
    for the main actions, round lists and fields - like NovaOS Mobile."""
    pal = QApplication.palette()
    dark = is_dark(pal)
    window = pal.color(QPalette.ColorRole.Window)
    text = pal.color(QPalette.ColorRole.Text)
    card_c = card_color()
    card = card_c.name()
    # the "glass" edge: a little lighter at the top of every card
    top = mix(card_c, QColor("white"), 0.06 if dark else 0.55).name()
    border = mix(window, text, 0.12).name()
    edge = mix(window, text, 0.07 if dark else 0.05).name()
    hover = mix(card_c, QColor(ACCENT), 0.10).name()
    hover_top = mix(QColor(top), QColor(ACCENT), 0.08).name()
    muted = mix(text, window, 0.40).name()
    field = mix(window, text, 0.05 if dark else 0.03).name()
    acc_hi = QColor(ACCENT).lighter(112).name()
    acc2_hi = QColor(ACCENT_2).lighter(112).name()
    acc_lo = QColor(ACCENT).darker(110).name()
    acc2_lo = QColor(ACCENT_2).darker(110).name()
    return f"""
    QFrame#NovaCard {{
        background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 {top}, stop:1 {card});
        border: 1px solid {border};
        border-radius: {RADIUS + 4}px;
    }}
    QFrame#NovaCard:hover {{
        background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 {hover_top}, stop:1 {hover});
        border-color: {ACCENT};
    }}
    QFrame#NovaPanel {{
        background: qlineargradient(x1:0, y1:0, x2:0, y2:1, stop:0 {top}, stop:1 {card});
        border: 1px solid {edge};
        border-radius: {RADIUS + 4}px;
    }}
    QFrame#NovaHero {{
        border-radius: {RADIUS + 8}px;
        background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
            stop:0 {ACCENT}, stop:0.55 #5A6BFF, stop:1 {ACCENT_2});
    }}
    QFrame#NovaHero QLabel {{ color: white; background: transparent; }}
    QLabel#NovaTitle {{ font-size: 22pt; font-weight: 700; }}
    QLabel#NovaSubtitle {{ font-size: 13pt; font-weight: 600; }}
    QLabel#NovaMuted {{ color: {muted}; }}
    QLabel#NovaChip {{
        border: 1px solid {border};
        border-radius: 10px;
        padding: 2px 9px;
        color: {muted};
    }}
    QLabel#NovaBadgeOk {{
        background: {SUCCESS}; color: white; border-radius: 9px; padding: 1px 8px; font-weight: 600;
    }}
    QLabel#NovaBadgeWarn {{
        background: {WARNING}; color: white; border-radius: 9px; padding: 1px 8px; font-weight: 600;
    }}
    QLabel#NovaBadgeDanger {{
        background: {DANGER}; color: white; border-radius: 9px; padding: 1px 8px; font-weight: 600;
    }}
    QPushButton#NovaPrimary {{
        background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 {ACCENT}, stop:1 {ACCENT_2});
        color: white; border: none;
        border-radius: 12px; padding: 8px 20px; font-weight: 600;
    }}
    QPushButton#NovaPrimary:hover {{
        background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 {acc_hi}, stop:1 {acc2_hi});
    }}
    QPushButton#NovaPrimary:pressed {{
        background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 {acc_lo}, stop:1 {acc2_lo});
    }}
    QPushButton#NovaPrimary:disabled {{ background: {muted}; }}
    QPushButton#NovaDanger {{
        background: transparent; color: {DANGER}; border: 1px solid {DANGER};
        border-radius: 12px; padding: 7px 18px; font-weight: 600;
    }}
    QPushButton#NovaDanger:hover {{ background: {DANGER}; color: white; }}
    QListWidget#NovaSidebar {{
        background: transparent; border: none; outline: none; font-size: 11pt;
    }}
    QListWidget#NovaSidebar::item {{
        padding: 9px 12px; border-radius: 12px; margin: 2px 6px;
    }}
    QListWidget#NovaSidebar::item:selected {{
        background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 {ACCENT}, stop:1 {ACCENT_2});
        color: white;
    }}
    QListWidget#NovaSidebar::item:hover:!selected {{ background: {hover}; }}
    QListWidget#NovaList {{
        background: {field}; border: 1px solid {edge}; border-radius: {RADIUS + 4}px;
        outline: none; padding: 6px; font-size: 12pt;
    }}
    QListWidget#NovaList::item {{ padding: 10px 12px; border-radius: 12px; margin: 1px 0px; }}
    QListWidget#NovaList::item:selected {{
        background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 {ACCENT}, stop:1 {ACCENT_2});
        color: white;
    }}
    QListWidget#NovaList::item:hover:!selected {{ background: {hover}; }}
    QLineEdit#NovaSearch, QLineEdit#NovaField {{
        background: {field}; border: 1px solid {border}; border-radius: 14px; padding: 8px 14px; font-size: 11pt;
    }}
    QLineEdit#NovaSearch:focus, QLineEdit#NovaField:focus {{ border-color: {ACCENT}; }}
    QProgressBar {{
        border: none; border-radius: 4px; background: {border}; max-height: 8px; text-align: center;
    }}
    QProgressBar::chunk {{
        border-radius: 4px;
        background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 {ACCENT}, stop:1 {ACCENT_2});
    }}
    QScrollArea {{ background: transparent; border: none; }}
    QScrollArea > QWidget#qt_scrollarea_viewport > QWidget {{ background: transparent; }}
    """


class _PaletteWatcher(QObject):
    """Re-computes the stylesheet when the colour scheme changes at runtime.

    The stylesheet derives colours from the palette; without this, apps that
    are open while the user switches light/dark kept the old colours (found
    while recording the trailer: dark text on a dark background)."""

    def eventFilter(self, obj, event) -> bool:  # noqa: N802 - Qt API
        if obj is QApplication.instance() and event.type() == QEvent.Type.ApplicationPaletteChange:
            css = stylesheet()
            if css != obj.styleSheet():
                obj.setStyleSheet(css)
        return False


_watcher: _PaletteWatcher | None = None


def apply(app: QApplication) -> None:
    global _watcher
    app.setStyleSheet(stylesheet())
    if _watcher is None:
        _watcher = _PaletteWatcher(app)
        app.installEventFilter(_watcher)
