"""Nova Updates – system (Debian) and app (Flatpak) updates in one place."""

from __future__ import annotations

import datetime
import html
import os
import subprocess
import sys
import time

from PyQt6.QtCore import QProcess, Qt, QTimer
from PyQt6.QtGui import QIcon
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
    QTreeWidget,
    QTreeWidgetItem,
    QVBoxLayout,
    QWidget,
)

from novaos.common.app import create_app, nova_icon, themed_icon
from novaos.common.i18n import _
from novaos.store import worker
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
        tl.addWidget(self.headline)
        tl.addWidget(self.subline)
        hl.addLayout(tl, 1)
        lay.addWidget(hero)

        # Actions
        row = QHBoxLayout()
        self.btn_check = QPushButton(themed_icon("view-refresh"), _("Check now"))
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

        # Settings
        panel = QFrame()
        panel.setObjectName("NovaPanel")
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

    # --------------------------------------------------------------- checking
    def check_local(self) -> None:
        """List updates from the current package lists (no password needed)."""
        self.headline.setText(_("Checking for updates…"))
        self._busy(True, _("Reading package lists…"), indeterminate=True)
        worker.run(lambda: (apt.list_upgradable(), flatpak_updates()), on_done=self._loaded,
                   on_error=lambda e: self._loaded(([], [])))

    def check_online(self) -> None:
        """Refresh the Debian package lists (needs administrator rights)."""
        self._run_helper(["refresh"], then=self.check_local)

    def _loaded(self, result) -> None:
        self.pkg_updates, self.app_updates = result
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
        self.btn_install.setEnabled(total > 0)
        self.btn_reboot.setText(_("Restart now"))
        self.btn_reboot.setVisible(apt.reboot_required())
        if apt.reboot_required():
            self.subline.setText(self.subline.text() + " " + _("A restart is required to finish previous updates."))
        prepared = scheduled.pending()
        if prepared:
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
        if self._errors:
            QMessageBox.warning(self, _("Nova Updates"), _("Some updates failed:") + "\n\n" + "\n".join(self._errors[:5]))
        else:
            subprocess.Popen(["notify-send", "-a", _("Nova Updates"), "-i", "nova-updates",
                              _("Updates installed"), _("Your system is up to date.")])
        self.check_local()

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
