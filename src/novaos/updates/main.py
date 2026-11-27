"""Nova Updates – system (Debian) and app (Flatpak) updates in one place."""

from __future__ import annotations

import datetime
import html
import os
import shutil
import subprocess
import sys
import time

from PyQt6.QtCore import QProcess, Qt, QTimer, QUrl
from PyQt6.QtGui import QDesktopServices, QIcon
from PyQt6.QtWidgets import (
    QCheckBox,
    QFrame,
    QHBoxLayout,
    QLabel,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QTreeWidget,
    QTreeWidgetItem,
    QVBoxLayout,
    QWidget,
)

from novaos.common.app import create_app, nova_icon, themed_icon
from novaos.common.i18n import _
from novaos.store import worker
from novaos.common.sysinfo import edition
from novaos.updates import apt, scheduled

PKEXEC = "/usr/bin/pkexec"


def flatpak_updates():
    try:
        from novaos.store.flatpak_backend import FlatpakBackend

        return FlatpakBackend().list_updates()
    except Exception:  # noqa: BLE001 - flatpak missing or offline
        return []


def flatpak_update_all(progress):
    from novaos.store.flatpak_backend import FlatpakBackend

    b = FlatpakBackend()
    ups = b.list_updates()
    for scope in ("system", "user"):
        refs = [u.ref for u in ups if u.scope == scope]
        if refs:
            b.update(refs, scope, progress=progress)
    return len(ups)


WHATSNEW = "/usr/share/novaos/whatsnew-pc.txt"
WEBSITE = "/usr/share/novaos/website"   # built into Nova Browser (package novaos-browser)
WEBSITE_ONLINE = "https://superali99-tech.github.io/Hanse-1-Betriebssystem-/"


def update_details_url(website: str | None = None) -> str:
    """The page of the current update on the NovaOS website: the copy built into Nova Browser
    (works offline), else the online version."""
    website = website or WEBSITE
    for rel in ("updates/aktuell.html", "index.html"):
        path = os.path.join(website, rel)
        if os.path.exists(path):
            return "file://" + path
    return WEBSITE_ONLINE + "updates/aktuell.html"


