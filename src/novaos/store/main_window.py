"""Main window of Nova Store."""

from __future__ import annotations

import datetime
import os

from PyQt6.QtCore import QProcess, QSize, Qt, QTimer
from PyQt6.QtGui import QIcon, QKeySequence, QShortcut
from PyQt6.QtWidgets import (
    QButtonGroup,
    QCheckBox,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QRadioButton,
    QScrollArea,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from novaos.common.i18n import _
from novaos.store.appstream import AppInfo
from novaos.store.details import DetailsPage
from novaos.store.images import ImageLoader
from novaos.common.sysinfo import edition
from novaos.store import system_apps, webapps, worker
from novaos.updates import apt as sysapt
from novaos.store.tiles import AppTile, InstalledRow, Section, SystemAppTile, WebAppTile, app_pixmap

CATEGORY_ITEMS = [
    ("games", _("Games"), "applications-games"),
    ("office", _("Office"), "applications-office"),
    ("internet", _("Internet"), "applications-internet"),
    ("music", _("Music"), "applications-multimedia"),
    ("video", _("Video"), "video-x-generic"),
    ("graphics", _("Graphics & Photo"), "applications-graphics"),
    ("education", _("Education"), "applications-education"),
    ("development", _("Development"), "applications-development"),
    ("system", _("System & Tools"), "applications-utilities"),
]

PAGE_SIZE = 48


def _german() -> bool:
    import os
    lang = os.environ.get("LANGUAGE") or os.environ.get("LC_ALL") or os.environ.get("LC_MESSAGES") or os.environ.get("LANG") or ""
    return lang.startswith("de")


def scroll_page() -> tuple[QScrollArea, QVBoxLayout]:
    area = QScrollArea()
    area.setWidgetResizable(True)
    host = QWidget()
    lay = QVBoxLayout(host)
    lay.setContentsMargins(28, 20, 28, 28)
    lay.setSpacing(14)
    area.setWidget(host)
    return area, lay


class StoreWindow(QMainWindow):
    def __init__(self, controller):
        super().__init__()
        self.c = controller
        self.images = ImageLoader(self)
        self.setWindowTitle(_("Nova Store"))
        self.resize(1280, 860)
        self.setMinimumSize(QSize(900, 600))
        self._history: list[int] = []
        self._list_apps: list[AppInfo] = []
        self._list_shown = 0

        central = QWidget()
        self.setCentralWidget(central)
        root = QHBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        # Sidebar -------------------------------------------------------------
        side = QFrame()
        side.setFixedWidth(240)
        side_lay = QVBoxLayout(side)
        side_lay.setContentsMargins(12, 16, 8, 12)
        side_lay.setSpacing(10)
        brand = QLabel(f"<span style='font-size:16pt; font-weight:700'>Nova</span>"
                       f"<span style='font-size:16pt; font-weight:300'> Store</span>")
        brand.setContentsMargins(10, 0, 0, 6)
        side_lay.addWidget(brand)
        self.nav = QListWidget()
        self.nav.setObjectName("NovaSidebar")
        self.nav.setIconSize(QSize(20, 20))
        side_lay.addWidget(self.nav, 1)
        root.addWidget(side)

        def add_nav(key, text, icon, selectable=True):
            item = QListWidgetItem(QIcon.fromTheme(icon), text)
            item.setData(Qt.ItemDataRole.UserRole, key)
            if not selectable:
                item.setFlags(Qt.ItemFlag.NoItemFlags)
            self.nav.addItem(item)
            return item

        add_nav("home", _("Discover"), "go-home")
        add_nav(None, _("Categories"), "", selectable=False).setForeground(self.palette().placeholderText())
        for key, text, icon in CATEGORY_ITEMS:
            add_nav("cat:" + key, text, icon)
        add_nav(None, "", "", selectable=False)
        add_nav("installed", _("Installed"), "drive-harddisk")
        self.updates_item = add_nav("updates", _("Updates"), "system-software-update")
        add_nav("settings", _("Settings"), "configure")
        self.nav.currentItemChanged.connect(self._nav_changed)

        # Content -------------------------------------------------------------
        content = QVBoxLayout()
        content.setContentsMargins(0, 12, 0, 0)
        content.setSpacing(0)
        top = QHBoxLayout()
        top.setContentsMargins(28, 0, 28, 6)
        self.search = QLineEdit()
        self.search.setObjectName("NovaSearch")
        self.search.setPlaceholderText(_("Search apps on Flathub…"))
        self.search.setClearButtonEnabled(True)
        self.search.addAction(QIcon.fromTheme("search"), QLineEdit.ActionPosition.LeadingPosition)
        self.search.setMaximumWidth(640)
        top.addWidget(self.search, 1)
        top.addStretch(0)
        self.queue_label = QLabel()
        self.queue_label.setObjectName("NovaMuted")
        self.queue_progress = QProgressBar()
        self.queue_progress.setFixedWidth(160)
        self.queue_progress.setTextVisible(False)
        self.queue_label.hide()
        self.queue_progress.hide()
        top.addWidget(self.queue_label)
        top.addWidget(self.queue_progress)
        content.addLayout(top)
        self.stack = QStackedWidget()
        content.addWidget(self.stack, 1)
        root.addLayout(content, 1)

        self._search_timer = QTimer(self)
        self._search_timer.setSingleShot(True)
        self._search_timer.setInterval(250)
        self._search_timer.timeout.connect(self._do_search)
        self.search.textChanged.connect(lambda: self._search_timer.start())
        QShortcut(QKeySequence("Ctrl+F"), self, activated=self.search.setFocus)
        QShortcut(QKeySequence("Escape"), self, activated=self._go_back)

        self._build_pages()

        c = self.c
        c.catalog_loading.connect(self._show_loading)
        c.catalog_ready.connect(self._catalog_ready)
        c.catalog_failed.connect(self._catalog_failed)
        c.installed_changed.connect(self._installed_changed)
        c.updates_changed.connect(self._updates_changed)
        c.ratings_ready.connect(self._refresh_home)
        c.op_progress.connect(self._queue_progress)
        c.op_finished.connect(self._op_finished)
        c.op_queued.connect(lambda k: self._queue_progress(k, 0, _("Waiting…")))
        # NovaOS apps that are system packages (Nova Browser): their updates appear in "Updates" too
        self.sys_updates: list = []
        self.sys_versions: dict = {}
        self.sys_busy: str | None = None
        self.sys_proc: QProcess | None = None
        QTimer.singleShot(1500, self._load_system_updates)

    # ------------------------------------------------------------------ pages
    def _build_pages(self) -> None:
        # Loading / error page
        self.loading_page = QWidget()
        ll = QVBoxLayout(self.loading_page)
        ll.addStretch(1)
        self.loading_label = QLabel(_("Loading app catalog…"))
        self.loading_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.loading_label.setWordWrap(True)
        self.loading_bar = QProgressBar()
        self.loading_bar.setRange(0, 0)
        self.loading_bar.setFixedWidth(260)
        self.retry_btn = QPushButton(QIcon.fromTheme("view-refresh"), _("Try again"))
        self.retry_btn.clicked.connect(lambda: self.c.start(force_refresh=True))
        self.retry_btn.hide()
        ll.addWidget(self.loading_label)
        ll.addWidget(self.loading_bar, 0, Qt.AlignmentFlag.AlignCenter)
        ll.addWidget(self.retry_btn, 0, Qt.AlignmentFlag.AlignCenter)
        ll.addStretch(2)
        self.stack.addWidget(self.loading_page)

        # Home
        self.home_page, self.home_lay = scroll_page()
        self.stack.addWidget(self.home_page)

        # Generic list page (category + search)
        self.list_page, self.list_lay = scroll_page()
        self.list_title = QLabel()
        self.list_title.setObjectName("NovaTitle")
        self.list_sub = QLabel()
        self.list_sub.setObjectName("NovaMuted")
        self.list_section = Section("")
        self.list_section.layout().itemAt(0).widget().hide()
        self.more_btn = QPushButton(_("Show more"))
        self.more_btn.clicked.connect(self._show_more)
        self.list_lay.addWidget(self.list_title)
        self.list_lay.addWidget(self.list_sub)
        self.list_lay.addWidget(self.list_section)
        self.list_lay.addWidget(self.more_btn, 0, Qt.AlignmentFlag.AlignHCenter)
        self.list_lay.addStretch(1)
        self.stack.addWidget(self.list_page)

        # Details
        self.details_page = DetailsPage(self.c, self.images)
        self.details_page.back.connect(self._go_back)
        self.stack.addWidget(self.details_page)

        # Installed
        self.installed_page, self.installed_lay = scroll_page()
        self.stack.addWidget(self.installed_page)

        # Updates
        self.updates_page, self.updates_lay = scroll_page()
        self.stack.addWidget(self.updates_page)

        # Settings
        self.settings_page, sl = scroll_page()
        self._build_settings(sl)
        self.stack.addWidget(self.settings_page)

    def _build_settings(self, lay: QVBoxLayout) -> None:
        title = QLabel(_("Settings"))
        title.setObjectName("NovaTitle")
        lay.addWidget(title)

        def panel(heading: str) -> QVBoxLayout:
            h = QLabel(heading)
            h.setObjectName("NovaSubtitle")
            lay.addWidget(h)
            f = QFrame()
            f.setObjectName("NovaPanel")
            v = QVBoxLayout(f)
            v.setContentsMargins(16, 12, 16, 12)
            lay.addWidget(f)
            return v

        p = panel(_("Installation"))
        self.rb_system = QRadioButton(_("Install for all users (recommended)"))
        self.rb_user = QRadioButton(_("Install only for me (no administrator rights needed)"))
        grp = QButtonGroup(self)
        grp.addButton(self.rb_system)
        grp.addButton(self.rb_user)
        (self.rb_user if self.c.scope == "user" else self.rb_system).setChecked(True)
        self.rb_system.toggled.connect(lambda on: setattr(self.c, "scope", "system" if on else "user"))
        p.addWidget(self.rb_system)
        p.addWidget(self.rb_user)

        p = panel(_("Updates"))
        cb = QCheckBox(_("Check for app updates automatically (every 2 weeks)"))
        cb.setChecked(self.c.auto_update_check)
        cb.toggled.connect(lambda on: setattr(self.c, "auto_update_check", on))
        p.addWidget(cb)
        refresh = QPushButton(QIcon.fromTheme("view-refresh"), _("Refresh app catalog now"))
        refresh.clicked.connect(lambda: self.c.start(force_refresh=True))
        p.addWidget(refresh, 0, Qt.AlignmentFlag.AlignLeft)

        p = panel(_("Privacy"))
        cb2 = QCheckBox(_("Show ratings and reviews from the Open Desktop Ratings Service"))
        cb2.setChecked(self.c.ratings_enabled)

        def toggle_ratings(on):
            self.c.ratings_enabled = on
            if on:
                self.c.load_ratings()

        cb2.toggled.connect(toggle_ratings)
        p.addWidget(cb2)
        info = QLabel(_(
            "Nova Store contacts only: Flathub (dl.flathub.org) for the catalog, apps and screenshots, "
            "and – if enabled – odrs.gnome.org to read ratings. No usage data is collected or sent."))
        info.setWordWrap(True)
        info.setObjectName("NovaMuted")
        p.addWidget(info)

        p = panel(_("Software sources"))
        src = QLabel(_("Flathub – the central repository of Flatpak apps. Apps run sandboxed and are "
                       "updated independently of the system. System packages (Debian) are updated with "
                       "Nova Updates."))
        src.setWordWrap(True)
        p.addWidget(src)
        lay.addStretch(1)

    # --------------------------------------------------------------- navigation
    def _set_page(self, widget: QWidget, remember: bool = True) -> None:
        idx = self.stack.indexOf(widget)
        if remember and self.stack.currentIndex() != idx:
            self._history.append(self.stack.currentIndex())
            self._history = self._history[-20:]
        self.stack.setCurrentIndex(idx)

    def _go_back(self) -> None:
        if self.search.hasFocus() and self.search.text():
            self.search.clear()
            return
        while self._history:
            idx = self._history.pop()
            if self.stack.widget(idx) is not self.loading_page:
                self.stack.setCurrentIndex(idx)
                return
        self.nav.setCurrentRow(0)

    def _nav_changed(self, item: QListWidgetItem | None, _prev=None) -> None:
        if item is None or not len(self.c.catalog):
            return
        key = item.data(Qt.ItemDataRole.UserRole)
        if self.search.text():
            self.search.blockSignals(True)
            self.search.clear()
            self.search.blockSignals(False)
        if key == "home":
            self._set_page(self.home_page)
        elif key and key.startswith("cat:"):
            cat = key[4:]
            label = next(t for k, t, _i in CATEGORY_ITEMS if k == cat)
            apps = self.c.catalog.by_category(cat)
            self._show_list(label, _("{n} apps").format(n=len(apps)), apps)
        elif key == "installed":
            self._fill_installed()
            self._set_page(self.installed_page)
        elif key == "updates":
            self._fill_updates()
            self._set_page(self.updates_page)
        elif key == "settings":
            self._set_page(self.settings_page)

    def open_app(self, app_id: str) -> None:
        app = self.c.catalog.get(app_id) or self.c.catalog.find_by_flatpak_name(app_id)
        if not app:
            QMessageBox.information(self, _("Nova Store"), _("This app is not in the catalog."))
            return
        self.details_page.show_app(app)
        self._set_page(self.details_page)

    def _show_list(self, title: str, subtitle: str, apps: list[AppInfo]) -> None:
        self.list_title.setText(title)
        self.list_sub.setText(subtitle)
        self.list_section.clear()
        self._list_apps = apps
        self._list_shown = 0
        self._show_more()
        self.list_page.verticalScrollBar().setValue(0)
        self._set_page(self.list_page)

    def _show_more(self) -> None:
        chunk = self._list_apps[self._list_shown:self._list_shown + PAGE_SIZE]
        for app in chunk:
            self.list_section.add(self._tile(app))
        self._list_shown += len(chunk)
        self.more_btn.setVisible(self._list_shown < len(self._list_apps))

    def _tile(self, app: AppInfo) -> AppTile:
        rating = self.c.ratings.get(app.id) if self.c.ratings_enabled else None
        tile = AppTile(app, rating, self.c.is_installed(app.flatpak_name))
        tile.open_app.connect(self.open_app)
        return tile

    def _do_search(self) -> None:
        q = self.search.text().strip()
        if not q:
            if self.stack.currentWidget() is self.list_page and self.list_title.text().startswith("“"):
                self._go_back()
            return
        if not len(self.c.catalog):
            return
        results = self.c.catalog.search(q)
        web = webapps.search(q)
        n = len(results) + len(web) + len(system_apps.search(q))
        sub = _("{n} results").format(n=n) if n else _("No apps found. Try a different search term.")
        self._show_list(f"“{q}”", sub, results)
        for w in reversed(web):  # the official web apps first
            self.list_section.flow.insertWidget(0, WebAppTile(w, _german()))
        for a in reversed(system_apps.search(q)):  # and NovaOS apps before them
            self.list_section.flow.insertWidget(0, self._system_tile(a))

    # ------------------------------------------------------------ controller
    def _show_loading(self, text: str) -> None:
        if len(self.c.catalog):
            return  # background refresh keeps the current page
        self.loading_label.setText(text)
        self.loading_bar.show()
        self.retry_btn.hide()
        self.stack.setCurrentWidget(self.loading_page)

    def _catalog_failed(self, message: str) -> None:
        if len(self.c.catalog):
            QMessageBox.warning(self, _("Nova Store"), message)
            return
        self.loading_label.setText(_("The app catalog could not be loaded.") + "\n\n" + message)
        self.loading_bar.hide()
        self.retry_btn.show()

    def _catalog_ready(self) -> None:
        self._refresh_home()
        if self.stack.currentWidget() is self.loading_page:
            self.nav.setCurrentRow(0)
            self.stack.setCurrentWidget(self.home_page)
        pending = getattr(self, "_pending_open", None)
        if pending:
            self._pending_open = None
            pending()

    def _clear_layout(self, lay) -> None:
        while lay.count():
            item = lay.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

    def _refresh_home(self) -> None:
        if not len(self.c.catalog):
            return
        lay = self.home_lay
        self._clear_layout(lay)
        hero = QFrame()
        hero.setObjectName("NovaHero")
        hl = QVBoxLayout(hero)
        hl.setContentsMargins(32, 28, 32, 28)
        t = QLabel(_("Discover great apps"))
        t.setStyleSheet("font-size: 26pt; font-weight: 800;")
        s = QLabel(_("{n} apps from Flathub – sandboxed, up to date and free to install.").format(n=len(self.c.catalog)))
        s.setStyleSheet("font-size: 12pt;")
        s.setWordWrap(True)
        hl.addWidget(t)
        hl.addWidget(s)
        chips = QHBoxLayout()
        chips.setSpacing(8)
        for i, (key, text, icon) in enumerate(CATEGORY_ITEMS[:6]):
            b = QPushButton(QIcon.fromTheme(icon), text.replace("&", "&&"))
            b.setCursor(Qt.CursorShape.PointingHandCursor)
            b.setStyleSheet("QPushButton { background: rgba(255,255,255,0.18); color: white; border: none;"
                            " border-radius: 12px; padding: 6px 14px; font-weight: 600; }"
                            "QPushButton:hover { background: rgba(255,255,255,0.32); }")
            b.clicked.connect(lambda _c=False, row=i + 2: self.nav.setCurrentRow(row))
            chips.addWidget(b)
        chips.addStretch(1)
        hl.addSpacing(10)
        hl.addLayout(chips)
        if edition() >= 2:
            hero.hide()   # NovaOS 2: the app of the day and the category tiles instead
            self._home_v2(lay)
        else:
            lay.addWidget(hero)

        # NovaOS apps that are part of the system (Nova Browser): always here, "Update" when one is waiting
        sec = Section(_("NovaOS apps"), _("Part of NovaOS – updates appear here and under “Updates”."))
        for a in system_apps.SYSTEM_APPS:
            sec.add(self._system_tile(a))
        lay.addWidget(sec)

        # Claude, ChatGPT, WhatsApp: no Linux apps exist - the official web versions in their own window
        sec = Section(_("Web apps"), _("No Linux app exists for these – NovaOS opens the official website in its own "
                                       "window, with its own entry in the start menu and taskbar."))
        for w in webapps.WEBAPPS:
            sec.add(WebAppTile(w, _german()))
        lay.addWidget(sec)

        rec = self.c.catalog.recommended()
        if rec:
            sec = Section(_("Recommended for NovaOS"), _("Popular, proven apps for everyday use"))
            for app in rec:
                sec.add(self._tile(app))
            lay.addWidget(sec)
        if self.c.ratings_enabled:
            ids = [a.id for a in self.c.catalog.apps.values() if a.kind == "desktop-application"]
            popular = [self.c.catalog.get(i) for i in self.c.ratings.popular(ids, 12)]
            popular = [p for p in popular if p and p not in rec]
            if popular:
                sec = Section(_("Popular"), _("Most rated apps on the Open Desktop Ratings Service"))
                for app in popular:
                    sec.add(self._tile(app))
                lay.addWidget(sec)
        recent = self.c.catalog.recently_updated(12)
        if recent:
            sec = Section(_("Recently updated"), _("Verified apps with fresh releases"))
            for app in recent:
                sec.add(self._tile(app))
            lay.addWidget(sec)
        lay.addStretch(1)

    def _installed_changed(self) -> None:
        if self.stack.currentWidget() is self.installed_page:
            self._fill_installed()

    def _updates_changed(self, n: int = 0) -> None:
        n = len(self.c.updates) + len(self.sys_updates)
        self.updates_item.setText(_("Updates") + (f"  ({n})" if n else ""))
        if self.stack.currentWidget() is self.updates_page:
            self._fill_updates()

    # ------------------------------------------------- NovaOS apps (Nova Browser)
    # ------------------------------------------------------------- NovaOS 2 home
    CAT_GRADIENTS = {
        "games": ("#FF6B6B", "#C9184A"), "office": ("#4D96FF", "#2952CC"), "internet": ("#2FD3C6", "#1B7FA6"),
        "music": ("#B983FF", "#7C3AED"), "video": ("#FF9F45", "#E4572E"), "graphics": ("#F9C74F", "#F3722C"),
        "education": ("#43AA8B", "#277DA1"), "development": ("#6C757D", "#343A40"), "system": ("#577590", "#2B3A55"),
    }

    def _home_v2(self, lay: QVBoxLayout) -> None:
        """NovaOS 2: a big "app of the day" card and colourful category tiles."""
        rec = self.c.catalog.recommended()
        if rec:
            app = rec[datetime.date.today().timetuple().tm_yday % len(rec)]
            card = QFrame()
            card.setObjectName("NovaHero")
            card.setMinimumHeight(220)
            card.setStyleSheet("QFrame#NovaHero { border-radius: 26px; background: qlineargradient(x1:0, y1:0, x2:1, y2:1,"
                               " stop:0 #8B5CF6, stop:0.5 #6366F1, stop:1 #06B6D4); }")
            cl = QHBoxLayout(card)
            cl.setContentsMargins(36, 30, 36, 30)
            cl.setSpacing(28)
            text = QVBoxLayout()
            tag = QLabel(_("APP OF THE DAY"))
            tag.setStyleSheet("font-size: 10pt; font-weight: 800; letter-spacing: 2px; color: rgba(255,255,255,0.8);")
            name = QLabel(app.name)
            name.setStyleSheet("font-size: 30pt; font-weight: 800;")
            summ = QLabel(app.summary)
            summ.setWordWrap(True)
            summ.setStyleSheet("font-size: 13pt; color: rgba(255,255,255,0.92);")
            btn = QPushButton(_("Take a look"))
            btn.setCursor(Qt.CursorShape.PointingHandCursor)
            btn.setStyleSheet("QPushButton { background: white; color: #4C1D95; border: none; border-radius: 16px;"
                              " padding: 9px 26px; font-weight: 800; font-size: 11pt; }"
                              "QPushButton:hover { background: #EDE9FE; }")
            btn.clicked.connect(lambda _c=False, a=app.id: self.open_app(a))
            text.addWidget(tag)
            text.addWidget(name)
            text.addWidget(summ)
            text.addSpacing(10)
            text.addWidget(btn, 0, Qt.AlignmentFlag.AlignLeft)
            text.addStretch(1)
            cl.addLayout(text, 1)
            icon = QLabel()
            icon.setPixmap(app_pixmap(app, 150))
            icon.setStyleSheet("background: rgba(255,255,255,0.18); border-radius: 34px; padding: 18px;")
            cl.addWidget(icon, 0, Qt.AlignmentFlag.AlignVCenter)
            lay.addWidget(card)
        # category tiles
        sec = Section(_("Categories"))
        for i, (key, text, icon) in enumerate(CATEGORY_ITEMS):
            c1, c2 = self.CAT_GRADIENTS.get(key, ("#7C5CFF", "#3D7BFF"))
            b = QPushButton(QIcon.fromTheme(icon), "  " + text.replace("&", "&&"))
            b.setIconSize(QSize(30, 30))
            b.setFixedSize(236, 76)
            b.setCursor(Qt.CursorShape.PointingHandCursor)
            b.setStyleSheet(f"QPushButton {{ background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 {c1}, stop:1 {c2});"
                            f" color: white; border: none; border-radius: 20px; font-size: 12pt; font-weight: 800;"
                            f" text-align: left; padding-left: 18px; }}"
                            f"QPushButton:hover {{ border: 2px solid rgba(255,255,255,0.7); }}")
            b.clicked.connect(lambda _c=False, row=i + 2: self.nav.setCurrentRow(row))
            sec.add(b)
        lay.addWidget(sec)

    def _system_tile(self, a) -> SystemAppTile:
        pend = next((p for p, _n in self.sys_updates if p.app.key == a.key), None)
        news = next((n for p, n in self.sys_updates if p.app.key == a.key), "")
        tile = SystemAppTile(a, (_("New: {news}").format(news=news) if pend else a.summary(_german())),
                             self.sys_versions.get(a.key, ""), pend, self.sys_busy == a.key)
        tile.update_clicked.connect(self._update_system)
        return tile

    def _load_system_updates(self) -> None:
        worker.run(lambda: system_apps.load(_german()), on_done=self._system_loaded, on_error=lambda e: None)

    def _system_loaded(self, items) -> None:
        self.sys_updates = items or []
        self.sys_versions = {a.key: system_apps.installed_version(a) for a in system_apps.SYSTEM_APPS}
        self._updates_changed()
        if self.stack.currentWidget() is self.home_page:
            self._refresh_home()

    def _check_all(self) -> None:
        """"Check for updates": refresh the package lists (password), then apps and NovaOS apps."""
        if os.path.exists(sysapt.HELPER) and self.sys_proc is None:
            self.sys_proc = QProcess(self)
            self.sys_proc.finished.connect(lambda *_a: self._check_done())
            self.sys_proc.start("/usr/bin/pkexec", [sysapt.HELPER, "refresh"])
        self.c.check_updates()

    def _check_done(self) -> None:
        self.sys_proc = None
        self._load_system_updates()

    def _update_system(self, pending) -> None:
        if self.sys_proc is not None:
            return
        self.sys_busy = pending.app.key
        self._fill_updates()
        if self.stack.currentWidget() is self.home_page:
            self._refresh_home()
        self.sys_proc = QProcess(self)
        self.sys_proc.finished.connect(lambda code, _st, p=pending: self._system_updated(p, code))
        self.sys_proc.start("/usr/bin/pkexec", [sysapt.HELPER, "install", *pending.packages])

    def _system_updated(self, pending, code: int) -> None:
        self.sys_proc = None
        self.sys_busy = None
        if code == 0:
            import subprocess
            subprocess.Popen(["notify-send", "-a", "Nova Store", "-i", pending.app.icon,
                              _("{app} is up to date").format(app=pending.app.name)])
        elif code not in (126, 127):  # 126/127: the password question was cancelled
            QMessageBox.warning(self, "Nova Store", _("{app} could not be updated (code {c}).").format(
                app=pending.app.name, c=code))
        self._load_system_updates()

    def _fill_installed(self) -> None:
        lay = self.installed_lay
        self._clear_layout(lay)
        title = QLabel(_("Installed apps"))
        title.setObjectName("NovaTitle")
        lay.addWidget(title)
        apps = sorted(self.c.installed.values(), key=lambda a: a.title.lower())
        sub = QLabel(_("{n} Flatpak apps installed").format(n=len(apps)))
        sub.setObjectName("NovaMuted")
        lay.addWidget(sub)
        if not apps:
            empty = QLabel(_("No apps installed yet. Browse the store to find some!"))
            empty.setObjectName("NovaMuted")
            lay.addWidget(empty)
        for inst in apps:
            cat = self.c.app_for(inst.name)
            scope = _("for all users") if inst.scope == "system" else _("only for me")
            row = InstalledRow(app_pixmap(cat, 48, inst.name), cat.name if cat else inst.title,
                               f"{inst.version or inst.branch} · {scope}", inst.installed_size)
            op = self.c.operation_for(inst.name)
            if op:
                row.add_button(op.status or _("Working…")).setEnabled(False)
            else:
                b = row.add_button(_("Open"), "NovaPrimary")
                b.clicked.connect(lambda _c=False, n=inst.name, s=inst.scope: self.c.backend.launch(n, s))
                if cat:
                    d = row.add_button(_("Details"))
                    d.clicked.connect(lambda _c=False, i=cat.id: self.open_app(i))
                r = row.add_button(_("Uninstall"), "NovaDanger")
                r.clicked.connect(lambda _c=False, n=inst.name, t=inst.title: self._confirm_uninstall(n, t))
            lay.addWidget(row)
        unused = QPushButton(QIcon.fromTheme("edit-clear-all"), _("Remove unused runtimes"))
        unused.setToolTip(_("Removes runtimes and extensions that no installed app needs anymore."))
        unused.clicked.connect(self.c.remove_unused)
        lay.addSpacing(10)
        lay.addWidget(unused, 0, Qt.AlignmentFlag.AlignLeft)
        lay.addStretch(1)

    def _confirm_uninstall(self, name: str, title: str) -> None:
        res = QMessageBox.question(self, _("Uninstall"), _("Do you really want to uninstall “{name}”?").format(name=title))
        if res == QMessageBox.StandardButton.Yes:
            self.c.uninstall(name)
            self._fill_installed()

    def _fill_updates(self) -> None:
        lay = self.updates_lay
        self._clear_layout(lay)
        title = QLabel(_("Updates"))
        title.setObjectName("NovaTitle")
        lay.addWidget(title)
        when = (datetime.datetime.fromtimestamp(self.c.last_update_check).strftime("%d.%m.%Y %H:%M")
                if self.c.last_update_check else _("never"))
        sub = QLabel(_("Last checked: {when}").format(when=when))
        sub.setObjectName("NovaMuted")
        lay.addWidget(sub)
        bar = QHBoxLayout()
        check = QPushButton(QIcon.fromTheme("view-refresh"), _("Check for updates"))
        check.clicked.connect(self._check_all)
        bar.addWidget(check)
        if self.c.updates:
            all_btn = QPushButton(QIcon.fromTheme("system-software-update"), _("Update all"))
            all_btn.setObjectName("NovaPrimary")
            all_btn.clicked.connect(lambda: self.c.update(None))
            all_btn.setEnabled(self.c.operation_for("__all__") is None)
            bar.addWidget(all_btn)
        bar.addStretch(1)
        lay.addLayout(bar)
        for p, news in self.sys_updates:
            busy = self.sys_busy == p.app.key
            row = InstalledRow(QIcon.fromTheme(p.app.icon, QIcon.fromTheme("internet-web-browser")).pixmap(48, 48),
                               p.app.name, _("New: {news}").format(news=news) + "\n" + _("Version {v}").format(v=p.version), 0)
            row.subtitle.setWordWrap(True)
            b = row.add_button(_("Updating…") if busy else _("Update"), "NovaPrimary")
            b.setEnabled(not busy and self.sys_busy is None)
            b.clicked.connect(lambda _c=False, pp=p: self._update_system(pp))
            lay.addWidget(row)
        if not self.c.updates and not self.sys_updates:
            ok = QLabel("✔ " + _("All apps are up to date."))
            ok.setObjectName("NovaSubtitle")
            lay.addWidget(ok)
        for u in self.c.updates:
            cat = self.c.app_for(u.name)
            kind = _("App") if u.ref.startswith("app/") else _("Runtime / extension")
            row = InstalledRow(app_pixmap(cat, 48, u.name), cat.name if cat else u.title or u.name,
                               f"{kind} · {u.branch}", 0)
            if u.ref.startswith("app/"):
                b = row.add_button(_("Update"))
                b.clicked.connect(lambda _c=False, n=u.name: self.c.update([n]))
                b.setEnabled(self.c.operation_for(u.name) is None and self.c.operation_for("__all__") is None)
            lay.addWidget(row)
        note = QLabel(_("System updates (Debian packages, kernel, drivers) are installed with Nova Updates."))
        note.setObjectName("NovaMuted")
        note.setWordWrap(True)
        lay.addSpacing(12)
        lay.addWidget(note)
        lay.addStretch(1)

    def _queue_progress(self, key: str, pct: int, text: str) -> None:
        self.queue_label.setText(f"{text} – {pct} %")
        self.queue_progress.setValue(pct)
        self.queue_label.show()
        self.queue_progress.show()

    def _op_finished(self, key: str, ok: bool, message: str) -> None:
        self.queue_label.hide()
        self.queue_progress.hide()
        if not ok and key in ("__all__", "__unused__"):
            QMessageBox.warning(self, _("Operation failed"), message)
        if ok and key == "__unused__":
            self.statusBar().showMessage(_("Unused runtimes removed."), 5000)
        if self.stack.currentWidget() is self.updates_page:
            self._fill_updates()
        if ok:
            app = self.c.app_for(key)
            if app:
                self.statusBar().showMessage(_("“{name}” finished.").format(name=app.name), 5000)

    def when_ready(self, fn) -> None:
        """Run ``fn`` once the catalog is loaded (used for --details etc.)."""
        if len(self.c.catalog):
            fn()
        else:
            self._pending_open = fn
