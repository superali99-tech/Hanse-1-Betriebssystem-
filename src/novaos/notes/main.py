"""Nova Notes – simple Markdown notes, stored as plain files.

Notes are ordinary ``.md`` files in ``~/Documents/Notes`` (the localized XDG
documents folder), so they can also be opened, synced or backed up with any
other tool. Changes are saved automatically.
"""

from __future__ import annotations

import os
import re
import sys
import time

from PyQt6.QtCore import QFileSystemWatcher, QStandardPaths, Qt, QTimer
from PyQt6.QtGui import QAction, QFont, QKeySequence
from PyQt6.QtWidgets import (
    QFileDialog,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QSplitter,
    QStackedWidget,
    QTextBrowser,
    QToolBar,
    QVBoxLayout,
    QWidget,
)

from novaos.common.app import create_app, themed_icon
from novaos.common.i18n import _


def notes_dir() -> str:
    docs = QStandardPaths.writableLocation(QStandardPaths.StandardLocation.DocumentsLocation) or os.path.expanduser("~/Documents")
    path = os.path.join(docs, _("Notes"))
    os.makedirs(path, exist_ok=True)
    return path


def safe_filename(title: str) -> str:
    name = re.sub(r"[\\/:*?\"<>|\n\r\t]+", " ", title).strip()[:80].strip(". ")
    return name or _("Untitled")


class NoteStore:
    """File based note storage (one Markdown file per note)."""

    def __init__(self, directory: str):
        self.dir = directory

    def list(self) -> list[str]:
        files = [os.path.join(self.dir, f) for f in os.listdir(self.dir) if f.endswith((".md", ".txt"))]
        files.sort(key=lambda p: os.path.getmtime(p), reverse=True)
        return files

    @staticmethod
    def read(path: str) -> str:
        with open(path, encoding="utf-8", errors="replace") as fh:
            return fh.read()

    @staticmethod
    def title_of(text: str, fallback: str) -> str:
        for line in text.splitlines():
            line = line.strip().lstrip("#").strip()
            if line:
                return line[:120]
        return fallback

    def save(self, path: str, text: str) -> str:
        """Save text; renames the file when the title (first line) changed."""
        title = self.title_of(text, _("Untitled"))
        wanted = os.path.join(self.dir, safe_filename(title) + ".md")
        if wanted != path and os.path.exists(path):
            wanted = self._unique(wanted, exclude=path)
            os.replace(path, wanted)
            path = wanted
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as fh:
            fh.write(text)
        os.replace(tmp, path)
        return path

    def create(self) -> str:
        path = self._unique(os.path.join(self.dir, _("New note") + ".md"))
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("# " + _("New note") + "\n\n")
        return path

    @staticmethod
    def _unique(path: str, exclude: str | None = None) -> str:
        base, ext = os.path.splitext(path)
        candidate, n = path, 2
        while os.path.exists(candidate) and candidate != exclude:
            candidate = f"{base} ({n}){ext}"
            n += 1
        return candidate


