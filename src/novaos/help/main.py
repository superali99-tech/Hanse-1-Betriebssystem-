"""Nova Help – offline help for NovaOS (HTML pages, searchable)."""

from __future__ import annotations

import html
import os
import re
import sys

from PyQt6.QtCore import QUrl, Qt
from PyQt6.QtWidgets import (
    QHBoxLayout,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QSplitter,
    QTextBrowser,
    QVBoxLayout,
    QWidget,
)

from novaos.common.app import create_app
from novaos.common.i18n import LANG, _

HELP_DIRS = ["/usr/share/novaos/help", os.path.join(os.path.dirname(__file__), "..", "..", "data", "help")]


def help_root() -> str:
    for base in HELP_DIRS:
        for lang in (LANG, "en", "de"):
            path = os.path.abspath(os.path.join(base, lang))
            if os.path.isdir(path):
                return path
    return ""


def page_title(path: str) -> str:
    try:
        with open(path, encoding="utf-8") as fh:
            m = re.search(r"<title>(.*?)</title>", fh.read(), re.S)
            return html.unescape(m.group(1).strip()) if m else os.path.basename(path)
    except OSError:
        return os.path.basename(path)


class HelpWindow(QWidget):
    def __init__(self, start: str | None = None):
        super().__init__()
        self.setWindowTitle(_("Nova Help"))
        self.resize(1050, 720)
        self.root = help_root()
        lay = QHBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        split = QSplitter()
        lay.addWidget(split)
        side = QWidget()
        sl = QVBoxLayout(side)
        sl.setContentsMargins(10, 12, 6, 10)
        self.search = QLineEdit()
        self.search.setObjectName("NovaSearch")
        self.search.setPlaceholderText(_("Search help…"))
        self.search.setClearButtonEnabled(True)
        self.search.textChanged.connect(self.filter)
        self.topics = QListWidget()
        self.topics.setObjectName("NovaSidebar")
        self.topics.currentItemChanged.connect(self._open_item)
        sl.addWidget(self.search)
        sl.addWidget(self.topics, 1)
        split.addWidget(side)
        self.view = QTextBrowser()
        self.view.setOpenExternalLinks(True)
        self.view.setOpenLinks(True)
        self.view.document().setDocumentMargin(28)
        self.view.setSearchPaths([self.root])
        split.addWidget(self.view)
        split.setSizes([280, 770])
        self.pages = sorted(f for f in os.listdir(self.root) if f.endswith(".html")) if self.root else []
        self._texts = {}
        for f in self.pages:
            with open(os.path.join(self.root, f), encoding="utf-8") as fh:
                self._texts[f] = re.sub(r"<[^>]+>", " ", fh.read()).lower()
        self.filter("")
        if start:
            for i in range(self.topics.count()):
                if self.topics.item(i).data(Qt.ItemDataRole.UserRole).startswith(start):
                    self.topics.setCurrentRow(i)
                    break
        elif self.topics.count():
            self.topics.setCurrentRow(0)
        if not self.root:
            self.view.setHtml("<h2>" + _("No help pages installed.") + "</h2>")

    def filter(self, text: str) -> None:
        q = text.strip().lower()
        self.topics.clear()
        for f in self.pages:
            if q and q not in self._texts[f]:
                continue
            item = QListWidgetItem(page_title(os.path.join(self.root, f)))
            item.setData(Qt.ItemDataRole.UserRole, f)
            self.topics.addItem(item)
        if q and self.topics.count():
            self.topics.setCurrentRow(0)

    def _open_item(self, item: QListWidgetItem | None, _prev=None) -> None:
        if item:
            self.view.setSource(QUrl.fromLocalFile(os.path.join(self.root, item.data(Qt.ItemDataRole.UserRole))))
            q = self.search.text().strip()
            if q:
                self.view.find(q)


def main() -> int:
    app = create_app("org.novaos.Help", _("Nova Help"), "nova-help")
    start = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else None
    w = HelpWindow(start)
    w.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
