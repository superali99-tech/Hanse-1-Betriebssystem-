"""Visual style for NovaOS apps.

The apps use the platform style (Breeze under Plasma) and the system colour
scheme, so light and dark mode work automatically. This module only adds
styling for NovaOS specific widgets (cards, chips, hero banners); colours are
taken from the active palette via ``palette(...)`` or derived from it.
"""

from __future__ import annotations

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
    pal = QApplication.palette()
    card = card_color().name()
    border = mix(pal.color(QPalette.ColorRole.Window), pal.color(QPalette.ColorRole.Text), 0.12).name()
    hover = mix(QColor(card), QColor(ACCENT), 0.10).name()
    muted = mix(pal.color(QPalette.ColorRole.Text), pal.color(QPalette.ColorRole.Window), 0.40).name()
    return f"""
    QFrame#NovaCard {{
        background: {card};
        border: 1px solid {border};
        border-radius: {RADIUS}px;
    }}
    QFrame#NovaCard:hover {{
        background: {hover};
        border-color: {ACCENT};
    }}
    QFrame#NovaPanel {{
        background: {card};
        border: 1px solid {border};
        border-radius: {RADIUS}px;
    }}
    QFrame#NovaHero {{
        border-radius: {RADIUS + 4}px;
        background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
            stop:0 {ACCENT}, stop:1 {ACCENT_2});
    }}
    QFrame#NovaHero QLabel {{ color: white; background: transparent; }}
    QLabel#NovaTitle {{ font-size: 22pt; font-weight: 700; }}
    QLabel#NovaSubtitle {{ font-size: 13pt; font-weight: 600; }}
    QLabel#NovaMuted {{ color: {muted}; }}
    QLabel#NovaChip {{
        border: 1px solid {border};
        border-radius: 9px;
        padding: 2px 8px;
        color: {muted};
    }}
    QLabel#NovaBadgeOk {{
        background: {SUCCESS}; color: white; border-radius: 8px; padding: 1px 7px; font-weight: 600;
    }}
    QLabel#NovaBadgeWarn {{
        background: {WARNING}; color: white; border-radius: 8px; padding: 1px 7px; font-weight: 600;
    }}
    QLabel#NovaBadgeDanger {{
        background: {DANGER}; color: white; border-radius: 8px; padding: 1px 7px; font-weight: 600;
    }}
    QPushButton#NovaPrimary {{
        background: {ACCENT}; color: white; border: none;
        border-radius: 10px; padding: 7px 18px; font-weight: 600;
    }}
    QPushButton#NovaPrimary:hover {{ background: {QColor(ACCENT).lighter(112).name()}; }}
    QPushButton#NovaPrimary:disabled {{ background: {muted}; }}
    QPushButton#NovaDanger {{
        background: transparent; color: {DANGER}; border: 1px solid {DANGER};
        border-radius: 10px; padding: 6px 16px; font-weight: 600;
    }}
    QPushButton#NovaDanger:hover {{ background: {DANGER}; color: white; }}
    QListWidget#NovaSidebar {{
        background: transparent; border: none; outline: none; font-size: 11pt;
    }}
    QListWidget#NovaSidebar::item {{
        padding: 8px 10px; border-radius: 9px; margin: 1px 4px;
    }}
    QListWidget#NovaSidebar::item:selected {{
        background: {ACCENT}; color: white;
    }}
    QListWidget#NovaSidebar::item:hover:!selected {{ background: {hover}; }}
    QLineEdit#NovaSearch {{
        border: 1px solid {border}; border-radius: 12px; padding: 7px 12px; font-size: 11pt;
    }}
    QLineEdit#NovaSearch:focus {{ border-color: {ACCENT}; }}
    QProgressBar {{
        border: none; border-radius: 4px; background: {border}; max-height: 8px; text-align: center;
    }}
    QProgressBar::chunk {{ border-radius: 4px; background: {ACCENT}; }}
    QScrollArea {{ background: transparent; border: none; }}
    QScrollArea > QWidget > QWidget {{ background: transparent; }}
    """


def apply(app: QApplication) -> None:
    app.setStyleSheet(stylesheet())
