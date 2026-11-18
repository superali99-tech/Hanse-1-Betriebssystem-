"""About NovaOS – version and system information."""

from __future__ import annotations

import subprocess
import sys

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QGuiApplication
from PyQt6.QtWidgets import QFrame, QGridLayout, QHBoxLayout, QLabel, QPushButton, QVBoxLayout, QWidget

from novaos import __version__
from novaos.common.app import create_app, nova_icon, themed_icon
from novaos.common.i18n import _
from novaos.common import sysinfo


class AboutWindow(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(_("About NovaOS"))
        self.resize(720, 640)
        lay = QVBoxLayout(self)
        lay.setContentsMargins(32, 28, 32, 24)
        lay.setSpacing(14)
        logo = QLabel()
        logo.setPixmap(nova_icon("novaos").pixmap(120, 120))
        lay.addWidget(logo, 0, Qt.AlignmentFlag.AlignHCenter)
        info = sysinfo.summary()
        title = QLabel(info["os"])
        title.setObjectName("NovaTitle")
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lay.addWidget(title)
        sub = QLabel(_("Based on {base} · KDE Plasma · Linux").format(base=info["base"]))
        sub.setObjectName("NovaMuted")
        sub.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lay.addWidget(sub)

        panel = QFrame()
        panel.setObjectName("NovaPanel")
        grid = QGridLayout(panel)
        grid.setContentsMargins(18, 14, 18, 14)
        grid.setHorizontalSpacing(24)
        rows = [
            (_("Operating system"), info["os"]),
            (_("Debian base"), info["base"]),
            (_("Kernel"), info["kernel"]),
            (_("Desktop"), info["desktop"] + (f" ({info['session']})" if info["session"] != "–" else "")),
            (_("Processor"), info["cpu"]),
            (_("Memory"), info["memory"]),
            (_("Graphics"), info["gpu"]),
            (_("System drive (free / total)"), info["disk"]),
            (_("Computer name"), info["hostname"]),
            (_("Nova apps"), __version__),
        ]
        self.report = "\n".join(f"{k}: {v}" for k, v in rows)
        for i, (k, v) in enumerate(rows):
            kl = QLabel(k)
            kl.setObjectName("NovaMuted")
            vl = QLabel(v)
            vl.setWordWrap(True)
            vl.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
            grid.addWidget(kl, i, 0, Qt.AlignmentFlag.AlignTop)
            grid.addWidget(vl, i, 1)
        lay.addWidget(panel)

        row = QHBoxLayout()
        copy = QPushButton(themed_icon("edit-copy"), _("Copy system report"))
        copy.clicked.connect(self.copy_report)
        upd = QPushButton(nova_icon("nova-updates"), _("Check for updates"))
        upd.clicked.connect(lambda: subprocess.Popen(["nova-updates"], start_new_session=True))
        details = QPushButton(themed_icon("hwinfo", "computer"), _("More details"))
        details.clicked.connect(lambda: subprocess.Popen(["kinfocenter"], start_new_session=True))
        for b in (copy, upd, details):
            row.addWidget(b)
        lay.addLayout(row)
        legal = QLabel(_("NovaOS is free software. It consists of Debian, KDE and many other open source projects, "
                         "each under its own licence (see /usr/share/doc/*/copyright). The NovaOS components are "
                         "licensed under the GPL-3.0-or-later. No telemetry."))
        legal.setWordWrap(True)
        legal.setObjectName("NovaMuted")
        lay.addWidget(legal)
        self.status = QLabel()
        self.status.setObjectName("NovaMuted")
        lay.addWidget(self.status)

    def copy_report(self) -> None:
        QGuiApplication.clipboard().setText(self.report)
        self.status.setText(_("System report copied to the clipboard."))


def main() -> int:
    app = create_app("org.novaos.About", _("About NovaOS"), "nova-about")
    w = AboutWindow()
    w.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
