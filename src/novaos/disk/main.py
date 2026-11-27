"""Nova Disk – disk analysis and file rescue (NovaOS 2).

Overview of the drives (space, health), what fills the disk (folders, file types, the
largest files), the trash, and rescuing deleted files from drives with PhotoRec.
"""

from __future__ import annotations

import os
import subprocess
import sys
import threading
import time

from PyQt6.QtCore import QObject, QProcess, Qt, QTimer, pyqtSignal
from PyQt6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from novaos.common.app import create_app, nova_icon, themed_icon
from novaos.common.i18n import _
from novaos.disk import analysis, recovery

HELPER = "/usr/libexec/novaos/nova-disk-helper"
CAT_COLORS = {"images": "#E8833A", "videos": "#7C5CFF", "music": "#22A06B", "documents": "#3D7BFF",
              "archives": "#D9B33A", "other": "#8A8FA3"}


def cat_name(c: str) -> str:
    return {"images": _("Pictures"), "videos": _("Videos"), "music": _("Music"), "documents": _("Documents"),
            "archives": _("Archives"), "other": _("Other")}.get(c, c)


def german() -> bool:
    lang = os.environ.get("LANGUAGE") or os.environ.get("LC_ALL") or os.environ.get("LC_MESSAGES") or os.environ.get("LANG") or ""
    return lang.startswith("de")


def hs(n: int) -> str:
    return analysis.human_size(n, german())


def bar(fraction: float, color: str = "#7C5CFF", height: int = 10) -> QFrame:
    """A rounded usage bar (fraction 0..1)."""
    outer = QFrame()
    outer.setFixedHeight(height)
    outer.setStyleSheet(f"background: rgba(128,128,140,0.22); border-radius: {height // 2}px;")
    inner = QFrame(outer)
    inner.setStyleSheet(f"background: qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 {color}, stop:1 #3D7BFF);"
                        f" border-radius: {height // 2}px;")
    outer.resizeEvent = lambda e, o=outer, i=inner, f=fraction: i.setGeometry(0, 0, max(height, int(o.width() * max(0.0, min(1.0, f)))), height)
    return outer


def page(title: str, subtitle: str) -> tuple[QScrollArea, QVBoxLayout]:
    area = QScrollArea()
    area.setWidgetResizable(True)
    host = QWidget()
    lay = QVBoxLayout(host)
    lay.setContentsMargins(28, 22, 28, 22)
    lay.setSpacing(14)
    t = QLabel(title)
    t.setObjectName("NovaTitle")
    s = QLabel(subtitle)
    s.setObjectName("NovaMuted")
    s.setWordWrap(True)
    lay.addWidget(t)
    lay.addWidget(s)
    area.setWidget(host)
    return area, lay


def clear(lay) -> None:
    while lay.count():
        it = lay.takeAt(0)
        if it.widget():
            it.widget().deleteLater()
        elif it.layout():
            clear(it.layout())


class _Bridge(QObject):
    scanned = pyqtSignal(object)
    scan_progress = pyqtSignal(int, int)
    drives = pyqtSignal(object)