def open_in_browser(url: str) -> None:
    """Nova Browser (Firefox) when it is there, otherwise the default browser."""
    for b in ("firefox-esr", "firefox"):
        if shutil.which(b):
            try:
                subprocess.Popen([b, "--new-tab", url], start_new_session=True,
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                return
            except OSError:
                break
    QDesktopServices.openUrl(QUrl(url))


def novaos_build() -> str:
    """The installed NovaOS version, e.g. 0.1.1.88 (the version of the Nova apps package)."""
    try:
        out = subprocess.run(["dpkg-query", "-W", "-f=${Version}", "novaos-apps"],
                             capture_output=True, text=True, timeout=10).stdout.strip()
        if out:
            return out
    except (OSError, subprocess.SubprocessError):
        pass
    from novaos import __version__
    return __version__


def whatsnew(path: str | None = None, german: bool | None = None) -> list[str]:
    """Lines "Deutsch|English" of the newest NovaOS changes (comments start with #)."""
    path = path or WHATSNEW
    if german is None:
        german = (os.environ.get("LANGUAGE") or os.environ.get("LC_ALL") or os.environ.get("LC_MESSAGES")
                  or os.environ.get("LANG") or "").startswith("de")
    items = []
    try:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                de, _sep, en = line.partition("|")
                items.append((de if german or not en else en).strip())
    except OSError:
        pass
    return items


class UpdatesWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(_("Nova Updates"))
        self.resize(900, 680)
        self.pkg_updates: list[apt.PackageUpdate] = []
        self.app_updates = []
        self.proc: QProcess | None = None
        self._queue: list[str] = []
        self._errors: list[str] = []

        area = QScrollArea()
        area.setWidgetResizable(True)
        self.setCentralWidget(area)
        host = QWidget()
        area.setWidget(host)
        lay = QVBoxLayout(host)
        lay.setContentsMargins(28, 22, 28, 22)
        lay.setSpacing(14)

        # Status hero
        hero = QFrame()
        hero.setObjectName("NovaHero")
        hero.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Maximum)
        hl = QHBoxLayout(hero)
        hl.setContentsMargins(26, 22, 26, 22)
        icon = QLabel()
        icon.setPixmap(nova_icon("nova-updates").pixmap(72, 72))
        hl.addWidget(icon)
        tl = QVBoxLayout()
        self.headline = QLabel(_("Checking for updates…"))
        self.headline.setStyleSheet("font-size: 20pt; font-weight: 800;")
        self.subline = QLabel("")
        self.subline.setWordWrap(True)
        self.build = novaos_build()
        self.version_label = QLabel(self._version_text())
        self.version_label.setStyleSheet("font-size: 10pt; color: rgba(255,255,255,0.78);")
        tl.addWidget(self.version_label)
        tl.addWidget(self.headline)
        tl.addWidget(self.subline)
        hl.addLayout(tl, 1)
        lay.addWidget(hero)
        self.nova2_card = self._nova2_card()
        self.nova2_card.hide()
        lay.addWidget(self.nova2_card)

        # Actions
        row = QHBoxLayout()
        self.btn_check = QPushButton(themed_icon("view-refresh"), _("Check for updates"))
        self.btn_check.clicked.connect(self.check_online)
        self.btn_install = QPushButton(themed_icon("system-software-update"), _("Install all updates"))
        self.btn_install.setObjectName("NovaPrimary")
        self.btn_install.clicked.connect(self.install_all)
        self.btn_reboot = QPushButton(themed_icon("system-reboot"), _("Restart now"))
        self.btn_reboot.clicked.connect(self.reboot)
        self.btn_reboot.hide()
        row.addWidget(self.btn_check)
        row.addStretch(1)
        row.addWidget(self.btn_reboot)
        row.addWidget(self.btn_install)
        lay.addLayout(row)

        self.progress = QProgressBar()
        self.progress.setRange(0, 100)
        self.progress.setTextVisible(False)
        self.progress_label = QLabel()
        self.progress_label.setObjectName("NovaMuted")
        self.progress.hide()
        self.progress_label.hide()
        lay.addWidget(self.progress)
        lay.addWidget(self.progress_label)

        # Lists
        self.tree = QTreeWidget()
        self.tree.setHeaderLabels([_("Update"), _("Installed"), _("New"), _("Type")])
        self.tree.setRootIsDecorated(True)
        self.tree.setMinimumHeight(260)
        self.tree.setColumnWidth(0, 300)
        lay.addWidget(self.tree, 1)

        # nothing to update: a calm card instead of an empty table
        self.uptodate = QFrame()
        self.uptodate.setObjectName("NovaPanel")
        ul = QHBoxLayout(self.uptodate)
        ul.setContentsMargins(22, 18, 22, 18)
        ul.setSpacing(16)
        check = QLabel("✓")
        check.setFixedSize(52, 52)
        check.setAlignment(Qt.AlignmentFlag.AlignCenter)
        check.setStyleSheet("background: #22A06B; color: white; border-radius: 26px; font-size: 24pt; font-weight: 800;")
        ul.addWidget(check)
        ut = QVBoxLayout()
        t1 = QLabel(_("Everything is up to date"))
        t1.setObjectName("NovaSubtitle")
        self.uptodate_text = QLabel("")
        self.uptodate_text.setObjectName("NovaMuted")
        self.uptodate_text.setWordWrap(True)
        ut.addWidget(t1)
        ut.addWidget(self.uptodate_text)
        ul.addLayout(ut, 1)
        self.uptodate.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Maximum)
        self.uptodate.hide()
        lay.addWidget(self.uptodate)

        news = whatsnew()[:5]   # the most important points - everything else on the website ("See all details")
        if news:
            np_ = QFrame()
            np_.setObjectName("NovaPanel")
            nl = QVBoxLayout(np_)
            nl.setContentsMargins(20, 14, 20, 14)
            nh = QLabel(_("What's new in NovaOS {v}").format(v=self.build))
            nh.setObjectName("NovaSubtitle")
            nl.addWidget(nh)
            body = QLabel("<ul style='margin-left:-18px'>" + "".join(
                "<li style='margin-bottom:4px'>" + html.escape(n) + "</li>" for n in news) + "</ul>")
            body.setTextFormat(Qt.TextFormat.RichText)
            body.setWordWrap(True)
            nl.addWidget(body)
            more = QPushButton(_("See all details") + "  →")
            more.setObjectName("NovaLink")
            more.setCursor(Qt.CursorShape.PointingHandCursor)
            more.setFlat(True)
            more.setStyleSheet("QPushButton { color: #7C5CFF; font-weight: 700; border: none; padding: 2px 0;"
                               " text-align: left; background: transparent; }"
                               "QPushButton:hover { color: #22B8D9; text-decoration: underline; }")
            more.clicked.connect(lambda: open_in_browser(update_details_url()))
            nl.addWidget(more, 0, Qt.AlignmentFlag.AlignLeft)
            np_.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Maximum)
            lay.addWidget(np_)
        # takes the free space while the table is hidden (nothing to update)
        self.filler = QWidget()
        self.filler.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Expanding)
        lay.addWidget(self.filler, 1)

        # Settings
        panel = QFrame()
        panel.setObjectName("NovaPanel")
        panel.setSizePolicy(QSizePolicy.Policy.Preferred, QSizePolicy.Policy.Maximum)
        pl = QVBoxLayout(panel)
        pl.setContentsMargins(16, 12, 16, 12)
        h = QLabel(_("Automatic updates"))
        h.setObjectName("NovaSubtitle")
        pl.addWidget(h)
        self.schedule_label = QLabel()
        self.schedule_label.setWordWrap(True)
        pl.addWidget(self.schedule_label)
        self.cb_auto = QCheckBox(_("Additionally install security updates daily in the background"))
        self.cb_auto.setChecked(apt.auto_updates_enabled())
        self.cb_auto.toggled.connect(self.toggle_auto)
        pl.addWidget(self.cb_auto)
        note = QLabel(_("NovaOS is based on Debian 13 “trixie”. Security fixes come from the Debian security team, "
                        "apps from Flathub. Interval and settings: /etc/novaos/updates.conf"))
        note.setWordWrap(True)
        note.setObjectName("NovaMuted")
        pl.addWidget(note)
        lay.addWidget(panel)

        self.log = QPlainTextEdit()
        self.log.setReadOnly(True)
        self.log.setMaximumBlockCount(2000)
        self.log.setFixedHeight(160)
        self.log.hide()
        self.btn_log = QPushButton(_("Show details"))
        self.btn_log.setCheckable(True)
        self.btn_log.toggled.connect(lambda on: (self.log.setVisible(on),
                                                 self.btn_log.setText(_("Hide details") if on else _("Show details"))))
        lay.addWidget(self.btn_log, 0, Qt.AlignmentFlag.AlignLeft)
        lay.addWidget(self.log)

        QTimer.singleShot(50, self.check_local)

    def _version_text(self) -> str:
        if edition() >= 2:
            return _("NovaOS 2 “Nebula” · build {v} · based on Debian 13").format(v=self.build)
        return _("NovaOS {v} · based on Debian 13").format(v=self.build)

    # ------------------------------------------------------------- NovaOS 2
    def _nova2_card(self) -> QFrame:
        card = QFrame()
        card.setObjectName("Nova2Card")
        card.setStyleSheet("QFrame#Nova2Card { border-radius: 24px; background: qlineargradient(x1:0, y1:0, x2:1, y2:1,"
                           " stop:0 #0F0C29, stop:0.45 #4C1D95, stop:1 #0891B2); }"
                           "QFrame#Nova2Card QLabel { color: white; background: transparent; }")
        cl = QHBoxLayout(card)
        cl.setContentsMargins(30, 24, 30, 24)
        cl.setSpacing(24)
        text = QVBoxLayout()
        tag = QLabel(_("NEW"))
        tag.setStyleSheet("font-size: 9pt; font-weight: 800; letter-spacing: 3px; color: #A5F3FC;")
        title = QLabel("NovaOS <span style='color:#67E8F9'>2</span>")
        title.setStyleSheet("font-size: 34pt; font-weight: 900;")
        sub = QLabel(_("“Nebula” – the biggest NovaOS update so far."))
        sub.setStyleSheet("font-size: 12pt; color: rgba(255,255,255,0.9);")
        feats = QLabel("<br>".join("✦ " + f for f in (
            _("Nova Disk: see what fills your disk and rescue deleted files"),
            _("The new Nova Store with the app of the day and colourful categories"),
            _("“Nebula” design: new wallpaper, start and login screen"))))
        feats.setStyleSheet("font-size: 11pt; color: rgba(255,255,255,0.92);")
        feats.setWordWrap(True)
        sub.setWordWrap(True)
        text.addWidget(tag)
        text.addWidget(title)
        text.addWidget(sub)
        text.addSpacing(6)
        text.addWidget(feats)
        cl.addLayout(text, 1)
        right = QVBoxLayout()
        right.addStretch(1)
        self.btn_nova2 = QPushButton(_("Get NovaOS 2"))
        self.btn_nova2.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_nova2.setStyleSheet("QPushButton { background: white; color: #4C1D95; border: none; border-radius: 18px;"
                                     " padding: 12px 30px; font-weight: 800; font-size: 12pt; }"
                                     "QPushButton:hover { background: #EDE9FE; } QPushButton:disabled { color: #8B8B9E; }")
        self.btn_nova2.clicked.connect(self.get_nova2)
        right.addWidget(self.btn_nova2)
        note = QLabel(_("Free · your files and apps stay"))
        note.setStyleSheet("font-size: 9pt; color: rgba(255,255,255,0.75);")
        note.setAlignment(Qt.AlignmentFlag.AlignCenter)
        right.addWidget(note)
        right.addStretch(1)
        cl.addLayout(right)
        return card

    def _nova2_available(self) -> bool:
        if edition() >= 2:
            return False
        from novaos.store.system_apps import policy
        installed, candidate = policy("novaos-2")
        return bool(candidate) and not installed

    def get_nova2(self) -> None:
        box = QMessageBox(self)
        box.setWindowTitle("NovaOS 2")
        box.setIcon(QMessageBox.Icon.Information)
        box.setText("<b>" + _("Get NovaOS 2 now?") + "</b>")
        box.setInformativeText(_("NovaOS now downloads NovaOS 2 in the background – this can take a few minutes. "
                                 "At the next restart NovaOS 2 is installed, with progress on the start screen; "
                                 "that takes a while, like installing NovaOS. Your files, settings and apps stay."))
        go = box.addButton(_("Get NovaOS 2"), QMessageBox.ButtonRole.AcceptRole)
        box.addButton(_("Not now"), QMessageBox.ButtonRole.RejectRole)
        box.exec()
        if box.clickedButton() is not go:
            return
        self._errors = []
        self.btn_nova2.setEnabled(False)
        self.btn_nova2.setText(_("Downloading…"))
        self._run_helper(["novaos2"], then=self._nova2_prepared)

    def _nova2_prepared(self) -> None:
        self._busy(False)
        self.btn_nova2.setEnabled(True)
        self.btn_nova2.setText(_("Get NovaOS 2"))
        if self._errors:
            QMessageBox.warning(self, "NovaOS 2", _("NovaOS 2 could not be prepared:") + "\n\n" + "\n".join(self._errors[:5]))
            return
        self.check_local()
        box = QMessageBox(self)
        box.setWindowTitle("NovaOS 2")
        box.setIcon(QMessageBox.Icon.Information)
        box.setText("<b>" + _("NovaOS 2 is ready") + "</b>")
        box.setInformativeText(_("Restart now to install NovaOS 2. Please do not turn off the computer while it is "
                                 "being installed. Save your work first."))
        now = box.addButton(_("Restart and install"), QMessageBox.ButtonRole.AcceptRole)
        box.addButton(_("Later"), QMessageBox.ButtonRole.RejectRole)
        box.exec()
        if box.clickedButton() is now:
            subprocess.Popen(["systemctl", "reboot"])

    # --------------------------------------------------------------- checking
    def check_local(self) -> None:
        """List updates from the current package lists (no password needed)."""
        self.headline.setText(_("Checking for updates…"))
        self._busy(True, _("Reading package lists…"), indeterminate=True)
        worker.run(lambda: (apt.list_upgradable(), flatpak_updates(), self._nova2_available()), on_done=self._loaded,
                   on_error=lambda e: self._loaded(([], [], False)))

    def check_online(self) -> None:
        """Refresh the Debian package lists (needs administrator rights)."""
        self._run_helper(["refresh"], then=self.check_local)

    def _loaded(self, result) -> None:
        self.pkg_updates, self.app_updates = result[0], result[1]
        nova2 = len(result) > 2 and result[2]
        self._busy(False)
        self.tree.clear()
        sec = sum(1 for p in self.pkg_updates if p.security)
        if self.pkg_updates:
            top = QTreeWidgetItem([_("System packages ({n})").format(n=len(self.pkg_updates)), "", "", ""])
            top.setIcon(0, nova_icon("novaos"))
            for p in self.pkg_updates:
                it = QTreeWidgetItem([p.name, p.old, p.new, _("Security update") if p.security else _("Update")])
                if p.security:
                    it.setIcon(3, themed_icon("security-high", "dialog-warning"))
                top.addChild(it)
            self.tree.addTopLevelItem(top)
            top.setExpanded(len(self.pkg_updates) < 30)
        if self.app_updates:
            top = QTreeWidgetItem([_("Apps and runtimes ({n})").format(n=len(self.app_updates)), "", "", ""])
            top.setIcon(0, nova_icon("nova-store"))
            for u in self.app_updates:
                it = QTreeWidgetItem([u.title or u.name, u.version or u.branch, _("new version"), "Flatpak"])
                ic = QIcon.fromTheme(u.name)
                if not ic.isNull():
                    it.setIcon(0, ic)
                top.addChild(it)
            self.tree.addTopLevelItem(top)
            top.setExpanded(True)
        total = len(self.pkg_updates) + len(self.app_updates)
        age = apt.lists_age_seconds()
        checked = (datetime.datetime.now() - datetime.timedelta(seconds=age)).strftime("%d.%m.%Y %H:%M") if age is not None else _("never")
        if total:
            self.headline.setText(_("{n} updates available").format(n=total))
            txt = _("{s} security updates.").format(s=sec) + " " if sec else ""
            self.subline.setText(txt + _("Package lists from {when}.").format(when=checked))
        else:
            self.headline.setText(_("Your system is up to date"))
            self.subline.setText(_("Package lists from {when}.").format(when=checked))
        self.tree.setVisible(total > 0)
        self.uptodate.setVisible(total == 0)
        self.filler.setVisible(total == 0)
        self.uptodate_text.setText(_("NovaOS {v} – last search: {when}.").format(v=self.build, when=checked))
        self.btn_install.setVisible(total > 0)
        self.btn_install.setEnabled(total > 0)
        # with nothing to install, searching is the main action
        self.btn_check.setObjectName("" if total or apt.reboot_required() else "NovaPrimary")
        self.btn_check.style().unpolish(self.btn_check)
        self.btn_check.style().polish(self.btn_check)
        self.btn_reboot.setText(_("Restart now"))
        self.btn_reboot.setVisible(apt.reboot_required())
        self.btn_reboot.setObjectName("NovaPrimary" if apt.reboot_required() and not total else "")
        self.btn_reboot.style().unpolish(self.btn_reboot)
        self.btn_reboot.style().polish(self.btn_reboot)
        if apt.reboot_required():
            self.subline.setText(self.subline.text() + " " + _("A restart is required to finish previous updates."))
            if not total:
                self.headline.setText(_("Done – please restart"))
                self.subline.setText(_("Package lists from {when}.").format(when=checked))
                self.uptodate_text.setText(_("The updates are installed. Restart the computer so that everything new is used."))
        prepared = scheduled.pending()
        self.nova2_card.setVisible(bool(nova2) and not (prepared and prepared.get("title")))
        if prepared and prepared.get("title"):
            self.headline.setText(_("{title} is ready – please restart").format(title=prepared["title"]))
            self.subline.setText(_("{title} has been downloaded and will be installed when you restart the computer.")
                                 .format(title=prepared["title"]))
            self.btn_reboot.setText(_("Restart and install"))
            self.btn_reboot.show()
        elif prepared:
            self.headline.setText(_("Update available – please restart"))
            self.subline.setText(_("{n} updates have been downloaded and will be installed when you restart the "
                                   "computer.").format(n=prepared.get("count", 0)))
            self.btn_reboot.setText(_("Restart and install"))
            self.btn_reboot.show()
        result = scheduled.last_result()
        if result and not result.get("ok", True) and not prepared:
            self.subline.setText(self.subline.text() + " " + _("The last automatic installation reported errors: "
                                                               "{e}").format(e="; ".join(result.get("errors", []))))
        self._show_schedule()

    def _show_schedule(self) -> None:
        cfg = scheduled.read_config()
        fmt = lambda t: datetime.datetime.fromtimestamp(t).strftime("%d.%m.%Y") if t else _("never")  # noqa: E731
        if scheduled.is_yes(cfg["ENABLED"]):
            text = _("NovaOS searches for updates every {d} days, downloads them in the background and installs "
                     "them at the next restart. All users are notified.").format(d=scheduled.interval_days(cfg))
            text += " " + _("Last search: {last} · next search: {next}.").format(
                last=fmt(scheduled.last_check()), next=fmt(scheduled.next_check()))
        else:
            text = _("The automatic update search is switched off.")
        self.schedule_label.setText(text)

    # ------------------------------------------------------------- installing
    def install_all(self) -> None:
        self._errors = []
        steps = []
        if self.pkg_updates:
            steps.append("system")
        if self.app_updates:
            steps.append("apps")
        self._queue = steps
        self._next_step()

    def _next_step(self) -> None:
        if not self._queue:
            self._finished()
            return
        step = self._queue.pop(0)
        if step == "system":
            self._run_helper(["upgrade"], then=self._next_step)
        else:
            self._busy(True, _("Updating apps…"))
            worker.run(flatpak_update_all, with_progress=True,
                       on_progress=lambda p, t: self._progress(p, t),
                       on_done=lambda n: self._next_step(),
                       on_error=lambda e: (self._errors.append(str(e)), self._next_step()))

    def _finished(self) -> None:
        self._busy(False)
        self.build = novaos_build()  # a NovaOS update changes the version shown at the top
        self.version_label.setText(self._version_text())
        if self._errors:
            QMessageBox.warning(self, _("Nova Updates"), _("Some updates failed:") + "\n\n" + "\n".join(self._errors[:5]))
        else:
            subprocess.Popen(["notify-send", "-a", _("Nova Updates"), "-i", "nova-updates",
                              _("Updates installed"), _("Your system is up to date.")])
        self.check_local()
        if not self._errors and apt.reboot_required():
            self.ask_restart()

    def ask_restart(self) -> None:
        """After installing: "Restart now" or "Later", like on a phone."""
        box = QMessageBox(self)
        box.setWindowTitle(_("Updates installed"))
        box.setIcon(QMessageBox.Icon.Information)
        box.setText("<b>" + _("Updates installed – restart to finish") + "</b>")
        box.setInformativeText(_("NovaOS needs a restart so that everything new is used. Save your work first."))
        now = box.addButton(_("Restart now"), QMessageBox.ButtonRole.AcceptRole)
        now.setObjectName("NovaPrimary")
        box.addButton(_("Later"), QMessageBox.ButtonRole.RejectRole)
        box.exec()
        if box.clickedButton() is now:
            subprocess.Popen(["systemctl", "reboot"])

    def _run_helper(self, args: list[str], then=None) -> None:
        if not os.path.exists(apt.HELPER):
            QMessageBox.warning(self, _("Nova Updates"), _("The update helper is not installed."))
            return
        self._busy(True, _("Waiting for authorization…"))
        self.log.appendPlainText("$ pkexec nova-apt-helper " + " ".join(args))
        self.proc = QProcess(self)
        self.proc.setProcessChannelMode(QProcess.ProcessChannelMode.MergedChannels)
        self.proc.readyReadStandardOutput.connect(self._read_helper)
        self.proc.finished.connect(lambda code, _status: self._helper_done(code, then))
        self.proc.start(PKEXEC, [apt.HELPER, *args])

    def _read_helper(self) -> None:
        while self.proc and self.proc.canReadLine():
            line = bytes(self.proc.readLine()).decode(errors="replace")
            kind, pct, text = apt.parse_helper_line(line)
            if kind == "progress":
                self._progress(pct, text)
            elif kind == "error":
                self._errors.append(text)
                self.log.appendPlainText("ERROR: " + text)
            elif kind == "log":
                self.log.appendPlainText(text)

    def _helper_done(self, code: int, then) -> None:
        self._read_helper()
        self.proc = None
        if code in (126, 127):  # pkexec: dismissed / not authorized
            self._busy(False)
            self.progress_label.setText(_("Authorization was cancelled."))
            self.progress_label.show()
            self._queue = []
            return
        if code != 0 and not self._errors:
            self._errors.append(_("The update helper exited with code {c}.").format(c=code))
        if then:
            then()
        else:
            self._busy(False)

    def toggle_auto(self, on: bool) -> None:
        self._run_helper(["auto-updates", "on" if on else "off"],
                         then=lambda: (self._busy(False), self.cb_auto.blockSignals(True),
                                       self.cb_auto.setChecked(apt.auto_updates_enabled()),
                                       self.cb_auto.blockSignals(False)))

    def reboot(self) -> None:
        if QMessageBox.question(self, _("Restart"), _("Restart the computer now? Save your work first.")) \
                == QMessageBox.StandardButton.Yes:
            subprocess.Popen(["systemctl", "reboot"])

    # ---------------------------------------------------------------- helpers
    def _busy(self, on: bool, text: str = "", indeterminate: bool = False) -> None:
        self.btn_check.setEnabled(not on)
        self.btn_install.setEnabled(not on and bool(self.pkg_updates or self.app_updates))
        self.cb_auto.setEnabled(not on)
        self.progress.setVisible(on)
        self.progress_label.setVisible(on)
        self.progress.setRange(0, 0 if indeterminate else 100)
        if on:
            self.progress.setValue(0)
            self.progress_label.setText(text)

    def _progress(self, pct: int, text: str) -> None:
        self.progress.setRange(0, 100)
        self.progress.setValue(max(0, min(100, pct)))
        self.progress_label.setText(html.unescape(text))


