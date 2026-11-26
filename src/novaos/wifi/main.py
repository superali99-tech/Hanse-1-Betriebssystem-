"""Nova WLAN – connect to a wireless network like on a phone: pick it, enter the password, done.

No IP addresses or other settings: NetworkManager does the rest and reconnects by itself
next time. Also used as the "Internet" page of Nova Welcome.
"""

from __future__ import annotations

import sys
import threading

from PyQt6.QtCore import QObject, Qt, pyqtSignal
from PyQt6.QtWidgets import (
    QCheckBox,
    QSizePolicy,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from novaos.common import wifi
from novaos.common.app import create_app, themed_icon
from novaos.common.i18n import _

BAR_ICONS = {
    4: ("network-wireless-signal-excellent-symbolic", "network-wireless-signal-excellent"),
    3: ("network-wireless-signal-good-symbolic", "network-wireless-signal-good"),
    2: ("network-wireless-signal-ok-symbolic", "network-wireless-signal-ok"),
    1: ("network-wireless-signal-weak-symbolic", "network-wireless-signal-weak"),
    0: ("network-wireless-signal-none-symbolic", "network-wireless-signal-none"),
}


class _Bridge(QObject):
    """Results of the background calls, delivered on the GUI thread."""
    scanned = pyqtSignal(object, object, str)   # networks, devices, connectivity
    connected = pyqtSignal(str, bool, str)      # ssid, ok, reason


class WifiPanel(QWidget):
    """The list of networks with password field and "Verbinden"."""

    def __init__(self, parent=None, backend=wifi):
        super().__init__(parent)
        self.backend = backend
        self.networks: list[wifi.Network] = []
        self.busy = False
        self.started = False
        self.bridge = _Bridge()
        self.bridge.scanned.connect(self._show_networks)
        self.bridge.connected.connect(self._connect_done)

        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(10)
        top = QHBoxLayout()
        self.status = QLabel(_("Searching for networks…"))
        self.status.setWordWrap(True)
        self.status.setStyleSheet("font-size: 12pt;")
        top.addWidget(self.status, 1)
        self.btn_refresh = QPushButton(themed_icon("view-refresh"), _("Refresh"))
        self.btn_refresh.clicked.connect(self.refresh)
        top.addWidget(self.btn_refresh)
        lay.addLayout(top)

        self.list = QListWidget()
        self.list.setObjectName("NovaList")  # round glass list (novaos.common.style)
        self.list.setMinimumHeight(180)
        self.list.currentRowChanged.connect(self._selected)
        self.list.itemDoubleClicked.connect(lambda _i: self._connect_clicked())
        lay.addWidget(self.list, 1)

        self.form = QWidget()
        fl = QHBoxLayout(self.form)
        fl.setContentsMargins(0, 0, 0, 0)
        self.password = QLineEdit()
        self.password.setEchoMode(QLineEdit.EchoMode.Password)
        self.password.setPlaceholderText(_("WLAN password"))
        self.password.setObjectName("NovaField")
        self.password.setMinimumHeight(38)
        self.password.returnPressed.connect(self._connect_clicked)
        fl.addWidget(self.password, 1)
        self.show_pw = QCheckBox(_("Show"))
        self.show_pw.toggled.connect(lambda on: self.password.setEchoMode(
            QLineEdit.EchoMode.Normal if on else QLineEdit.EchoMode.Password))
        fl.addWidget(self.show_pw)
        self.btn_connect = QPushButton(themed_icon("network-connect"), _("Connect"))
        self.btn_connect.setObjectName("NovaPrimary")
        self.btn_connect.setMinimumHeight(38)
        self.btn_connect.setMinimumWidth(130)
        self.btn_connect.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Fixed)
        self.btn_connect.clicked.connect(self._connect_clicked)
        fl.addWidget(self.btn_connect)
        lay.addWidget(self.form)
        self.form.setVisible(False)  # appears when a network is chosen

        self.message = QLabel("")
        self.message.setWordWrap(True)
        lay.addWidget(self.message)

    # -------------------------------------------------------------- scanning
    def start(self) -> None:
        """First scan (when the page/window is shown)."""
        if not self.started:
            self.started = True
            self.refresh()

    def refresh(self) -> None:
        if self.busy:
            return
        self._busy(True, _("Searching for networks…"))

        def work():
            devices = self.backend.wifi_devices() if self.backend.available() else []
            nets = self.backend.scan() if devices else []
            self.bridge.scanned.emit(nets, devices, self.backend.connectivity() if self.backend.available() else "unknown")

        threading.Thread(target=work, daemon=True).start()

    def _show_networks(self, nets, devices, conn: str) -> None:
        self._busy(False)
        keep = self._current_ssid()
        self.networks = list(nets)
        self.list.clear()
        if not self.backend.available() or not devices:
            self.status.setText(_("Connected to the internet ✓") if conn == "full" else
                                _("No WLAN found on this computer. Plug in a network cable – it connects by itself."))
            self.form.setVisible(False)
            return
        connected = next((n for n in self.networks if n.in_use), None)
        if connected:
            self.status.setText(_("Connected to “{name}” ✓").format(name=connected.ssid))
        elif conn == "full":
            self.status.setText(_("Connected to the internet ✓"))
        elif self.networks:
            self.status.setText(_("Choose your network:"))
        else:
            self.status.setText(_("No networks found. Is the router switched on? Try “Refresh”."))
        for n in self.networks:
            text = n.ssid + ("   🔒" if n.secured else "")
            if n.in_use:
                text += "   – " + _("connected")
            item = QListWidgetItem(themed_icon(*BAR_ICONS[n.bars]), text)
            item.setData(Qt.ItemDataRole.UserRole, n.ssid)
            if n.in_use:
                f = item.font()
                f.setBold(True)
                item.setFont(f)
            self.list.addItem(item)
        for i, n in enumerate(self.networks):
            if n.ssid == keep:
                self.list.setCurrentRow(i)
                break
        else:
            self._selected(self.list.currentRow())

    # ------------------------------------------------------------ connecting
    def _current_ssid(self) -> str | None:
        item = self.list.currentItem()
        return item.data(Qt.ItemDataRole.UserRole) if item else None

    def _network(self, ssid: str | None) -> wifi.Network | None:
        return next((n for n in self.networks if n.ssid == ssid), None)

    def _selected(self, _row: int) -> None:
        net = self._network(self._current_ssid())
        self.form.setVisible(net is not None)
        self.form.setEnabled(net is not None and not self.busy)
        secured = bool(net and net.secured and not net.in_use)
        self.password.setVisible(secured)
        self.show_pw.setVisible(secured)
        self.password.clear()
        self.message.setText("")
        if net and net.in_use:
            self.btn_connect.setText(_("Connected"))
            self.btn_connect.setEnabled(False)
        else:
            self.btn_connect.setText(_("Connect"))
            self.btn_connect.setEnabled(True)
        if secured:
            self.password.setFocus()

    def _connect_clicked(self) -> None:
        net = self._network(self._current_ssid())
        if net is None or self.busy or net.in_use:
            return
        pw = self.password.text() if net.secured else None
        if net.secured and len(pw or "") < 8:
            self.message.setText(_("A WLAN password has at least 8 characters."))
            self.password.setFocus()
            return
        self._busy(True, _("Connecting to “{name}”…").format(name=net.ssid))

        def work():
            ok, reason = self.backend.connect(net.ssid, pw)
            self.bridge.connected.emit(net.ssid, ok, reason)

        threading.Thread(target=work, daemon=True).start()

    def _connect_done(self, ssid: str, ok: bool, reason: str) -> None:
        self._busy(False)
        if ok:
            self.message.setText("")
            self.status.setText(_("Connected to “{name}” ✓").format(name=ssid))
            self.refresh()
            return
        if reason == "password":
            self.message.setText(_("The password is not correct. Please try again."))
            self.password.selectAll()
            self.password.setFocus()
        elif reason == "not_found":
            self.message.setText(_("The network is out of range. Move closer to the router and try again."))
        elif reason == "timeout":
            self.message.setText(_("The router does not answer. Please try again."))
        else:
            self.message.setText(_("Could not connect: {reason}").format(reason=reason))
        self.status.setText(_("Choose your network:"))

    def _busy(self, on: bool, text: str = "") -> None:
        self.busy = on
        if on:
            self.status.setText(text)
        self.btn_refresh.setEnabled(not on)
        self.list.setEnabled(not on)
        self.form.setEnabled(not on and self._current_ssid() is not None)


class WifiWindow(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(_("WLAN"))
        self.resize(560, 560)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(28, 24, 28, 20)
        t = QLabel(_("Connect to the internet"))
        t.setObjectName("NovaTitle")
        lay.addWidget(t)
        s = QLabel(_("Choose your WLAN and enter the password – that's all. NovaOS remembers it and connects by itself next time."))
        s.setWordWrap(True)
        s.setObjectName("NovaMuted")
        lay.addWidget(s)
        self.panel = WifiPanel()
        lay.addWidget(self.panel, 1)
        self.panel.start()


def main() -> int:
    app = create_app("org.novaos.Wifi", _("WLAN"), "network-wireless")
    win = WifiWindow()
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
