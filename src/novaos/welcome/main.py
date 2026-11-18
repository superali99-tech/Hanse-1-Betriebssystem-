"""Nova Welcome – first-run assistant of NovaOS.

Shown once after the first login (autostart) and in every live session.
Lets the user pick light/dark mode, find apps, learn about privacy and –
in the live system – start the installer.
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
from novaos.common.sysinfo import is_live_session, os_release
from novaos.common.widgets import Card

WALLPAPER = "/usr/share/wallpapers/NovaAurora/contents/{}/3840x2160.png"
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
        self.back.setVisible(idx > 0)
        self.next.setText(_("Finish") if idx == n - 1 else _("Next"))
        self.dots.setText("  ".join("●" if i == idx else "○" for i in range(n)))

    def finish(self) -> None:
        SETTINGS.setValue("done", not self.cb_again.isChecked())
        self.close()


def main() -> int:
    live = is_live_session()
    if "--autostart" in sys.argv and not live and SETTINGS.value("done", False, type=bool):
        return 0
    app = create_app("org.novaos.Welcome", _("Welcome to NovaOS"), "nova-welcome")
    win = WelcomeWindow(live)
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