def _restart_prompt() -> None:
    """Plasma's own restart dialog (lets the user save work); Nova Updates as fallback."""
    rc = subprocess.run(["dbus-send", "--session", "--type=method_call", "--dest=org.kde.LogoutPrompt",
                         "/LogoutPrompt", "org.kde.LogoutPrompt.promptReboot"], capture_output=True).returncode
    if rc != 0:
        subprocess.Popen(["nova-updates"], start_new_session=True)


def notifier() -> int:
    """Run in every user session (systemd user timer + path unit).

    * updates downloaded by the 14-day check → "Update available, please
      restart" (repeated at login and every 4 hours until the restart)
    * after the restart → one-time "Updates installed" (or an error)
    """
    from PyQt6.QtCore import QSettings

    settings = QSettings("NovaOS", "nova-updates")
    if not settings.value("notify", True, type=bool):
        return 0
    result = scheduled.last_result()
    seen = settings.value("last_result_seen", 0.0, type=float)
    if result and result.get("time", 0) > seen:
        settings.setValue("last_result_seen", float(result["time"]))
        settings.sync()
        if time.time() - result["time"] < 3 * 86400:
            if result.get("ok"):
                title, body = _("Updates installed"), _("NovaOS was updated at the last restart ({n} updates).").format(
                    n=result.get("count", 0))
            else:
                title, body = _("Updates could not be installed"), _("Details are shown in Nova Updates.")
            subprocess.run(["notify-send", "-a", _("Nova Updates"), "-i", "nova-updates", title, body],
                           capture_output=True, timeout=30)
    prepared = scheduled.pending()
    if not prepared:
        return 0
    body = _("{n} updates are ready and will be installed when you restart. Please restart your PC so that "
             "NovaOS stays up to date.").format(n=prepared.get("count", 0))
    try:
        res = subprocess.run(["notify-send", "-a", _("Nova Updates"), "-i", "nova-updates", "-u", "critical",
                              "-A", "restart=" + _("Restart now"), "-A", "open=" + _("Details"), "-w",
                              _("Update available"), body], capture_output=True, text=True, timeout=3600)
        action = res.stdout.strip()
        if action == "restart":
            _restart_prompt()
        elif action == "open":
            subprocess.Popen(["nova-updates"], start_new_session=True)
    except (OSError, subprocess.TimeoutExpired):
        pass
    return 0


def main() -> int:
    if "--notify" in sys.argv:
        return notifier()
    app = create_app("org.novaos.Updates", _("Nova Updates"), "nova-updates")
    win = UpdatesWindow()
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
