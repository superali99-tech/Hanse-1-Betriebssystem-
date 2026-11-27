"""Small building blocks of the Nova Store UI (tiles, rows, sections)."""

from __future__ import annotations

from PyQt6.QtCore import QSize, Qt, pyqtSignal
from PyQt6.QtGui import QIcon, QPixmap
from PyQt6.QtWidgets import QFrame, QHBoxLayout, QLabel, QPushButton, QSizePolicy, QVBoxLayout, QWidget

from novaos.common.i18n import _
from novaos.common.widgets import Card, ElidedLabel, FlowLayout, StarRating, human_size
from novaos.store.appstream import AppInfo

ICON_FALLBACK = "application-x-executable"


def app_pixmap(app: AppInfo | None, size: int, name: str = "") -> QPixmap:
    """Icon of a catalog app (cached PNG) or of an installed app (theme)."""
    if app and app.icon_path:
        pix = QPixmap(app.icon_path)
        if not pix.isNull():
            return pix.scaled(size, size, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
    icon = QIcon.fromTheme(name or (app.flatpak_name if app else ""))
    if icon.isNull():
        icon = QIcon.fromTheme(ICON_FALLBACK)
    return icon.pixmap(QSize(size, size))


class AppTile(Card):
    """Card with icon, name, summary and rating – used in grids."""

    open_app = pyqtSignal(str)

    WIDTH, HEIGHT = 300, 104

    def __init__(self, app: AppInfo, rating=None, installed: bool = False, parent: QWidget | None = None):
        super().__init__(parent)
        self.app = app
        self.setFixedSize(self.WIDTH, self.HEIGHT)
        lay = QHBoxLayout(self)
        lay.setContentsMargins(14, 12, 14, 12)
        lay.setSpacing(12)
        icon = QLabel()
        icon.setFixedSize(60, 60)
        icon.setPixmap(app_pixmap(app, 60))
        icon.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lay.addWidget(icon, 0, Qt.AlignmentFlag.AlignTop)

        text = QVBoxLayout()
        text.setSpacing(2)
        name = ElidedLabel(app.name)
        f = name.font()
        f.setBold(True)
        f.setPointSizeF(f.pointSizeF() * 1.08)
        name.setFont(f)
        text.addWidget(name)
        summary = QLabel(app.summary)
        summary.setObjectName("NovaMuted")
        summary.setWordWrap(True)
        summary.setMaximumHeight(36)
        summary.setAlignment(Qt.AlignmentFlag.AlignTop | Qt.AlignmentFlag.AlignLeft)
        summary.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)
        text.addWidget(summary, 1)
        bottom = QHBoxLayout()
        bottom.setSpacing(6)
        if rating and rating.total:
            stars = StarRating(rating.average, 11)
            bottom.addWidget(stars)
            cnt = QLabel(f"({rating.total})")
            cnt.setObjectName("NovaMuted")
            bottom.addWidget(cnt)
        bottom.addStretch(1)
        if installed:
            badge = QLabel(_("Installed"))
            badge.setObjectName("NovaBadgeOk")
            bottom.addWidget(badge)
        elif app.verified:
            badge = QLabel("✔ " + _("Verified"))
            badge.setObjectName("NovaChip")
            badge.setToolTip(_("The developer has been verified by Flathub"))
            bottom.addWidget(badge)
        text.addLayout(bottom)
        lay.addLayout(text, 1)
        self.clicked.connect(lambda: self.open_app.emit(app.id))


class Section(QWidget):
    """Titled section containing a flow layout of tiles."""

    def __init__(self, title: str, subtitle: str = "", parent: QWidget | None = None):
        super().__init__(parent)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 8, 0, 8)
        lay.setSpacing(8)
        t = QLabel(title)
        t.setObjectName("NovaSubtitle")
        lay.addWidget(t)
        if subtitle:
            s = QLabel(subtitle)
            s.setObjectName("NovaMuted")
            lay.addWidget(s)
        self.flow_host = QWidget()
        self.flow = FlowLayout(self.flow_host, spacing=14)
        lay.addWidget(self.flow_host)

    def add(self, widget: QWidget) -> None:
        self.flow.addWidget(widget)

    def clear(self) -> None:
        self.flow.clear()


