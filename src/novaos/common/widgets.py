"""Reusable widgets for the NovaOS apps."""

from __future__ import annotations

from PyQt6.QtCore import QPoint, QRect, QRectF, QSize, Qt, pyqtSignal
from PyQt6.QtGui import QColor, QFontMetrics, QPainter, QPainterPath, QPixmap
from PyQt6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QLayout,
    QSizePolicy,
    QVBoxLayout,
    QWidget,
    QWidgetItem,
)

from novaos.common import style


class FlowLayout(QLayout):
    """Layout that wraps its items like text (Qt's classic flow layout)."""

    def __init__(self, parent: QWidget | None = None, margin: int = 0, spacing: int = 12):
        super().__init__(parent)
        self._items: list[QWidgetItem] = []
        self.setContentsMargins(margin, margin, margin, margin)
        self._spacing = spacing

    def addItem(self, item):  # noqa: N802 (Qt API)
        self._items.append(item)

    def insertWidget(self, index: int, widget: QWidget) -> None:  # noqa: N802 (Qt naming)
        """Put a widget at a position (e.g. official web apps before the other results)."""
        self.addChildWidget(widget)
        self._items.insert(max(0, min(index, len(self._items))), QWidgetItem(widget))
        self.invalidate()

    def count(self):
        return len(self._items)

    def itemAt(self, index):  # noqa: N802
        return self._items[index] if 0 <= index < len(self._items) else None

    def takeAt(self, index):  # noqa: N802
        return self._items.pop(index) if 0 <= index < len(self._items) else None

    def expandingDirections(self):  # noqa: N802
        return Qt.Orientation(0)

    def hasHeightForWidth(self):  # noqa: N802
        return True

    def heightForWidth(self, width):  # noqa: N802
        return self._do_layout(QRect(0, 0, width, 0), test_only=True)

    def setGeometry(self, rect):  # noqa: N802
        super().setGeometry(rect)
        self._do_layout(rect, test_only=False)

    def sizeHint(self):  # noqa: N802
        return self.minimumSize()

    def minimumSize(self):  # noqa: N802
        size = QSize()
        for item in self._items:
            size = size.expandedTo(item.minimumSize())
        m = self.contentsMargins()
        return size + QSize(m.left() + m.right(), m.top() + m.bottom())

    def clear(self):
        while self._items:
            item = self._items.pop()
            if item.widget():
                item.widget().deleteLater()

    def _do_layout(self, rect: QRect, test_only: bool) -> int:
        m = self.contentsMargins()
        eff = rect.adjusted(m.left(), m.top(), -m.right(), -m.bottom())
        x, y, line_height = eff.x(), eff.y(), 0
        for item in self._items:
            hint = item.sizeHint()
            next_x = x + hint.width() + self._spacing
            if next_x - self._spacing > eff.right() + 1 and line_height > 0:
                x = eff.x()
                y += line_height + self._spacing
                next_x = x + hint.width() + self._spacing
                line_height = 0
            if not test_only:
                item.setGeometry(QRect(QPoint(x, y), hint))
            x = next_x
            line_height = max(line_height, hint.height())
        return y + line_height - rect.y() + m.bottom()


class Card(QFrame):
    """Rounded, clickable card container."""

    clicked = pyqtSignal()

    def __init__(self, parent: QWidget | None = None, clickable: bool = True):
        super().__init__(parent)
        self.setObjectName("NovaCard" if clickable else "NovaPanel")
        if clickable:
            self.setCursor(Qt.CursorShape.PointingHandCursor)
        self._clickable = clickable

    def mouseReleaseEvent(self, event):  # noqa: N802
        if self._clickable and event.button() == Qt.MouseButton.LeftButton and self.rect().contains(event.position().toPoint()):
            self.clicked.emit()
        super().mouseReleaseEvent(event)