class DiskWindow(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Nova Disk")
        self.resize(1080, 760)
        self.bridge = _Bridge()
        self.bridge.scanned.connect(self._show_scan)
        self.bridge.scan_progress.connect(lambda n, b: self.scan_status.setText(
            _("{n} files · {size} so far …").format(n=f"{n:,}".replace(",", "."), size=hs(b))))
        self.bridge.drives.connect(self._show_drives)
        self.cancel_scan = False
        self.rescue_proc: QProcess | None = None
        self.rescue_out = ""

        root = QHBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        side = QWidget()
        side.setFixedWidth(230)
        sl = QVBoxLayout(side)
        sl.setContentsMargins(14, 22, 10, 14)
        logo = QHBoxLayout()
        ic = QLabel()
        ic.setPixmap(nova_icon("nova-disk").pixmap(36, 36))
        logo.addWidget(ic)
        nm = QLabel("<b>Nova</b> Disk")
        nm.setStyleSheet("font-size: 16pt;")
        logo.addWidget(nm, 1)
        sl.addLayout(logo)
        sl.addSpacing(14)
        self.nav = QListWidget()
        self.nav.setObjectName("NovaSidebar")
        for icon, text in (("drive-harddisk", _("Overview")), ("view-statistics", _("Analyse storage")),
                           ("user-trash", _("Trash")), ("edit-undo", _("Rescue files"))):
            self.nav.addItem(QListWidgetItem(themed_icon(icon), text))
        sl.addWidget(self.nav, 1)
        root.addWidget(side)
        self.stack = QStackedWidget()
        root.addWidget(self.stack, 1)

        self._build_overview()
        self._build_analysis()
        self._build_trash()
        self._build_rescue()
        self.nav.currentRowChanged.connect(self._nav)
        self.nav.setCurrentRow(0)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self._rescue_tick)

    def _nav(self, row: int) -> None:
        self.stack.setCurrentIndex(row)
        if row == 0:
            self.load_drives()
        elif row == 2:
            self.fill_trash()
        elif row == 3:
            self.fill_sources()

    # ------------------------------------------------------------ overview
    def _build_overview(self) -> None:
        area, lay = page(_("Your drives"), _("How full every drive is – and whether it is healthy."))
        self.drives_lay = QVBoxLayout()
        self.drives_lay.setSpacing(12)
        lay.addLayout(self.drives_lay)
        lay.addStretch(1)
        self.stack.addWidget(area)

    def load_drives(self) -> None:
        threading.Thread(target=lambda: self.bridge.drives.emit(analysis.drives()), daemon=True).start()

    def _show_drives(self, drives) -> None:
        clear(self.drives_lay)
        if not drives:
            self.drives_lay.addWidget(QLabel(_("No drives found.")))
        for d in drives:
            card = QFrame()
            card.setObjectName("NovaPanel")
            cl = QVBoxLayout(card)
            cl.setContentsMargins(20, 16, 20, 16)
            head = QHBoxLayout()
            icon = QLabel()
            icon.setPixmap(themed_icon({"usb": "drive-removable-media-usb", "sd": "media-flash-sd-mmc",
                                        "nvme": "drive-harddisk-solidstate"}.get(d.kind, "drive-harddisk")).pixmap(44, 44))
            head.addWidget(icon)
            kind = {"usb": _("USB drive"), "sd": _("Memory card"), "nvme": _("SSD (NVMe)")}.get(d.kind, _("Hard disk / SSD"))
            title = QLabel(f"<b>{d.model or d.name}</b><br><span style='color:gray'>{kind} · {hs(d.size)} · {d.path}</span>")
            head.addWidget(title, 1)
            hb = QPushButton(themed_icon("security-high"), _("Check health"))
            hb.clicked.connect(lambda _c=False, dev=d.path, b=hb: self.check_health(dev, b))
            head.addWidget(hb)
            cl.addLayout(head)
            for p in d.partitions:
                if not p["mounts"]:
                    continue
                total, used, _free = analysis.usage(p["mounts"][0])
                if not total:
                    continue
                row = QHBoxLayout()
                name = p["label"] or p["mounts"][0]
                lbl = QLabel(f"{name}  <span style='color:gray'>{p['fstype']}</span>")
                lbl.setMinimumWidth(200)
                row.addWidget(lbl)
                row.addWidget(bar(used / total, "#7C5CFF" if used / total < 0.9 else "#E5484D"), 1)
                row.addWidget(QLabel(_("{used} of {total} used").format(used=hs(used), total=hs(total))))
                cl.addLayout(row)
            self.drives_lay.addWidget(card)

    def check_health(self, device: str, button: QPushButton) -> None:
        if not os.path.exists(HELPER):
            return
        button.setEnabled(False)
        button.setText(_("Checking…"))
        proc = QProcess(self)

        def done(code, _st):
            import json
            button.setEnabled(True)
            try:
                data = json.loads(bytes(proc.readAllStandardOutput()).decode() or "{}")
            except ValueError:
                data = {}
            st = data.get("smart_status", {})
            if "passed" not in st:
                button.setText(_("Health unknown"))
                button.setToolTip(_("This drive does not report its health (e.g. many USB sticks)."))
            elif st["passed"]:
                temp = data.get("temperature", {}).get("current")
                button.setText("✓ " + _("Healthy") + (f" · {temp} °C" if temp else ""))
            else:
                button.setText("⚠ " + _("Attention: back up your data"))
                button.setStyleSheet("color: #E5484D; font-weight: 700;")
        proc.finished.connect(done)
        proc.start("/usr/bin/pkexec", [HELPER, "health", device])

    # ------------------------------------------------------------ analysis
    def _build_analysis(self) -> None:
        area, lay = page(_("What fills your disk?"), _("Nova Disk measures folders and files and shows what takes the most space."))
        row = QHBoxLayout()
        for text, path in ((_("Home folder"), os.path.expanduser("~")), (_("Whole system"), "/")):
            b = QPushButton(themed_icon("folder-home" if path != "/" else "drive-harddisk"), text)
            b.clicked.connect(lambda _c=False, p=path: self.start_scan(p))
            row.addWidget(b)
        other = QPushButton(themed_icon("folder-open"), _("Choose folder…"))
        other.clicked.connect(self._choose_scan)
        row.addWidget(other)
        row.addStretch(1)
        self.btn_stop_scan = QPushButton(_("Stop"))
        self.btn_stop_scan.clicked.connect(lambda: setattr(self, "cancel_scan", True))
        self.btn_stop_scan.hide()
        row.addWidget(self.btn_stop_scan)
        lay.addLayout(row)
        self.scan_status = QLabel(_("Choose what to analyse."))
        self.scan_status.setObjectName("NovaMuted")
        lay.addWidget(self.scan_status)
        self.result_lay = QVBoxLayout()
        self.result_lay.setSpacing(12)
        lay.addLayout(self.result_lay)
        lay.addStretch(1)
        self.stack.addWidget(area)

    def _choose_scan(self) -> None:
        d = QFileDialog.getExistingDirectory(self, _("Choose folder…"), os.path.expanduser("~"))
        if d:
            self.start_scan(d)

    def start_scan(self, path: str) -> None:
        self.cancel_scan = False
        self.btn_stop_scan.show()
        clear(self.result_lay)
        self.scan_status.setText(_("Measuring {path} …").format(path=path))

        def work():
            r = analysis.scan(path, progress=lambda n, b: self.bridge.scan_progress.emit(n, b),
                              cancelled=lambda: self.cancel_scan)
            self.bridge.scanned.emit(r)
        threading.Thread(target=work, daemon=True).start()

    def _show_scan(self, r: analysis.ScanResult) -> None:
        self.btn_stop_scan.hide()
        self.scan_status.setText(_("{path}: {size} in {n} files").format(
            path=r.root, size=hs(r.total), n=f"{r.files:,}".replace(",", ".")) + (" · " + _("stopped") if self.cancel_scan else ""))
        clear(self.result_lay)
        if not r.total:
            return
        # file types as a coloured strip with chips
        types = QFrame()
        types.setObjectName("NovaPanel")
        tl = QVBoxLayout(types)
        tl.setContentsMargins(20, 14, 20, 14)
        h = QLabel(_("By file type"))
        h.setObjectName("NovaSubtitle")
        tl.addWidget(h)
        strip = QHBoxLayout()
        strip.setSpacing(2)
        cats = sorted(r.by_category.items(), key=lambda kv: -kv[1])
        for c, size in cats:
            seg = QFrame()
            seg.setFixedHeight(16)
            seg.setStyleSheet(f"background: {CAT_COLORS.get(c, '#888')}; border-radius: 4px;")
            strip.addWidget(seg, max(1, int(1000 * size / r.total)))
        tl.addLayout(strip)
        chips = QHBoxLayout()
        for c, size in cats:
            chip = QLabel(f"<span style='color:{CAT_COLORS.get(c, '#888')}'>●</span> {cat_name(c)}  <b>{hs(size)}</b>")
            chips.addWidget(chip)
        chips.addStretch(1)
        tl.addLayout(chips)
        self.result_lay.addWidget(types)
        # biggest folders
        folders = QFrame()
        folders.setObjectName("NovaPanel")
        fl = QVBoxLayout(folders)
        fl.setContentsMargins(20, 14, 20, 14)
        h = QLabel(_("Biggest folders"))
        h.setObjectName("NovaSubtitle")
        fl.addWidget(h)
        top = r.top_children(10)
        biggest = top[0][1] if top else 1
        for name, size in top:
            if not size or (size < r.total * 0.002 and name.startswith(".")):
                continue   # tiny hidden files/folders are noise here
            row = QHBoxLayout()
            n = QLabel(name)
            n.setMinimumWidth(220)
            row.addWidget(n)
            row.addWidget(bar(size / max(1, biggest)), 1)
            pct = QLabel(f"{hs(size)}  ·  {100 * size / r.total:.0f} %".replace(".", "," if german() else "."))
            pct.setMinimumWidth(140)
            pct.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
            row.addWidget(pct)
            fl.addLayout(row)
        self.result_lay.addWidget(folders)
        # largest files
        files = QFrame()
        files.setObjectName("NovaPanel")
        gl = QVBoxLayout(files)
        gl.setContentsMargins(20, 14, 20, 14)
        h = QLabel(_("Largest files"))
        h.setObjectName("NovaSubtitle")
        gl.addWidget(h)
        home = os.path.expanduser("~")
        for size, path in r.largest[:15]:
            row = QHBoxLayout()
            dot = QLabel(f"<span style='color:{CAT_COLORS.get(analysis.category(path), '#888')}'>●</span>")
            row.addWidget(dot)
            n = QLabel(f"<b>{os.path.basename(path)}</b><br><span style='color:gray'>{os.path.dirname(path)}</span>")
            row.addWidget(n, 1)
            row.addWidget(QLabel(hs(size)))
            ic = themed_icon("folder-open")
            show = QPushButton(ic, "" if not ic.isNull() else _("Show"))
            show.setToolTip(_("Show in folder"))
            show.clicked.connect(lambda _c=False, p=path: subprocess.Popen(["xdg-open", os.path.dirname(p)]))
            row.addWidget(show)
            if path.startswith(home + "/"):
                ic = themed_icon("user-trash")
                rm = QPushButton(ic, "" if not ic.isNull() else _("Delete"))
                rm.setToolTip(_("Move to trash (can be restored)"))
                rm.clicked.connect(lambda _c=False, p=path, w=n, b=rm: self._trash_file(p, w, b))
                row.addWidget(rm)
            gl.addLayout(row)
        self.result_lay.addWidget(files)

    def _trash_file(self, path: str, label: QLabel, button: QPushButton) -> None:
        if QMessageBox.question(self, "Nova Disk", _("Move “{name}” to the trash? You can restore it there.").format(
                name=os.path.basename(path))) != QMessageBox.StandardButton.Yes:
            return
        try:
            recovery.to_trash(path)
            label.setText(f"<s>{os.path.basename(path)}</s><br><span style='color:gray'>{_('in the trash')}</span>")
            button.setEnabled(False)
        except OSError as exc:
            QMessageBox.warning(self, "Nova Disk", str(exc))

    # --------------------------------------------------------------- trash
    def _build_trash(self) -> None:
        area, lay = page(_("Trash"), _("Deleted by mistake? Put files back where they were."))
        self.trash_lay = QVBoxLayout()
        self.trash_lay.setSpacing(8)
        lay.addLayout(self.trash_lay)
        lay.addStretch(1)
        self.stack.addWidget(area)

    def fill_trash(self) -> None:
        clear(self.trash_lay)
        items = recovery.trash_items()
        if not items:
            ok = QLabel("✓ " + _("The trash is empty."))
            ok.setObjectName("NovaSubtitle")
            self.trash_lay.addWidget(ok)
            hint = QLabel(_("Files deleted for good or from USB sticks and memory cards can often still be rescued: "
                            "see “Rescue files”."))
            hint.setObjectName("NovaMuted")
            hint.setWordWrap(True)
            self.trash_lay.addWidget(hint)
            return
        for it in items[:200]:
            card = QFrame()
            card.setObjectName("NovaPanel")
            row = QHBoxLayout(card)
            row.setContentsMargins(14, 10, 14, 10)
            dot = QLabel(f"<span style='color:{CAT_COLORS.get(analysis.category(it.name), '#888')}; font-size:16pt'>●</span>")
            row.addWidget(dot)
            when = it.deleted.replace("T", " ")[:16]
            row.addWidget(QLabel(f"<b>{os.path.basename(it.original) or it.name}</b><br>"
                                 f"<span style='color:gray'>{os.path.dirname(it.original)} · {when} · {hs(it.size)}</span>"), 1)
            b = QPushButton(themed_icon("edit-undo"), _("Restore"))
            b.setObjectName("NovaPrimary")
            b.clicked.connect(lambda _c=False, i=it: self._restore(i))
            row.addWidget(b)
            self.trash_lay.addWidget(card)

    def _restore(self, item) -> None:
        try:
            target = recovery.restore(item)
            QMessageBox.information(self, "Nova Disk", _("Restored to:\n{path}").format(path=target))
        except OSError as exc:
            QMessageBox.warning(self, "Nova Disk", str(exc))
        self.fill_trash()

    # -------------------------------------------------------------- rescue
    def _build_rescue(self) -> None:
        area, lay = page(_("Rescue files"),
                         _("Gets deleted photos, videos and documents back from USB sticks, memory cards and disks – "
                           "even after formatting. Nova Disk only reads the drive and saves what it finds on another drive."))
        box = QFrame()
        box.setObjectName("NovaPanel")
        bl = QVBoxLayout(box)
        bl.setContentsMargins(20, 16, 20, 16)
        bl.setSpacing(10)
        bl.addWidget(QLabel("<b>1.</b> " + _("Rescue from which drive?")))
        self.source = QComboBox()
        self.source.setMinimumHeight(36)
        bl.addWidget(self.source)
        bl.addWidget(QLabel("<b>2.</b> " + _("What should be found?")))
        types = QHBoxLayout()
        self.groups = {}
        for key, text in (("photos", _("Photos")), ("videos", _("Videos")), ("documents", _("Documents")),
                          ("music", _("Music")), ("archives", _("Archives"))):
            cb = QCheckBox(text)
            cb.setChecked(key in ("photos", "videos", "documents"))
            self.groups[key] = cb
            types.addWidget(cb)
        types.addStretch(1)
        bl.addLayout(types)
        bl.addWidget(QLabel("<b>3.</b> " + _("Save the rescued files where? (on another drive)")))
        trow = QHBoxLayout()
        self.target = QLabel(os.path.join(os.path.expanduser("~"), _("Rescued files")))
        trow.addWidget(self.target, 1)
        choose = QPushButton(themed_icon("folder-open"), _("Choose…"))
        choose.clicked.connect(self._choose_target)
        trow.addWidget(choose)
        bl.addLayout(trow)
        self.rescue_msg = QLabel("")
        self.rescue_msg.setWordWrap(True)
        bl.addWidget(self.rescue_msg)
        arow = QHBoxLayout()
        self.btn_rescue = QPushButton(themed_icon("edit-find"), _("Start search"))
        self.btn_rescue.setObjectName("NovaPrimary")
        self.btn_rescue.clicked.connect(self.start_rescue)
        self.btn_stop = QPushButton(_("Stop"))
        self.btn_stop.clicked.connect(self.stop_rescue)
        self.btn_stop.hide()
        self.btn_open = QPushButton(themed_icon("folder-open"), _("Open rescued files"))
        self.btn_open.clicked.connect(lambda: subprocess.Popen(["xdg-open", self.rescue_out]))
        self.btn_open.hide()
        arow.addWidget(self.btn_rescue)
        arow.addWidget(self.btn_stop)
        arow.addWidget(self.btn_open)
        arow.addStretch(1)
        bl.addLayout(arow)
        self.rescue_bar = QProgressBar()
        self.rescue_bar.setRange(0, 0)
        self.rescue_bar.hide()
        bl.addWidget(self.rescue_bar)
        lay.addWidget(box)
        tips = QLabel(_("Tips: use the drive as little as possible until the files are rescued – every new file can "
                        "overwrite deleted ones. Rescued files get new names (e.g. f1234567.jpg); photos keep their "
                        "date and camera data."))
        tips.setObjectName("NovaMuted")
        tips.setWordWrap(True)
        lay.addWidget(tips)
        lay.addStretch(1)
        self.stack.addWidget(area)

    def fill_sources(self) -> None:
        if self.rescue_proc is not None:
            return
        self.source.clear()
        self._source_mounts = {}
        # USB sticks and memory cards first - that is where most rescues happen
        for d in sorted(analysis.drives(), key=lambda d: d.kind not in ("usb", "sd")):
            kind = {"usb": _("USB drive"), "sd": _("Memory card"), "nvme": _("SSD (NVMe)")}.get(d.kind, _("Hard disk / SSD"))
            parts = d.partitions or [{"path": d.path, "size": d.size, "fstype": "", "label": "", "mounts": []}]
            for p in parts:
                if p["fstype"] in ("swap", "crypto_LUKS") or "/boot/efi" in p["mounts"] or "/boot" in p["mounts"]:
                    continue
                label = p["label"] or (p["mounts"][0] if p["mounts"] else "")
                self.source.addItem(f"{d.model or d.name} – {label or p['path']} ({hs(p['size'])}, {kind})", p["path"])
                self._source_mounts[p["path"]] = [m for q in d.partitions for m in q["mounts"]]

    def _choose_target(self) -> None:
        d = QFileDialog.getExistingDirectory(self, _("Choose…"), os.path.expanduser("~"))
        if d:
            self.target.setText(d)

    def start_rescue(self) -> None:
        dev = self.source.currentData()
        groups = [k for k, cb in self.groups.items() if cb.isChecked()]
        if not dev or not groups:
            self.rescue_msg.setText(_("Choose a drive and at least one kind of file."))
            return
        out = os.path.join(self.target.text(), time.strftime("%Y-%m-%d %H-%M"))
        os.makedirs(out, exist_ok=True)
        if recovery.same_device(out, self._source_mounts.get(dev, [])):
            self.rescue_msg.setText("⚠ " + _("The target folder is on the drive you rescue from. Choose a folder on "
                                             "another drive (e.g. a second USB drive) – otherwise the files you want "
                                             "back could be overwritten."))
            return
        self.rescue_out = out
        self.rescue_msg.setText(_("Searching … this can take from a few minutes to some hours, depending on the size "
                                  "of the drive."))
        self.btn_rescue.hide()
        self.btn_open.hide()
        self.btn_stop.show()
        self.rescue_bar.show()
        self.rescue_proc = QProcess(self)
        self.rescue_proc.finished.connect(self._rescue_done)
        self.rescue_proc.start("/usr/bin/pkexec", [HELPER, "recover", dev, out, *groups])
        self.timer.start(2000)

    def _rescue_tick(self) -> None:
        n = recovery.found_files(self.rescue_out)
        self.rescue_msg.setText(_("Searching … {n} files found so far.").format(n=n))

    def stop_rescue(self) -> None:
        try:
            open(os.path.join(self.rescue_out, recovery.STOP_FILE), "w").close()
        except OSError:
            pass

    def _rescue_done(self, code: int, _st) -> None:
        self.timer.stop()
        out = bytes(self.rescue_proc.readAllStandardOutput()).decode(errors="replace") if self.rescue_proc else ""
        self.rescue_proc = None
        self.rescue_bar.hide()
        self.btn_stop.hide()
        self.btn_rescue.show()
        n = recovery.found_files(self.rescue_out)
        if code in (126, 127):
            self.rescue_msg.setText(_("Cancelled."))
            return
        if "ERROR same drive" in out:
            self.rescue_msg.setText("⚠ " + _("The target folder is on the drive you rescue from. Choose another drive."))
            return
        self.rescue_msg.setText(("✓ " if n else "") + _("{n} files rescued.").format(n=n)
                                + ("" if n else " " + _("Nothing found of the chosen kinds.")))
        self.btn_open.setVisible(n > 0)


def main() -> int:
    app = create_app("org.novaos.Disk", "Nova Disk", "nova-disk")
    win = DiskWindow()
    win.show()
    if "--rescue" in sys.argv:
        win.nav.setCurrentRow(3)
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
