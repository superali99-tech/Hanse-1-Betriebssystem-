"""App details page of Nova Store."""

from __future__ import annotations

import datetime
import html

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QDesktopServices, QIcon, QPixmap
from PyQt6.QtCore import QUrl
from PyQt6.QtWidgets import (
    QDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from novaos.common.i18n import LANG, _
from novaos.common.widgets import StarRating, human_size, rounded_pixmap
from novaos.store import permissions, worker
from novaos.store.appstream import AppInfo
from novaos.store.tiles import InfoRow, app_pixmap


class ScreenshotDialog(QDialog):
    def __init__(self, pix: QPixmap, caption: str, parent=None):
        super().__init__(parent)
        self.setWindowTitle(caption or _("Screenshot"))
        lay = QVBoxLayout(self)
        lbl = QLabel()
        screen = self.screen().availableGeometry() if self.screen() else None
        if screen and (pix.width() > screen.width() * 0.9 or pix.height() > screen.height() * 0.85):
            pix = pix.scaled(int(screen.width() * 0.9), int(screen.height() * 0.85),
                             Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        lbl.setPixmap(pix)
        lay.addWidget(lbl)


class ClickableLabel(QLabel):
    clicked = pyqtSignal()

    def mouseReleaseEvent(self, e):  # noqa: N802
        self.clicked.emit()
        super().mouseReleaseEvent(e)


class DetailsPage(QWidget):
    back = pyqtSignal()

    def __init__(self, controller, images, parent=None):
        super().__init__(parent)
        self.c = controller
        self.images = images
        self.app: AppInfo | None = None
        self._details = None
        self._perms: list = []

        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        outer.addWidget(self.scroll)
        self.body = QWidget()
        self.scroll.setWidget(self.body)
        self.lay = QVBoxLayout(self.body)
        self.lay.setContentsMargins(28, 18, 28, 28)
        self.lay.setSpacing(18)

        back = QPushButton(QIcon.fromTheme("go-previous"), _("Back"))
        back.setFlat(True)
        back.clicked.connect(self.back.emit)
        self.lay.addWidget(back, 0, Qt.AlignmentFlag.AlignLeft)

        # Header -----------------------------------------------------------
        header = QHBoxLayout()
        header.setSpacing(20)
        self.icon = QLabel()
        self.icon.setFixedSize(112, 112)
        header.addWidget(self.icon, 0, Qt.AlignmentFlag.AlignTop)
        titles = QVBoxLayout()
        titles.setSpacing(4)
        self.title = QLabel()
        self.title.setObjectName("NovaTitle")
        self.title.setWordWrap(True)
        self.developer = QLabel()
        self.developer.setObjectName("NovaMuted")
        self.summary = QLabel()
        self.summary.setWordWrap(True)
        rating_row = QHBoxLayout()
        self.stars = StarRating(0, 16)
        self.rating_text = QLabel()
        self.rating_text.setObjectName("NovaMuted")
        rating_row.addWidget(self.stars)
        rating_row.addWidget(self.rating_text)
        rating_row.addStretch(1)
        titles.addWidget(self.title)
        titles.addWidget(self.developer)
        titles.addWidget(self.summary)
        titles.addLayout(rating_row)
        header.addLayout(titles, 1)

        actions = QVBoxLayout()
        actions.setSpacing(8)
        self.btn_install = QPushButton(_("Install"))
        self.btn_install.setObjectName("NovaPrimary")
        self.btn_install.setMinimumWidth(170)
        self.btn_install.clicked.connect(self._install)
        self.btn_open = QPushButton(QIcon.fromTheme("media-playback-start"), _("Open"))
        self.btn_open.setObjectName("NovaPrimary")
        self.btn_open.clicked.connect(self._open)
        self.btn_update = QPushButton(QIcon.fromTheme("update-none"), _("Update"))
        self.btn_update.clicked.connect(lambda: self.c.update([self.app.flatpak_name]))
        self.btn_remove = QPushButton(_("Uninstall"))
        self.btn_remove.setObjectName("NovaDanger")
        self.btn_remove.clicked.connect(self._uninstall)
        self.progress = QProgressBar()
        self.progress.setRange(0, 100)
        self.progress.setTextVisible(False)
        self.progress.setFixedWidth(220)
        self.status = QLabel()
        self.status.setObjectName("NovaMuted")
        self.status.setWordWrap(True)
        self.status.setMaximumWidth(260)
        self.btn_cancel = QPushButton(QIcon.fromTheme("dialog-cancel"), _("Cancel"))
        self.btn_cancel.clicked.connect(lambda: self.c.cancel(self.app.flatpak_name))
        for w in (self.btn_install, self.btn_open, self.btn_update, self.btn_remove, self.progress, self.status, self.btn_cancel):
            actions.addWidget(w)
        actions.addStretch(1)
        header.addLayout(actions)
        self.lay.addLayout(header)

        # Screenshots ------------------------------------------------------
        self.shots_area = QScrollArea()
        self.shots_area.setWidgetResizable(True)
        self.shots_area.setFixedHeight(270)
        self.shots_area.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.shots_host = QWidget()
        self.shots_lay = QHBoxLayout(self.shots_host)
        self.shots_lay.setContentsMargins(0, 0, 0, 0)
        self.shots_lay.setSpacing(12)
        self.shots_area.setWidget(self.shots_host)
        self.lay.addWidget(self.shots_area)

        # Description + side panels ------------------------------------------
        columns = QHBoxLayout()
        columns.setSpacing(24)
        left = QVBoxLayout()
        left.setSpacing(12)
        self.description = QLabel()
        self.description.setWordWrap(True)
        self.description.setTextFormat(Qt.TextFormat.RichText)
        self.description.setOpenExternalLinks(True)
        self.description.setAlignment(Qt.AlignmentFlag.AlignTop)
        left.addWidget(self._heading(_("About this app")))
        left.addWidget(self.description)
        left.addWidget(self._heading(_("Ratings and reviews")))
        self.reviews_box = QVBoxLayout()
        self.reviews_box.setSpacing(10)
        left.addLayout(self.reviews_box)
        left.addStretch(1)
        columns.addLayout(left, 3)

        right = QVBoxLayout()
        right.setSpacing(12)
        self.info_panel = QFrame()
        self.info_panel.setObjectName("NovaPanel")
        self.info_lay = QVBoxLayout(self.info_panel)
        self.info_lay.setContentsMargins(16, 12, 16, 12)
        right.addWidget(self._heading(_("Information")))
        right.addWidget(self.info_panel)
        self.perm_panel = QFrame()
        self.perm_panel.setObjectName("NovaPanel")
        self.perm_lay = QVBoxLayout(self.perm_panel)
        self.perm_lay.setContentsMargins(16, 12, 16, 12)
        right.addWidget(self._heading(_("Permissions")))
        right.addWidget(self.perm_panel)
        right.addStretch(1)
        columns.addLayout(right, 2)
        self.lay.addLayout(columns)

        self.c.op_progress.connect(self._on_progress)
        self.c.op_finished.connect(self._on_finished)
        self.c.op_queued.connect(lambda key: self._refresh_actions() if self.app and key == self.app.flatpak_name else None)
        self.c.installed_changed.connect(self._refresh_actions)
        self.c.updates_changed.connect(lambda n: self._refresh_actions())

    @staticmethod
    def _heading(text: str) -> QLabel:
        lbl = QLabel(text)
        lbl.setObjectName("NovaSubtitle")
        return lbl

    @staticmethod
    def _clear(layout) -> None:
        while layout.count():
            item = layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
            elif item.layout():
                DetailsPage._clear(item.layout())

    # ----------------------------------------------------------------- content
    def show_app(self, app: AppInfo) -> None:
        self.app = app
        self._details = None
        self._perms = []
        self.scroll.verticalScrollBar().setValue(0)
        self.icon.setPixmap(app_pixmap(app, 112))
        self.title.setText(html.escape(app.name))
        dev = html.escape(app.developer or _("Unknown developer"))
        if app.verified:
            dev += "  <span style='color:#22A06B'>✔ " + _("Verified") + "</span>"
        self.developer.setText(dev)
        self.summary.setText(html.escape(app.summary))
        rating = self.c.ratings.get(app.id) if self.c.ratings_enabled else None
        if rating and rating.total:
            self.stars.set_rating(rating.average)
            self.stars.show()
            self.rating_text.setText(f"{rating.average:.1f} · " + _("{n} ratings").format(n=rating.total))
        else:
            self.stars.hide()
            self.rating_text.setText(_("No ratings yet") if self.c.ratings_enabled else "")

        self.description.setText(app.description or html.escape(app.summary))

        self._clear(self.shots_lay)
        if app.screenshots:
            self.shots_area.show()
            for shot in app.screenshots[:8]:
                lbl = ClickableLabel()
                lbl.setFixedSize(400, 250)
                lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
                lbl.setText(_("Loading screenshot…"))
                lbl.setObjectName("NovaMuted")
                lbl.setCursor(Qt.CursorShape.PointingHandCursor)
                lbl.setToolTip(shot.caption)
                self.shots_lay.addWidget(lbl)
                self.images.get(shot.thumbnail, lambda pix, l=lbl: self._set_shot(l, pix))
                lbl.clicked.connect(lambda s=shot: self.images.get(s.full, lambda pix, c=s.caption: ScreenshotDialog(pix, c, self).exec()))
            self.shots_lay.addStretch(1)
        else:
            self.shots_area.hide()

        self._fill_info()
        self._fill_permissions(loading=True)
        self._fill_reviews()
        self._refresh_actions()
        worker.run(self.c.backend.remote_details, app.ref, on_done=lambda d, a=app: self._details_loaded(a, d),
                   on_error=lambda e, a=app: self._details_failed(a, e))

    @staticmethod
    def _set_shot(lbl: QLabel, pix: QPixmap) -> None:
        if pix.isNull():
            lbl.setText(_("Screenshot not available"))
            return
        lbl.setPixmap(rounded_pixmap(pix.scaled(400, 250, Qt.AspectRatioMode.KeepAspectRatio,
                                                Qt.TransformationMode.SmoothTransformation), 10))

    def _fill_info(self) -> None:
        app = self.app
        self._clear(self.info_lay)
        rows = [(_("Version"), app.version or "–")]
        if app.release_timestamp:
            rows.append((_("Released"), datetime.date.fromtimestamp(app.release_timestamp).strftime("%d.%m.%Y")))
        rows.append((_("License"), app.license or "–"))
        if self._details:
            rows.append((_("Download size"), human_size(self._details.download_size) + " " + _("(without runtime)")))
            rows.append((_("Installed size"), human_size(self._details.installed_size)))
        rows.append((_("Runtime"), app.runtime.split("/")[0] + " " + app.runtime.split("/")[-1] if app.runtime else "–"))
        rows.append((_("Source"), "Flathub (Flatpak)"))
        if app.homepage:
            rows.append((_("Website"), f"<a href='{html.escape(app.homepage)}'>{html.escape(app.homepage)}</a>"))
        rows.append((_("App ID"), app.flatpak_name))
        for k, v in rows:
            self.info_lay.addWidget(InfoRow(k, v))
        if self._details and self._details.eol:
            warn = QLabel("⚠ " + _("This app is no longer maintained: ") + html.escape(self._details.eol))
            warn.setWordWrap(True)
            warn.setObjectName("NovaBadgeWarn")
            self.info_lay.addWidget(warn)

    def _fill_permissions(self, loading: bool = False, error: str = "") -> None:
        self._clear(self.perm_lay)
        if loading:
            self.perm_lay.addWidget(QLabel(_("Checking permissions…")))
            return
        if error:
            lbl = QLabel(_("Permissions could not be loaded: ") + error)
            lbl.setWordWrap(True)
            self.perm_lay.addWidget(lbl)
            return
        level, text = permissions.summary(self._perms)
        badge = QLabel(text)
        badge.setWordWrap(True)
        badge.setObjectName({0: "NovaBadgeOk", 1: "NovaBadgeWarn", 2: "NovaBadgeDanger"}[level])
        self.perm_lay.addWidget(badge)
        for p in self._perms[:14]:
            row = QHBoxLayout()
            icon = QLabel()
            icon.setPixmap(QIcon.fromTheme(p.icon, QIcon.fromTheme("security-medium")).pixmap(22, 22))
            row.addWidget(icon, 0, Qt.AlignmentFlag.AlignTop)
            mark = {0: "", 1: " ⚠", 2: " ⛔"}[p.level]
            txt = QLabel(f"<b>{html.escape(p.title)}</b>{mark}<br><span>{html.escape(p.detail)}</span>")
            txt.setWordWrap(True)
            row.addWidget(txt, 1)
            self.perm_lay.addLayout(row)
        if len(self._perms) > 14:
            more = QLabel(_("… and {n} more").format(n=len(self._perms) - 14))
            more.setObjectName("NovaMuted")
            self.perm_lay.addWidget(more)
        hint = QLabel(_("Permissions can be changed later in Settings → Applications → Flatpak permissions."))
        hint.setObjectName("NovaMuted")
        hint.setWordWrap(True)
        self.perm_lay.addWidget(hint)

    def _details_loaded(self, app: AppInfo, details) -> None:
        if app is not self.app:
            return
        self._details = details
        self._perms = permissions.analyze(details.metadata)
        self._fill_info()
        self._fill_permissions()

    def _details_failed(self, app: AppInfo, exc: Exception) -> None:
        if app is not self.app:
            return
        from novaos.store.flatpak_backend import friendly_error

        self._fill_permissions(error=friendly_error(exc))

    def _fill_reviews(self) -> None:
        self._clear(self.reviews_box)
        if not self.c.ratings_enabled:
            lbl = QLabel(_("Ratings are disabled in the store settings."))
            lbl.setObjectName("NovaMuted")
            self.reviews_box.addWidget(lbl)
            return
        loading = QLabel(_("Loading reviews from the Open Desktop Ratings Service…"))
        loading.setObjectName("NovaMuted")
        self.reviews_box.addWidget(loading)
        locale = {"de": "de_DE"}.get(LANG, "en_US")
        app = self.app
        worker.run(self.c.ratings.fetch_reviews, app.id, locale, app.version,
                   on_done=lambda r, a=app: self._reviews_loaded(a, r),
                   on_error=lambda e, a=app: self._reviews_loaded(a, None))

    def _reviews_loaded(self, app: AppInfo, reviews) -> None:
        if app is not self.app:
            return
        self._clear(self.reviews_box)
        if reviews is None:
            lbl = QLabel(_("Reviews could not be loaded (offline?)."))
            lbl.setObjectName("NovaMuted")
            self.reviews_box.addWidget(lbl)
            return
        if not reviews:
            lbl = QLabel(_("There are no reviews for this app yet."))
            lbl.setObjectName("NovaMuted")
            self.reviews_box.addWidget(lbl)
            return
        for r in reviews[:8]:
            card = QFrame()
            card.setObjectName("NovaPanel")
            lay = QVBoxLayout(card)
            lay.setContentsMargins(14, 10, 14, 10)
            top = QHBoxLayout()
            top.addWidget(StarRating(r.rating, 12))
            head = QLabel(f"<b>{html.escape(r.summary)}</b>")
            head.setWordWrap(True)
            top.addWidget(head, 1)
            lay.addLayout(top)
            body = QLabel(html.escape(r.description).replace("\n", "<br>"))
            body.setWordWrap(True)
            lay.addWidget(body)
            meta = QLabel(" · ".join(x for x in (
                r.user or _("Anonymous"),
                datetime.date.fromtimestamp(r.date).strftime("%d.%m.%Y") if r.date else "",
                (_("Version") + " " + r.version) if r.version else "",
                r.distro) if x))
            meta.setObjectName("NovaMuted")
            lay.addWidget(meta)
            self.reviews_box.addWidget(card)
        src = QLabel(_("Source: Open Desktop Ratings Service (odrs.gnome.org)"))
        src.setObjectName("NovaMuted")
        self.reviews_box.addWidget(src)

    # ----------------------------------------------------------------- actions
    def _refresh_actions(self) -> None:
        if not self.app:
            return
        name = self.app.flatpak_name
        op = self.c.operation_for(name)
        installed = self.c.is_installed(name)
        busy = op is not None
        self.btn_install.setVisible(not installed and not busy)
        self.btn_open.setVisible(installed and not busy)
        self.btn_remove.setVisible(installed and not busy)
        self.btn_update.setVisible(installed and not busy and self.c.has_update(name))
        self.progress.setVisible(busy)
        self.btn_cancel.setVisible(busy)
        self.status.setVisible(busy)
        if busy:
            self.progress.setValue(op.progress)
            self.status.setText(f"{op.status} – {op.progress} %" if op.status else _("Waiting…"))

    def _install(self) -> None:
        app = self.app
        high = [p for p in self._perms if p.level == permissions.HIGH]
        if high:
            text = _("“{name}” requests extended permissions:").format(name=app.name) + "<ul>"
            text += "".join(f"<li><b>{html.escape(p.title)}</b>: {html.escape(p.detail)}</li>" for p in high)
            text += "</ul>" + _("Only install apps from developers you trust. Install anyway?")
            box = QMessageBox(QMessageBox.Icon.Warning, _("Check permissions"), text,
                              QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No, self)
            box.setTextFormat(Qt.TextFormat.RichText)
            box.button(QMessageBox.StandardButton.Yes).setText(_("Install"))
            box.button(QMessageBox.StandardButton.No).setText(_("Cancel"))
            if box.exec() != QMessageBox.StandardButton.Yes:
                return
        self.c.install(app)
        self._refresh_actions()

    def _uninstall(self) -> None:
        app = self.app
        res = QMessageBox.question(
            self, _("Uninstall"),
            _("Do you really want to uninstall “{name}”? Your personal data of this app is kept in ~/.var/app.").format(name=app.name),
        )
        if res == QMessageBox.StandardButton.Yes:
            self.c.uninstall(app.flatpak_name)
            self._refresh_actions()

    def _open(self) -> None:
        inst = self.c.installed.get(self.app.flatpak_name)
        self.c.backend.launch(self.app.flatpak_name, inst.scope if inst else "system")

    def _on_progress(self, key: str, pct: int, text: str) -> None:
        if self.app and key in (self.app.flatpak_name, "__all__"):
            self.progress.setValue(pct)
            self.status.setText(f"{text} – {pct} %")
            self._refresh_actions()

    def _on_finished(self, key: str, ok: bool, message: str) -> None:
        if not self.app or key != self.app.flatpak_name:
            return
        self._refresh_actions()
        if not ok:
            QMessageBox.warning(self, _("Operation failed"), message)


def open_url(url: str) -> None:
    QDesktopServices.openUrl(QUrl(url))
