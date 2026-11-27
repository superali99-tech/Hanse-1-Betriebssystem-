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

from PyQt6.QtCore import (
    QEasingCurve,
    QParallelAnimationGroup,
    QPoint,
    QPropertyAnimation,
    QRectF,
    QSequentialAnimationGroup,
    QSettings,
    Qt,
    QTimer,
)
from PyQt6.QtGui import QColor, QLinearGradient, QPainter, QPainterPath, QPen, QPixmap
from PyQt6.QtWidgets import (
    QCheckBox,
    QFrame,
    QGraphicsOpacityEffect,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QSizePolicy,
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
        if edition() >= 2:
            SETTINGS.setValue("nova2_tour", TOUR_VERSION)
        SETTINGS.sync()
        super().closeEvent(event)


TOUR_VERSION = 2   # raise to show the NovaOS 2 card once more after an update


class FadeIn:
    """Quick entrance: fade in and glide up a few pixels, one element after the other."""

    DURATION = 220   # ms per element
    STAGGER = 70     # ms between two elements

    def __init__(self) -> None:
        self.group = QParallelAnimationGroup()
        self.items: list[QWidget] = []

    def add(self, w: QWidget) -> QWidget:
        eff = QGraphicsOpacityEffect(w)
        eff.setOpacity(0.0)
        w.setGraphicsEffect(eff)
        self.items.append(w)
        return w

    def start(self) -> None:
        for i, w in enumerate(self.items):
            seq = QSequentialAnimationGroup(self.group)
            seq.addPause(i * self.STAGGER)
            par = QParallelAnimationGroup(seq)
            fade = QPropertyAnimation(w.graphicsEffect(), b"opacity", par)
            fade.setDuration(self.DURATION)
            fade.setStartValue(0.0)
            fade.setEndValue(1.0)
            fade.setEasingCurve(QEasingCurve.Type.OutCubic)
            glide = QPropertyAnimation(w, b"pos", par)
            glide.setDuration(self.DURATION)
            glide.setStartValue(w.pos() + QPoint(0, 14))
            glide.setEndValue(w.pos())
            glide.setEasingCurve(QEasingCurve.Type.OutCubic)
            self.group.addAnimation(seq)
        self.group.finished.connect(self._clear)
        self.group.start()

    def _clear(self) -> None:
        for w in self.items:   # no effect left: sharp text, no extra painting
            w.setGraphicsEffect(None)


class FeatureTile(QFrame):
    """A tile on the NovaOS 2 card: coloured icon, title, text; click opens it."""

    def __init__(self, icon: str, colors: tuple[str, str], title: str, text: str, cmd: str, parent=None):
        super().__init__(parent)
        self.cmd = cmd
        self.setObjectName("Nova2Tile")
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setStyleSheet(
            "QFrame#Nova2Tile { background: rgba(255,255,255,0.07); border: 1px solid rgba(255,255,255,0.12);"
            " border-radius: 22px; }"
            "QFrame#Nova2Tile:hover { background: rgba(255,255,255,0.13); border-color: rgba(165,243,252,0.55); }"
            "QFrame#Nova2Tile QLabel { background: transparent; border: none; color: white; }")
        lay = QVBoxLayout(self)
        lay.setContentsMargins(20, 20, 20, 18)
        lay.setSpacing(8)
        badge = QLabel()
        badge.setFixedSize(54, 54)
        badge.setAlignment(Qt.AlignmentFlag.AlignCenter)
        badge.setStyleSheet("background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 %s, stop:1 %s);"
                            " border-radius: 16px;" % colors)
        badge.setPixmap(nova_icon(icon).pixmap(34, 34))
        lay.addWidget(badge)
        lay.addSpacing(4)
        t = QLabel(title)
        t.setWordWrap(True)
        t.setStyleSheet("font-size: 14pt; font-weight: 800;")
        lay.addWidget(t)
        d = QLabel(text)
        d.setWordWrap(True)
        d.setStyleSheet("font-size: 10pt; color: rgba(255,255,255,0.78);")
        lay.addWidget(d)
        lay.addStretch(1)
        more = QLabel(_("Open") + "  →")
        more.setStyleSheet("font-size: 10pt; font-weight: 700; color: #A5F3FC;")
        lay.addWidget(more)

    def mouseReleaseEvent(self, event):  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton and self.rect().contains(event.position().toPoint()):
            run_detached(*self.cmd.split())
        super().mouseReleaseEvent(event)


class Nova2Tour(QWidget):
    """The big NovaOS 2 card, once after the upgrade: a frameless rounded card with the Nebula
    picture, what is new as tiles, quick animations."""

    W, H, RADIUS, HERO = 1040, 620, 30, 300

    def __init__(self):
        super().__init__()
        self.setWindowTitle(_("Welcome to NovaOS 2"))
        self.setWindowFlags(Qt.WindowType.FramelessWindowHint | Qt.WindowType.Window)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setFixedSize(self.W, self.H)
        self.hero = QPixmap(NEBULA)
        self.anim = FadeIn()

        root = QVBoxLayout(self)
        root.setContentsMargins(48, 0, 48, 34)
        root.setSpacing(0)

        top = QVBoxLayout()
        top.setContentsMargins(0, 52, 0, 0)
        top.setSpacing(2)
        tag = QLabel(_("WELCOME TO"))
        tag.setStyleSheet("color: #A5F3FC; font-size: 10pt; font-weight: 800; letter-spacing: 5px;")
        title = QLabel("NovaOS <span style='color:#67E8F9'>2</span>")
        title.setStyleSheet("color: white; font-size: 58pt; font-weight: 900;")
        sub = QLabel(_("Faster, more beautiful and full of new ideas – your files, apps and settings are all here."))
        sub.setWordWrap(True)
        sub.setStyleSheet("color: rgba(255,255,255,0.92); font-size: 13pt;")
        for w in (tag, title, sub):
            top.addWidget(self.anim.add(w))
        root.addLayout(top)
        root.addSpacing(self.HERO - 52 - 210)

        tiles = QHBoxLayout()
        tiles.setSpacing(16)
        for icon, colors, name, text, cmd in (
            ("nova-disk", ("#06B6D4", "#3B82F6"), _("Nova Disk"),
             _("Analyse your disk, check your drives and rescue deleted files."), "nova-disk"),
            ("nova-store", ("#8B5CF6", "#EC4899"), _("The new Nova Store"),
             _("App of the day, colourful categories and web apps."), "nova-store"),
            ("preferences-desktop-theme", ("#22C55E", "#06B6D4"), _("Round taskbar"),
             _("Floating glass dock with round corners – the active app glows."), "systemsettings kcm_desktoptheme"),
            ("preferences-desktop-wallpaper", ("#F59E0B", "#EF4444"), _("Design “Nebula”"),
             _("New wallpaper, start screen and login screen."), "systemsettings kcm_wallpaper"),
        ):
            tile = FeatureTile(icon, colors, name, text, cmd)
            tile.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)   # all equally wide
            tiles.addWidget(self.anim.add(tile), 1)
        root.addSpacing(26)
        root.addLayout(tiles, 1)
        root.addSpacing(24)

        row = QHBoxLayout()
        foot = QLabel("NovaOS 2 · Nebula")
        foot.setStyleSheet("color: rgba(255,255,255,0.55); font-size: 10pt; font-weight: 600;")
        row.addWidget(foot, 1)
        go = QPushButton(_("Let's go"))
        go.setCursor(Qt.CursorShape.PointingHandCursor)
        go.setMinimumSize(190, 50)
        go.setStyleSheet("QPushButton { color: white; font-size: 12pt; font-weight: 800; border: none;"
                         " border-radius: 25px; background: qlineargradient(x1:0, y1:0, x2:1, y2:0,"
                         " stop:0 #7C5CFF, stop:1 #06B6D4); }"
                         "QPushButton:hover { background: qlineargradient(x1:0, y1:0, x2:1, y2:0,"
                         " stop:0 #8F75FF, stop:1 #22D3EE); }")
        go.clicked.connect(self.close)
        row.addWidget(self.anim.add(go))
        root.addLayout(row)
        self._started = False

    def showEvent(self, event):  # noqa: N802
        super().showEvent(event)
        if not self._started:
            self._started = True
            QTimer.singleShot(0, self.anim.start)   # after the layout has placed everything

    def paintEvent(self, event):  # noqa: N802
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        card = QPainterPath()
        card.addRoundedRect(QRectF(self.rect()), self.RADIUS, self.RADIUS)
        p.setClipPath(card)
        # deep space background
        base = QColor("#0D0B22")   # where the picture fades out: no visible edge
        bg = QLinearGradient(0, 0, 0, self.height())
        bg.setColorAt(0.0, base)
        bg.setColorAt(min(1.0, (self.HERO + 120) / self.height()), base)
        bg.setColorAt(1.0, QColor("#07071A"))
        p.fillRect(self.rect(), bg)
        # the Nebula picture at the top, fading into the card
        if not self.hero.isNull():
            pix = self.hero.scaled(self.width(), self.HERO + 120, Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                                   Qt.TransformationMode.SmoothTransformation)
            p.drawPixmap(0, 0, pix, (pix.width() - self.width()) // 2, (pix.height() - self.HERO - 120) // 2,
                         self.width(), self.HERO + 120)
        fade = QLinearGradient(0, 0, 0, self.HERO + 120)
        fade.setColorAt(0.0, QColor(11, 11, 30, 40))
        fade.setColorAt(0.55, QColor(15, 12, 41, 110))
        fade.setColorAt(1.0, base)
        p.fillRect(QRectF(0, 0, self.width(), self.HERO + 121), fade)
        p.setClipping(False)
        p.setPen(QPen(QColor(255, 255, 255, 40), 1))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawRoundedRect(QRectF(self.rect()).adjusted(0.5, 0.5, -0.5, -0.5), self.RADIUS, self.RADIUS)

    def mousePressEvent(self, event):  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton and self.windowHandle() is not None:
            self.windowHandle().startSystemMove()   # drag the card anywhere

    def keyPressEvent(self, event):  # noqa: N802
        if event.key() in (Qt.Key.Key_Escape, Qt.Key.Key_Return, Qt.Key.Key_Enter):
            self.close()
        else:
            super().keyPressEvent(event)

    def closeEvent(self, event):  # noqa: N802
        SETTINGS.setValue("seen_edition", edition())
        SETTINGS.setValue("nova2_tour", TOUR_VERSION)
        SETTINGS.sync()
        super().closeEvent(event)


def show_nova2_tour(live: bool) -> bool:
    """After the upgrade to NovaOS 2 (assistant already done): the NovaOS 2 card, once per
    TOUR_VERSION."""
    if live or "--nova2" in sys.argv:
        return "--nova2" in sys.argv
    return (edition() >= 2 and SETTINGS.value("done", False, type=bool)
            and SETTINGS.value("nova2_tour", 0, type=int) < TOUR_VERSION)


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
