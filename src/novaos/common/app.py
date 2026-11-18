"""Application bootstrap shared by all NovaOS apps."""

from __future__ import annotations

import os
import sys

from PyQt6.QtCore import QLibraryInfo, QLocale, QTranslator
from PyQt6.QtGui import QIcon
from PyQt6.QtWidgets import QApplication

from novaos import __version__
from novaos.common import style

ICON_DIRS = (
    "/usr/share/icons/hicolor/scalable/apps",
    os.path.join(os.path.dirname(__file__), "..", "..", "..", "data", "icons", "hicolor", "scalable", "apps"),
)


def nova_icon(name: str) -> QIcon:
    """Theme icon with a fallback to the NovaOS SVG shipped with the apps."""
    icon = QIcon.fromTheme(name)
    if not icon.isNull():
        return icon
    for base in ICON_DIRS:
        path = os.path.abspath(os.path.join(base, name + ".svg"))
        if os.path.exists(path):
            return QIcon(path)
    return QIcon()


def themed_icon(*names: str) -> QIcon:
    """First available freedesktop/Breeze icon out of ``names``."""
    for name in names:
        icon = QIcon.fromTheme(name)
        if not icon.isNull():
            return icon
    return QIcon()


def create_app(app_id: str, display_name: str, icon_name: str) -> QApplication:
    """Create the QApplication with NovaOS metadata.

    ``app_id`` must match the .desktop file name (without suffix) so that
    Plasma's task manager can associate windows with their launcher.
    """
    app = QApplication.instance() or QApplication(sys.argv)
    app.setApplicationName(app_id.split(".")[-1])
    app.setApplicationDisplayName(display_name)
    app.setApplicationVersion(__version__)
    app.setOrganizationName("NovaOS")
    app.setOrganizationDomain("novaos.org")
    app.setDesktopFileName(app_id)
    app.setWindowIcon(nova_icon(icon_name))

    # Qt's own strings (standard buttons, file dialogs, ...)
    translator = QTranslator(app)
    path = QLibraryInfo.path(QLibraryInfo.LibraryPath.TranslationsPath)
    if translator.load(QLocale.system(), "qtbase", "_", path):
        app.installTranslator(translator)

    style.apply(app)
    return app