class NotesWindow(QMainWindow):
    def __init__(self, store: NoteStore):
        super().__init__()
        self.store = store
        self.current: str | None = None
        self._dirty = False
        self.setWindowTitle(_("Nova Notes"))
        self.resize(1100, 720)

        tb = QToolBar()
        tb.setMovable(False)
        tb.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextBesideIcon)
        self.addToolBar(tb)
        act_new = QAction(themed_icon("document-new"), _("New note"), self, shortcut=QKeySequence.StandardKey.New)
        act_new.triggered.connect(self.new_note)
        act_del = QAction(themed_icon("edit-delete"), _("Delete"), self, shortcut=QKeySequence("Ctrl+Del"))
        act_del.triggered.connect(self.delete_note)
        self.act_preview = QAction(themed_icon("view-preview"), _("Preview"), self, checkable=True,
                                   shortcut=QKeySequence("Ctrl+P"))
        self.act_preview.toggled.connect(self.toggle_preview)
        act_export = QAction(themed_icon("document-export"), _("Export…"), self)
        act_export.triggered.connect(self.export_note)
        act_folder = QAction(themed_icon("folder-open"), _("Open folder"), self)
        act_folder.triggered.connect(lambda: __import__("subprocess").Popen(["xdg-open", self.store.dir]))
        for a in (act_new, act_del, self.act_preview, act_export, act_folder):
            tb.addAction(a)
        QAction(self, shortcut=QKeySequence.StandardKey.Save, triggered=self.save_now)

        split = QSplitter()
        self.setCentralWidget(split)
        left = QWidget()
        ll = QVBoxLayout(left)
        ll.setContentsMargins(8, 8, 4, 8)
        self.search = QLineEdit()
        self.search.setObjectName("NovaSearch")
        self.search.setPlaceholderText(_("Search notes…"))
        self.search.setClearButtonEnabled(True)
        self.search.textChanged.connect(self.refresh_list)
        self.list = QListWidget()
        self.list.currentItemChanged.connect(self._select)
        self.count = QLabel()
        self.count.setObjectName("NovaMuted")
        ll.addWidget(self.search)
        ll.addWidget(self.list, 1)
        ll.addWidget(self.count)
        split.addWidget(left)

        self.stack = QStackedWidget()
        self.editor = QPlainTextEdit()
        f = QFont("Inter", 12)
        self.editor.setFont(f)
        self.editor.setTabStopDistance(28)
        self.editor.textChanged.connect(self._changed)
        self.preview = QTextBrowser()
        self.preview.setOpenExternalLinks(True)
        self.stack.addWidget(self.editor)
        self.stack.addWidget(self.preview)
        right = QWidget()
        rl = QVBoxLayout(right)
        rl.setContentsMargins(4, 8, 8, 8)
        rl.addWidget(self.stack, 1)
        self.status = QLabel()
        self.status.setObjectName("NovaMuted")
        rl.addWidget(self.status)
        split.addWidget(right)
        split.setSizes([300, 800])

        self._save_timer = QTimer(self, singleShot=True, interval=800)
        self._save_timer.timeout.connect(self.save_now)
        self._watcher = QFileSystemWatcher([self.store.dir], self)
        self._watcher.directoryChanged.connect(lambda _p: QTimer.singleShot(200, self.refresh_list))

        self.refresh_list()
        if self.list.count() == 0:
            self.new_note()
        else:
            self.list.setCurrentRow(0)

    # ---------------------------------------------------------------- list
    def refresh_list(self) -> None:
        query = self.search.text().strip().lower()
        current = self.current
        self.list.blockSignals(True)
        self.list.clear()
        files = self.store.list()
        shown = 0
        for path in files:
            try:
                text = self.store.read(path)
            except OSError:
                continue
            if query and query not in text.lower():
                continue
            title = self.store.title_of(text, os.path.basename(path))
            body = " ".join(l.strip() for l in text.splitlines()[1:] if l.strip())[:80]
            when = time.strftime("%d.%m.%Y %H:%M", time.localtime(os.path.getmtime(path)))
            item = QListWidgetItem(f"{title}\n{when}  {body}")
            item.setData(Qt.ItemDataRole.UserRole, path)
            item.setToolTip(path)
            self.list.addItem(item)
            shown += 1
            if path == current:
                self.list.setCurrentItem(item)
        self.list.blockSignals(False)
        self.count.setText(_("{n} notes").format(n=shown))

    def _select(self, item: QListWidgetItem | None, _prev=None) -> None:
        if item is None:
            return
        path = item.data(Qt.ItemDataRole.UserRole)
        if path == self.current:
            return
        self.save_now()
        self.current = path
        try:
            text = self.store.read(path)
        except OSError as exc:
            QMessageBox.warning(self, _("Nova Notes"), str(exc))
            return
        self.editor.blockSignals(True)
        self.editor.setPlainText(text)
        self.editor.blockSignals(False)
        self._dirty = False
        self._update_preview()
        self.status.setText(path)

    # ------------------------------------------------------------- editing
    def _changed(self) -> None:
        self._dirty = True
        self.status.setText(_("Editing…"))
        self._save_timer.start()

    def save_now(self) -> None:
        if not self._dirty or not self.current:
            return
        try:
            new_path = self.store.save(self.current, self.editor.toPlainText())
        except OSError as exc:
            self.status.setText(_("Could not save: ") + str(exc))
            return
        self.current = new_path
        self._dirty = False
        self.status.setText(_("Saved") + " · " + new_path)
        self.refresh_list()

    def new_note(self) -> None:
        self.save_now()
        path = self.store.create()
        self.search.clear()
        self.current = None
        self.refresh_list()
        for i in range(self.list.count()):
            if self.list.item(i).data(Qt.ItemDataRole.UserRole) == path:
                self.list.setCurrentRow(i)
        self.act_preview.setChecked(False)
        self.editor.setFocus()
        cur = self.editor.textCursor()
        cur.movePosition(cur.MoveOperation.End)
        self.editor.setTextCursor(cur)

    def delete_note(self) -> None:
        if not self.current:
            return
        title = self.store.title_of(self.editor.toPlainText(), os.path.basename(self.current))
        if QMessageBox.question(self, _("Delete note"), _("Move “{title}” to the trash?").format(title=title)) \
                != QMessageBox.StandardButton.Yes:
            return
        from PyQt6.QtCore import QFile

        if not QFile.moveToTrash(self.current):
            os.unlink(self.current)
        self.current = None
        self._dirty = False
        self.refresh_list()
        if self.list.count():
            self.list.setCurrentRow(0)
        else:
            self.new_note()

    def toggle_preview(self, on: bool) -> None:
        self._update_preview()
        self.stack.setCurrentIndex(1 if on else 0)

    def _update_preview(self) -> None:
        self.preview.setMarkdown(self.editor.toPlainText())

    def export_note(self) -> None:
        if not self.current:
            return
        path, flt = QFileDialog.getSaveFileName(self, _("Export note"), os.path.expanduser("~"),
                                                _("PDF (*.pdf);;HTML (*.html);;Markdown (*.md)"))
        if not path:
            return
        doc = self.preview.document()
        self._update_preview()
        if path.endswith(".pdf"):
            from PyQt6.QtGui import QPageSize
            from PyQt6.QtPrintSupport import QPrinter

            printer = QPrinter(QPrinter.PrinterMode.HighResolution)
            printer.setOutputFormat(QPrinter.OutputFormat.PdfFormat)
            printer.setPageSize(QPageSize(QPageSize.PageSizeId.A4))
            printer.setOutputFileName(path)
            doc.print(printer)
        elif path.endswith(".html"):
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(doc.toHtml())
        else:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(self.editor.toPlainText())
        self.status.setText(_("Exported to {path}").format(path=path))

    def closeEvent(self, event):  # noqa: N802
        self.save_now()
        super().closeEvent(event)


def main() -> int:
    app = create_app("org.novaos.Notes", _("Nova Notes"), "nova-notes")
    win = NotesWindow(NoteStore(notes_dir()))
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