class InfoRow(QWidget):
    def __init__(self, key: str, value: str, parent: QWidget | None = None):
        super().__init__(parent)
        lay = QHBoxLayout(self)
        lay.setContentsMargins(0, 2, 0, 2)
        k = QLabel(key)
        k.setObjectName("NovaMuted")
        k.setMinimumWidth(150)
        v = QLabel(value)
        v.setTextInteractionFlags(Qt.TextInteractionFlag.TextBrowserInteraction)
        v.setOpenExternalLinks(True)
        v.setWordWrap(True)
        lay.addWidget(k, 0, Qt.AlignmentFlag.AlignTop)
        lay.addWidget(v, 1)


class InstalledRow(QFrame):
    """Row in the 'Installed' and 'Updates' pages."""

    open_details = pyqtSignal(str)

    def __init__(self, pix: QPixmap, title: str, subtitle: str, size: int, parent: QWidget | None = None):
        super().__init__(parent)
        self.setObjectName("NovaPanel")
        lay = QHBoxLayout(self)
        lay.setContentsMargins(12, 8, 12, 8)
        lay.setSpacing(12)
        icon = QLabel()
        icon.setPixmap(pix)
        icon.setFixedSize(48, 48)
        lay.addWidget(icon)
        text = QVBoxLayout()
        text.setSpacing(0)
        t = QLabel(f"<b>{title}</b>")
        text.addWidget(t)
        s = QLabel(subtitle)
        s.setObjectName("NovaMuted")
        self.subtitle = s
        text.addWidget(s)
        lay.addLayout(text, 1)
        if size:
            sz = QLabel(human_size(size))
            sz.setObjectName("NovaMuted")
            lay.addWidget(sz)
        self.buttons = QHBoxLayout()
        self.buttons.setSpacing(6)
        lay.addLayout(self.buttons)

    def add_button(self, text: str, object_name: str = "", icon: str = "") -> QPushButton:
        btn = QPushButton(text)
        if object_name:
            btn.setObjectName(object_name)
        if icon:
            btn.setIcon(QIcon.fromTheme(icon))
        self.buttons.addWidget(btn)
        return btn


class WebAppTile(Card):
    """A Nova Store web app (Claude, ChatGPT, WhatsApp): "Add" / "Open" + "Remove"."""

    WIDTH, HEIGHT = 300, 120

    def __init__(self, app, german: bool, parent: QWidget | None = None):
        super().__init__(parent, clickable=False)
        from novaos.store import webapps
        self.app, self.webapps = app, webapps
        self.setFixedSize(self.WIDTH, self.HEIGHT)
        lay = QHBoxLayout(self)
        lay.setContentsMargins(14, 12, 14, 12)
        lay.setSpacing(12)
        icon = QLabel()
        icon.setFixedSize(60, 60)
        icon.setPixmap(QIcon.fromTheme(app.icon, QIcon.fromTheme("internet-web-browser")).pixmap(60, 60))
        lay.addWidget(icon, 0, Qt.AlignmentFlag.AlignTop)
        text = QVBoxLayout()
        text.setSpacing(2)
        name = QLabel(app.name)
        f = name.font()
        f.setBold(True)
        f.setPointSizeF(f.pointSizeF() * 1.08)
        name.setFont(f)
        text.addWidget(name)
        summary = QLabel(app.summary(german))
        summary.setObjectName("NovaMuted")
        summary.setWordWrap(True)
        summary.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)
        text.addWidget(summary, 1)
        row = QHBoxLayout()
        row.setSpacing(6)
        self.btn = QPushButton()
        self.btn.setObjectName("NovaPrimary")
        self.btn.clicked.connect(self._main)
        self.remove = QPushButton(_("Remove"))
        self.remove.clicked.connect(self._remove)
        row.addWidget(self.btn)
        row.addWidget(self.remove)
        row.addStretch(1)
        text.addLayout(row)
        lay.addLayout(text, 1)
        self._update()

    def _update(self) -> None:
        on = self.webapps.installed(self.app)
        self.btn.setText(_("Open") if on else _("Add"))
        self.remove.setVisible(on)

    def _main(self) -> None:
        if not self.webapps.installed(self.app):
            self.webapps.install(self.app)
            self._update()
        self.webapps.launch(self.app)

    def _remove(self) -> None:
        self.webapps.uninstall(self.app)
        self._update()