class ElidedLabel(QLabel):
    """Single line label that elides overly long text with '…'."""

    def __init__(self, text: str = "", parent: QWidget | None = None):
        super().__init__(parent)
        self._full = text
        self.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)
        self.setText(text)

    def setText(self, text):  # noqa: N802
        self._full = text or ""
        self.setToolTip(self._full)
        self._update()

    def resizeEvent(self, event):  # noqa: N802
        super().resizeEvent(event)
        self._update()

    def _update(self):
        fm = QFontMetrics(self.font())
        super().setText(fm.elidedText(self._full, Qt.TextElideMode.ElideRight, max(10, self.width())))


class StarRating(QWidget):
    """Read-only 0–5 star display."""

    def __init__(self, rating: float = 0.0, size: int = 14, parent: QWidget | None = None):
        super().__init__(parent)
        self._rating = rating
        self._size = size
        self.setFixedSize(size * 5 + 8, size + 2)

    def set_rating(self, rating: float) -> None:
        self._rating = max(0.0, min(5.0, rating))
        self.update()

    def paintEvent(self, _event):  # noqa: N802
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        s = self._size
        empty = QColor(self.palette().color(self.foregroundRole()))
        empty.setAlphaF(0.25)
        full = QColor("#F5B301")
        for i in range(5):
            path = _star_path(QRectF(i * (s + 2), 1, s, s))
            p.fillPath(path, empty)
            fill = max(0.0, min(1.0, self._rating - i))
            if fill > 0:
                p.save()
                p.setClipRect(QRectF(i * (s + 2), 0, s * fill, s + 2))
                p.fillPath(path, full)
                p.restore()


def _star_path(r: QRectF) -> QPainterPath:
    import math

    cx, cy = r.center().x(), r.center().y()
    outer, inner = r.width() / 2, r.width() / 4.6
    path = QPainterPath()
    for k in range(10):
        radius = outer if k % 2 == 0 else inner
        angle = -math.pi / 2 + k * math.pi / 5
        pt = (cx + radius * math.cos(angle), cy + radius * math.sin(angle))
        if k == 0:
            path.moveTo(*pt)
        else:
            path.lineTo(*pt)
    path.closeSubpath()
    return path


def rounded_pixmap(pix: QPixmap, radius: int) -> QPixmap:
    if pix.isNull():
        return pix
    out = QPixmap(pix.size())
    out.setDevicePixelRatio(pix.devicePixelRatio())
    out.fill(Qt.GlobalColor.transparent)
    p = QPainter(out)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    path = QPainterPath()
    dpr = pix.devicePixelRatio()
    path.addRoundedRect(QRectF(0, 0, pix.width() / dpr, pix.height() / dpr), radius, radius)
    p.setClipPath(path)
    p.drawPixmap(0, 0, pix)
    p.end()
    return out


def label(text: str, kind: str | None = None, wrap: bool = False) -> QLabel:
    lbl = QLabel(text)
    if kind:
        lbl.setObjectName(kind)
    lbl.setWordWrap(wrap)
    return lbl


def hbox(*widgets, spacing: int = 8, margins=(0, 0, 0, 0)) -> QHBoxLayout:
    lay = QHBoxLayout()
    lay.setSpacing(spacing)
    lay.setContentsMargins(*margins)
    for w in widgets:
        if w is None:
            lay.addStretch(1)
        elif isinstance(w, QLayout):
            lay.addLayout(w)
        else:
            lay.addWidget(w)
    return lay


def vbox(*widgets, spacing: int = 8, margins=(0, 0, 0, 0)) -> QVBoxLayout:
    lay = QVBoxLayout()
    lay.setSpacing(spacing)
    lay.setContentsMargins(*margins)
    for w in widgets:
        if w is None:
            lay.addStretch(1)
        elif isinstance(w, QLayout):
            lay.addLayout(w)
        else:
            lay.addWidget(w)
    return lay


def human_size(num: float) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if abs(num) < 1024 or unit == "TB":
            return f"{num:.0f} {unit}" if unit in ("B", "KB") else f"{num:.1f} {unit}"
        num /= 1024
    return f"{num:.1f} TB"


__all__ = [
    "FlowLayout",
    "Card",
    "ElidedLabel",
    "StarRating",
    "rounded_pixmap",
    "label",
    "hbox",
    "vbox",
    "human_size",
    "style",
]
