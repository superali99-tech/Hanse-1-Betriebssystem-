"""Nova Welcome – first-run assistant of NovaOS.

Shown once after the first login (autostart) and in every live session.
Lets the user connect to the WLAN, pick light/dark mode, find apps, learn
about privacy and – in the live system – start the installer.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys

from PyQt6.QtCore import QSettings, Qt
from PyQt6.QtGui import QPixmap
from PyQt6.QtWidgets import (
    QCheckBox,
    QFrame,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from novaos.common.app import create_app, nova_icon
from novaos.common.i18n import _
from novaos.common.sysinfo import edition, is_live_session, os_release
from novaos.common.widgets import Card
from novaos.wifi.main import WifiPanel

WALLPAPER = "/usr/share/wallpapers/NovaAurora/contents/{}/3840x2160.png"
NEBULA = "/usr/share/wallpapers/NovaNebula/contents/images_dark/3840x2160.jpg"
SETTINGS = QSettings("NovaOS", "nova-welcome")


def run_detached(*cmd: str) -> None:
    try:
        subprocess.Popen(list(cmd), start_new_session=True)
    except OSError:
        pass


class ThemeCard(Card):
    def __init__(self, title: str, variant: str, lnf: str, parent=None):
        super().__init__(parent)
        self.lnf = lnf
        self.setFixedSize(300, 230)
        lay = QVBoxLayout(self)
        img = QLabel()
        pix = QPixmap(WALLPAPER.format("images_dark" if variant == "dark" else "images"))
        if not pix.isNull():
            img.setPixmap(pix.scaled(270, 152, Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                                     Qt.TransformationMode.SmoothTransformation).copy(0, 0, 270, 152))
        else:
            img.setFixedSize(270, 152)
            img.setStyleSheet("background: %s; border-radius: 10px;" % ("#1b1e2b" if variant == "dark" else "#eef1ff"))
        lay.addWidget(img, 0, Qt.AlignmentFlag.AlignHCenter)
        t = QLabel(f"<b>{title}</b>")
        t.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lay.addWidget(t)


class WelcomeWindow(QWidget):
    def __init__(self, live: bool):
        super().__init__()
        self.live = live
        self.setWindowTitle(_("Welcome to NovaOS"))
        self.resize(940, 640)
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        self.stack = QStackedWidget()
        root.addWidget(self.stack, 1)

        nav = QHBoxLayout()
        nav.setContentsMargins(28, 12, 28, 20)
        self.back = QPushButton(_("Back"))
        self.back.clicked.connect(lambda: self.go(self.stack.currentIndex() - 1))
        self.dots = QLabel()
        self.dots.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.next = QPushButton(_("Next"))
        self.next.setObjectName("NovaPrimary")
        self.next.clicked.connect(lambda: self.go(self.stack.currentIndex() + 1))
        nav.addWidget(self.back)
        nav.addWidget(self.dots, 1)
        nav.addWidget(self.next)
        root.addLayout(nav)

        self.stack.addWidget(self.page_intro())
        self.wifi_page = self.page_wifi()
        self.stack.addWidget(self.wifi_page)
        self.stack.addWidget(self.page_theme())
        self.stack.addWidget(self.page_apps())
        self.stack.addWidget(self.page_privacy())
        self.stack.addWidget(self.page_done())
        self.go(0)

    # ------------------------------------------------------------------ pages
    def _page(self, title: str, subtitle: str) -> tuple[QWidget, QVBoxLayout]:
        w = QWidget()
        lay = QVBoxLayout(w)
        lay.setContentsMargins(48, 36, 48, 12)
        lay.setSpacing(14)
        t = QLabel(title)
        t.setObjectName("NovaTitle")
        s = QLabel(subtitle)
        s.setWordWrap(True)
        s.setObjectName("NovaMuted")
        lay.addWidget(t)
        lay.addWidget(s)
        return w, lay

    def page_intro(self) -> QWidget:
        w = QWidget()
        lay = QVBoxLayout(w)
        lay.setContentsMargins(0, 0, 0, 0)
        hero = QFrame()
        hero.setObjectName("NovaHero")
        hero.setStyleSheet("QFrame#NovaHero { border-radius: 0px; }")
        hl = QVBoxLayout(hero)
        hl.setContentsMargins(48, 56, 48, 56)
        logo = QLabel()
        logo.setPixmap(nova_icon("novaos").pixmap(110, 110))
        hl.addWidget(logo, 0, Qt.AlignmentFlag.AlignHCenter)
        rel = os_release()
        t = QLabel(_("Welcome to NovaOS"))
        t.setStyleSheet("font-size: 30pt; font-weight: 800;")
        t.setAlignment(Qt.AlignmentFlag.AlignCenter)
        v = QLabel(rel.get("PRETTY_NAME", "NovaOS"))
        v.setAlignment(Qt.AlignmentFlag.AlignCenter)
        v.setStyleSheet("font-size: 12pt;")
        hl.addWidget(t)
        hl.addWidget(v)
        lay.addWidget(hero)
        body = QVBoxLayout()
        body.setContentsMargins(48, 24, 48, 0)
        text = QLabel(_("NovaOS is a fast, modern and private operating system based on Debian and KDE Plasma. "
                        "This assistant helps you to set up the most important things in less than a minute."))
        text.setWordWrap(True)
        text.setAlignment(Qt.AlignmentFlag.AlignCenter)
        body.addWidget(text)
        if self.live:
            box = QHBoxLayout()
            box.addStretch(1)
            inst = QPushButton(nova_icon("novaos-install"), _("Install NovaOS"))
            inst.setObjectName("NovaPrimary")
            inst.clicked.connect(lambda: run_detached("novaos-install"))
            box.addWidget(inst)
            box.addStretch(1)
            body.addSpacing(10)
            live = QLabel(_("You are running the live system. Changes are lost when you shut down unless you install NovaOS."))
            live.setWordWrap(True)
            live.setAlignment(Qt.AlignmentFlag.AlignCenter)
            live.setObjectName("NovaMuted")
            body.addWidget(live)
            body.addLayout(box)
        body.addStretch(1)
        lay.addLayout(body, 1)
        return w

    def page_wifi(self) -> QWidget:
        w, lay = self._page(_("Connect to the internet"),
                            _("Choose your WLAN and enter the password – that's all. With a network cable "
                              "you are already connected."))
        self.wifi = WifiPanel()
        lay.addWidget(self.wifi, 1)
        return w

    def page_theme(self) -> QWidget:
        w, lay = self._page(_("Choose your style"), _("You can change this at any time in Nova Settings → Appearance."))
        row = QHBoxLayout()
        row.addStretch(1)
        self.theme_status = QLabel()
        self.theme_status.setObjectName("NovaMuted")
        for title, variant, lnf in ((_("Light"), "light", "org.novaos.desktop"),
                                    (_("Dark"), "dark", "org.novaos.desktop.dark")):
            card = ThemeCard(title, variant, lnf)
            card.clicked.connect(lambda l=lnf, t=title: self.apply_theme(l, t))
            row.addWidget(card)
        row.addStretch(1)
        lay.addSpacing(20)
        lay.addLayout(row)
        lay.addWidget(self.theme_status, 0, Qt.AlignmentFlag.AlignHCenter)
        lay.addStretch(1)
        return w

    def apply_theme(self, lnf: str, title: str) -> None:
        tool = shutil.which("plasma-apply-lookandfeel")
        if not tool:
            self.theme_status.setText(_("Theme switching needs KDE Plasma."))
            return
        # Apply colours/wallpaper only; keep the user's panel layout.
        res = subprocess.run([tool, "--apply", lnf], capture_output=True, text=True)
        if res.returncode == 0:
            self.theme_status.setText(_("“{t}” applied.").format(t=title))
        else:
            self.theme_status.setText(res.stderr.strip() or _("Could not apply the theme."))

    def page_apps(self) -> QWidget:
        w, lay = self._page(_("Get your favourite apps"),
                            _("Nova Store offers thousands of apps from Flathub. Popular choices:"))
        grid = QHBoxLayout()
        grid.setSpacing(10)
        apps = [("org.libreoffice.LibreOffice", "LibreOffice", _("Office")),
                ("org.videolan.VLC", "VLC", _("Video")),
                ("org.gimp.GIMP", "GIMP", _("Graphics")),
                ("com.valvesoftware.Steam", "Steam", _("Games")),
                ("com.obsproject.Studio", "OBS Studio", _("Streaming"))]
        for app_id, name, cat in apps:
            card = Card()
            card.setFixedSize(150, 120)
            cl = QVBoxLayout(card)
            n = QLabel(f"<b>{name}</b>")
            n.setAlignment(Qt.AlignmentFlag.AlignCenter)
            c = QLabel(cat)
            c.setObjectName("NovaMuted")
            c.setAlignment(Qt.AlignmentFlag.AlignCenter)
            cl.addStretch(1)
            cl.addWidget(n)
            cl.addWidget(c)
            cl.addStretch(1)
            card.clicked.connect(lambda a=app_id: run_detached("nova-store", "--details", a))
            grid.addWidget(card)
        lay.addSpacing(10)
        lay.addLayout(grid)
        open_store = QPushButton(nova_icon("nova-store"), _("Open Nova Store"))
        open_store.clicked.connect(lambda: run_detached("nova-store"))
        lay.addWidget(open_store, 0, Qt.AlignmentFlag.AlignLeft)
        tip = QLabel(_("Already included: Nova Browser (Firefox), Nova Files, Nova Media, Nova Photos, "
                       "Nova Notes, Nova Calendar, Nova Calculator and more."))
        tip.setWordWrap(True)
        tip.setObjectName("NovaMuted")
        lay.addWidget(tip)
        lay.addStretch(1)
        return w

    def page_privacy(self) -> QWidget:
        w, lay = self._page(_("Privacy and security"), _("NovaOS respects your privacy:"))
        points = [
            ("✔", _("No telemetry and no hidden data collection – neither in the system nor in the Nova apps.")),
            ("✔", _("Firewall enabled: incoming connections are blocked by default.")),
            ("✔", _("Security updates are installed automatically every day.")),
            ("✔", _("Apps from Nova Store run in a sandbox (Flatpak) with visible permissions.")),
            ("✔", _("Nova Browser blocks trackers and uses HTTPS-only mode.")),
        ]
        for mark, text in points:
            row = QHBoxLayout()
            m = QLabel(f"<span style='color:#22A06B; font-size:14pt'>{mark}</span>")
            t = QLabel(text)
            t.setWordWrap(True)
            row.addWidget(m, 0, Qt.AlignmentFlag.AlignTop)
            row.addWidget(t, 1)
            lay.addLayout(row)
        store_settings = QSettings("NovaOS", "nova-store")
        cb = QCheckBox(_("Show app ratings from the Open Desktop Ratings Service in Nova Store"))
        cb.setChecked(store_settings.value("ratings_enabled", True, type=bool))
        cb.toggled.connect(lambda on: store_settings.setValue("ratings_enabled", on))
        lay.addSpacing(8)
        lay.addWidget(cb)
        lay.addStretch(1)
        return w

    def page_done(self) -> QWidget:
        w, lay = self._page(_("All set!"), _("Have fun with NovaOS. Here are a few useful places:"))
        row = QHBoxLayout()
        for icon, text, cmd in (("nova-help", _("Help"), "nova-help"),
                                ("nova-settings", _("Settings"), "systemsettings"),
                                ("nova-store", _("Nova Store"), "nova-store"),
                                ("nova-updates", _("Updates"), "nova-updates")):
            b = QPushButton(nova_icon(icon), text)
            b.setMinimumHeight(44)
            b.clicked.connect(lambda _c=False, c=cmd: run_detached(c))
            row.addWidget(b)
        lay.addLayout(row)
        tips = QLabel("<ul>"
                      + "<li>" + _("<b>Meta</b> key: open the start menu and search") + "</li>"
                      + "<li>" + _("<b>Alt+Space</b>: central search for apps, files and settings") + "</li>"
                      + "<li>" + _("<b>Meta+W</b>: overview of all windows and virtual desktops") + "</li>"
                      + "<li>" + _("<b>Ctrl+Esc</b>: task manager") + "</li>"
                      + "</ul>")
        tips.setTextFormat(Qt.TextFormat.RichText)
        lay.addWidget(tips)
        self.cb_again = QCheckBox(_("Show this assistant at the next login"))
        self.cb_again.setChecked(False)
        lay.addStretch(1)
        lay.addWidget(self.cb_again)
        return w

    # ------------------------------------------------------------- navigation
    def go(self, idx: int) -> None:
        n = self.stack.count()
        if idx >= n:
            self.finish()
            return
        idx = max(0, idx)
        self.stack.setCurrentIndex(idx)
        if self.stack.currentWidget() is self.wifi_page:
            self.wifi.start()  # search for networks only when the page is shown
        self.back.setVisible(idx > 0)
        self.next.setText(_("Finish") if idx == n - 1 else _("Next"))
        self.dots.setText("  ".join("●" if i == idx else "○" for i in range(n)))

    def finish(self) -> None:
        self.close()

    def closeEvent(self, event):  # noqa: N802
        # Closing the window (also via the title bar) counts as "seen",
        # unless the user asked to see the assistant again.
        SETTINGS.setValue("done", not self.cb_again.isChecked())
        SETTINGS.setValue("seen_edition", edition())
        SETTINGS.sync()
        super().closeEvent(event)


class Nova2Tour(QWidget):
    """Shown once after the upgrade to NovaOS 2: what is new, with buttons to try it."""

    FEATURES = (
        ("nova-disk", "nova-disk",
         lambda: _("Nova Disk"),
         lambda: _("See at a glance what fills your disk, check the health of your drives and rescue deleted "
                   "photos, videos and documents – even from USB sticks and SD cards."),
         lambda: _("Open Nova Disk")),
        ("nova-store", "nova-store",
         lambda: _("The new Nova Store"),
         lambda: _("A fresh start page with the app of the day and colourful categories, NovaOS apps and web "
                   "apps like Claude, ChatGPT and WhatsApp."),
         lambda: _("Open Nova Store")),
        ("preferences-desktop-wallpaper", "systemsettings kcm_wallpaper",
         lambda: _("Design “Nebula”"),
         lambda: _("A new wallpaper in light and dark, a new start screen and a new login screen."),
         lambda: _("Change wallpaper")),
    )

    def __init__(self):
        super().__init__()
        self.setWindowTitle(_("Welcome to NovaOS 2"))
        self.resize(980, 680)
        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)
        hero = QLabel()
        hero.setFixedHeight(270)
        hero.setAlignment(Qt.AlignmentFlag.AlignCenter)
        pix = QPixmap(NEBULA)
        if not pix.isNull():
            pix = pix.scaled(980, 270, Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                             Qt.TransformationMode.SmoothTransformation)
            hero.setPixmap(pix.copy((pix.width() - 980) // 2, (pix.height() - 270) // 2, 980, 270))
            hero.setScaledContents(True)
        hero.setStyleSheet("background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #0F0C29, stop:0.5 #4C1D95,"
                           " stop:1 #0891B2);")
        over = QVBoxLayout(hero)
        over.setContentsMargins(48, 40, 48, 36)
        tag = QLabel(_("WELCOME TO"))
        tag.setStyleSheet("color: #A5F3FC; font-size: 10pt; font-weight: 800; letter-spacing: 4px;"
                          " background: transparent;")
        title = QLabel("NovaOS <span style='color:#67E8F9'>2</span>")
        title.setStyleSheet("color: white; font-size: 46pt; font-weight: 900; background: transparent;")
        sub = QLabel(_("“Nebula” is here. Your files, apps and settings are all still there – "
                       "here is what is new."))
        sub.setWordWrap(True)
        sub.setStyleSheet("color: rgba(255,255,255,0.92); font-size: 13pt; background: transparent;")
        over.addStretch(1)
        over.addWidget(tag)
        over.addWidget(title)
        over.addWidget(sub)
        root.addWidget(hero)

        body = QVBoxLayout()
        body.setContentsMargins(40, 28, 40, 24)
        body.setSpacing(18)
        cards = QHBoxLayout()
        cards.setSpacing(18)
        for icon, cmd, name, text, button in self.FEATURES:
            card = Card()
            cl = QVBoxLayout(card)
            cl.setContentsMargins(20, 20, 20, 18)
            cl.setSpacing(10)
            ic = QLabel()
            ic.setPixmap(nova_icon(icon).pixmap(52, 52))
            cl.addWidget(ic)
            t = QLabel(name())
            t.setStyleSheet("font-size: 14pt; font-weight: 800;")
            cl.addWidget(t)
            d = QLabel(text())
            d.setWordWrap(True)
            d.setObjectName("NovaMuted")
            cl.addWidget(d)
            cl.addStretch(1)
            b = QPushButton(button())
            b.clicked.connect(lambda _c=False, c=cmd: run_detached(*c.split()))
            cl.addWidget(b)
            cards.addWidget(card, 1)
        body.addLayout(cards, 1)
        row = QHBoxLayout()
        note = QLabel(_("Nova Updates keeps NovaOS 2 up to date – just like before."))
        note.setObjectName("NovaMuted")
        row.addWidget(note, 1)
        go = QPushButton(_("Let's go"))
        go.setObjectName("NovaPrimary")
        go.setMinimumWidth(160)
        go.clicked.connect(self.close)
        row.addWidget(go)
        body.addLayout(row)
        root.addLayout(body, 1)

    def closeEvent(self, event):  # noqa: N802
        SETTINGS.setValue("seen_edition", edition())
        SETTINGS.sync()
        super().closeEvent(event)


def show_nova2_tour(live: bool) -> bool:
    """After the upgrade to NovaOS 2 (assistant already done): the NovaOS 2 tour, once."""
    if live or "--nova2" in sys.argv:
        return "--nova2" in sys.argv
    return (edition() >= 2 and SETTINGS.value("done", False, type=bool)
            and SETTINGS.value("seen_edition", 1, type=int) < 2)


def main() -> int:
    live = is_live_session()
    if show_nova2_tour(live):
        app = create_app("org.novaos.Welcome", _("Welcome to NovaOS 2"), "nova-welcome")
        tour = Nova2Tour()
        tour.show()
        return app.exec()
    if "--autostart" in sys.argv and not live and SETTINGS.value("done", False, type=bool):
        return 0
    app = create_app("org.novaos.Welcome", _("Welcome to NovaOS"), "nova-welcome")
    win = WelcomeWindow(live)
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
