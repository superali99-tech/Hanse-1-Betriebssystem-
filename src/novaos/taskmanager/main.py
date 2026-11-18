"""Nova Task Manager – processes, performance graphs and error logs."""

from __future__ import annotations

import collections
import os
import signal
import subprocess
import sys
import time

import psutil
from PyQt6.QtCore import QAbstractTableModel, QModelIndex, QPointF, QSortFilterProxyModel, Qt, QTimer
from PyQt6.QtGui import QColor, QKeySequence, QPainter, QPainterPath, QPen, QShortcut
from PyQt6.QtWidgets import (
    QAbstractItemView,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QTableView,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from novaos.common import style
from novaos.common.app import create_app, themed_icon
from novaos.common.i18n import _
from novaos.common.widgets import human_size

INTERVAL_MS = 2000
HISTORY = 60


# ------------------------------------------------------------------ processes
class ProcessModel(QAbstractTableModel):
    COLUMNS = [_("Name"), _("PID"), _("User"), _("CPU %"), _("Memory"), _("Status")]

    def __init__(self):
        super().__init__()
        self.rows: list[tuple] = []
        self._procs: dict[int, psutil.Process] = {}
        self.ncpu = psutil.cpu_count() or 1

    def refresh(self) -> None:
        rows = []
        seen = set()
        for p in psutil.process_iter(["pid", "name", "username", "memory_info", "status", "cmdline"]):
            pid = p.info["pid"]
            seen.add(pid)
            proc = self._procs.setdefault(pid, p)
            try:
                cpu = proc.cpu_percent(None) / self.ncpu
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue
            mem = p.info["memory_info"].rss if p.info["memory_info"] else 0
            name = p.info["name"] or "?"
            cmd = " ".join(p.info["cmdline"] or [])
            rows.append((name, pid, p.info["username"] or "", cpu, mem, p.info["status"] or "", cmd))
        for pid in list(self._procs):
            if pid not in seen:
                del self._procs[pid]
        self.beginResetModel()
        self.rows = rows
        self.endResetModel()

    def rowCount(self, parent=QModelIndex()):  # noqa: N802
        return len(self.rows)

    def columnCount(self, parent=QModelIndex()):  # noqa: N802
        return len(self.COLUMNS)

    def headerData(self, section, orientation, role=Qt.ItemDataRole.DisplayRole):  # noqa: N802
        if orientation == Qt.Orientation.Horizontal and role == Qt.ItemDataRole.DisplayRole:
            return self.COLUMNS[section]
        return None

    def data(self, index, role=Qt.ItemDataRole.DisplayRole):
        row = self.rows[index.row()]
        col = index.column()
        if role == Qt.ItemDataRole.DisplayRole:
            if col == 3:
                return f"{row[3]:.1f}"
            if col == 4:
                return human_size(row[4])
            return str(row[col])
        if role == Qt.ItemDataRole.UserRole:  # sort key
            return row[col]
        if role == Qt.ItemDataRole.ToolTipRole:
            return row[6]
        if role == Qt.ItemDataRole.TextAlignmentRole and col in (1, 3, 4):
            return int(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
        return None


class ProcessFilter(QSortFilterProxyModel):
    def __init__(self):
        super().__init__()
        self.setSortRole(Qt.ItemDataRole.UserRole)
        self.setFilterCaseSensitivity(Qt.CaseSensitivity.CaseInsensitive)
        self.setFilterKeyColumn(-1)


class ProcessesTab(QWidget):
    def __init__(self):
        super().__init__()
        lay = QVBoxLayout(self)
        top = QHBoxLayout()
        self.filter = QLineEdit()
        self.filter.setObjectName("NovaSearch")
        self.filter.setPlaceholderText(_("Search processes…"))
        self.filter.setClearButtonEnabled(True)
        top.addWidget(self.filter, 1)
        self.only_mine = QPushButton(_("Only my processes"))
        self.only_mine.setCheckable(True)
        self.only_mine.setChecked(True)
        top.addWidget(self.only_mine)
        lay.addLayout(top)
        self.model = ProcessModel()
        self.proxy = ProcessFilter()
        self.proxy.setSourceModel(self.model)
        self.view = QTableView()
        self.view.setModel(self.proxy)
        self.view.setSortingEnabled(True)
        self.view.sortByColumn(3, Qt.SortOrder.DescendingOrder)
        self.view.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.view.setSelectionMode(QAbstractItemView.SelectionMode.SingleSelection)
        self.view.verticalHeader().hide()
        self.view.setAlternatingRowColors(True)
        self.view.setShowGrid(False)
        self.view.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        lay.addWidget(self.view, 1)
        bottom = QHBoxLayout()
        self.summary = QLabel()
        self.summary.setObjectName("NovaMuted")
        bottom.addWidget(self.summary, 1)
        b_end = QPushButton(themed_icon("process-stop"), _("End task"))
        b_end.clicked.connect(lambda: self.signal_selected(signal.SIGTERM))
        b_kill = QPushButton(themed_icon("edit-delete"), _("Force quit"))
        b_kill.setObjectName("NovaDanger")
        b_kill.clicked.connect(lambda: self.signal_selected(signal.SIGKILL))
        bottom.addWidget(b_end)
        bottom.addWidget(b_kill)
        lay.addLayout(bottom)
        self.filter.textChanged.connect(self._apply_filter)
        self.only_mine.toggled.connect(lambda _c: self.refresh())
        QShortcut(QKeySequence.StandardKey.Delete, self.view, activated=lambda: self.signal_selected(signal.SIGTERM))
        self.user = psutil.Process().username()

    def _apply_filter(self) -> None:
        self.proxy.setFilterFixedString(self.filter.text())

    def refresh(self) -> None:
        sel = self.selected_pid()
        self.model.refresh()
        if self.only_mine.isChecked():
            self.model.beginResetModel()
            self.model.rows = [r for r in self.model.rows if r[2] == self.user]
            self.model.endResetModel()
        self.summary.setText(_("{n} processes").format(n=len(self.model.rows)))
        if sel is not None:
            for r in range(self.proxy.rowCount()):
                idx = self.proxy.index(r, 1)
                if self.proxy.data(idx, Qt.ItemDataRole.UserRole) == sel:
                    self.view.selectRow(r)
                    break

    def selected_pid(self) -> int | None:
        rows = self.view.selectionModel().selectedRows() if self.view.selectionModel() else []
        if not rows:
            return None
        return self.proxy.data(self.proxy.index(rows[0].row(), 1), Qt.ItemDataRole.UserRole)

    def signal_selected(self, sig: int) -> None:
        pid = self.selected_pid()
        if pid is None:
            return
        try:
            proc = psutil.Process(pid)
            name = proc.name()
        except psutil.NoSuchProcess:
            return
        verb = _("end") if sig == signal.SIGTERM else _("force quit")
        if QMessageBox.question(self, _("Nova Task Manager"),
                                _("Do you want to {verb} “{name}” (PID {pid})? Unsaved data may be lost.").format(
                                    verb=verb, name=name, pid=pid)) != QMessageBox.StandardButton.Yes:
            return
        try:
            os.kill(pid, sig)
        except PermissionError:
            QMessageBox.warning(self, _("Nova Task Manager"),
                                _("This process belongs to another user or the system and cannot be ended."))
        except ProcessLookupError:
            pass
        QTimer.singleShot(500, self.refresh)


# ---------------------------------------------------------------- performance
class Graph(QWidget):
    """Line graph of the last HISTORY samples (0..max)."""

    def __init__(self, color: str, maximum: float | None = 100.0, series: int = 1, colors: list[str] | None = None):
        super().__init__()
        self.setMinimumHeight(110)
        self.data = [collections.deque([0.0] * HISTORY, maxlen=HISTORY) for _ in range(series)]
        self.colors = colors or [color]
        self.maximum = maximum

    def push(self, *values: float) -> None:
        for d, v in zip(self.data, values):
            d.append(v)
        self.update()

    def paintEvent(self, _e):  # noqa: N802
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        r = self.rect().adjusted(1, 1, -1, -1)
        grid = QColor(self.palette().color(self.foregroundRole()))
        grid.setAlphaF(0.10)
        p.setPen(QPen(grid, 1))
        p.drawRoundedRect(r, 10, 10)
        for i in range(1, 4):
            y = r.top() + r.height() * i / 4
            p.drawLine(QPointF(r.left() + 6, y), QPointF(r.right() - 6, y))
        top = self.maximum or max(1.0, max(max(d) for d in self.data) * 1.15)
        for d, col in zip(self.data, self.colors):
            path = QPainterPath()
            step = r.width() / (HISTORY - 1)
            for i, v in enumerate(d):
                pt = QPointF(r.left() + i * step, r.bottom() - (min(v, top) / top) * (r.height() - 4))
                if i == 0:
                    path.moveTo(pt)
                else:
                    path.lineTo(pt)
            fill = QPainterPath(path)
            fill.lineTo(QPointF(r.right(), r.bottom()))
            fill.lineTo(QPointF(r.left(), r.bottom()))
            c = QColor(col)
            c.setAlphaF(0.18)
            p.fillPath(fill, c)
            p.setPen(QPen(QColor(col), 2))
            p.drawPath(path)


class PerfCard(QFrame):
    def __init__(self, title: str, graph: Graph):
        super().__init__()
        self.setObjectName("NovaPanel")
        lay = QVBoxLayout(self)
        lay.setContentsMargins(14, 10, 14, 12)
        head = QHBoxLayout()
        t = QLabel(title)
        t.setObjectName("NovaSubtitle")
        self.value = QLabel()
        self.value.setAlignment(Qt.AlignmentFlag.AlignRight)
        head.addWidget(t)
        head.addWidget(self.value, 1)
        lay.addLayout(head)
        lay.addWidget(graph)
        self.detail = QLabel()
        self.detail.setObjectName("NovaMuted")
        self.detail.setWordWrap(True)
        lay.addWidget(self.detail)
        self.graph = graph


class PerformanceTab(QScrollArea):
    def __init__(self):
        super().__init__()
        self.setWidgetResizable(True)
        host = QWidget()
        self.setWidget(host)
        grid = QGridLayout(host)
        grid.setSpacing(14)
        self.cpu = PerfCard(_("Processor"), Graph(style.ACCENT))
        self.mem = PerfCard(_("Memory"), Graph(style.ACCENT_2, 100.0, 2, [style.ACCENT_2, "#22D3EE"]))
        self.net = PerfCard(_("Network"), Graph("#22A06B", None, 2, ["#22A06B", "#F472B6"]))
        self.disk_io = PerfCard(_("Disk activity"), Graph("#D9822B", None, 2, ["#D9822B", "#A78BFA"]))
        grid.addWidget(self.cpu, 0, 0)
        grid.addWidget(self.mem, 0, 1)
        grid.addWidget(self.net, 1, 0)
        grid.addWidget(self.disk_io, 1, 1)
        self.disks = QFrame()
        self.disks.setObjectName("NovaPanel")
        self.disks_lay = QVBoxLayout(self.disks)
        self.disks_lay.setContentsMargins(14, 10, 14, 12)
        grid.addWidget(self.disks, 2, 0, 1, 2)
        try:
            freq = psutil.cpu_freq()
        except Exception:  # noqa: BLE001
            freq = None
        self.cpu_name = _cpu_model()
        self.cpu_freq = f" · {freq.max / 1000:.1f} GHz" if freq and freq.max else ""
        self._net = psutil.net_io_counters()
        self._disk = psutil.disk_io_counters()
        self._t = time.monotonic()
        self._disk_rows: dict[str, tuple[QLabel, QProgressBar]] = {}
        psutil.cpu_percent(None)

    def refresh(self) -> None:
        now = time.monotonic()
        dt_ = max(0.1, now - self._t)
        self._t = now
        cpu = psutil.cpu_percent(None)
        self.cpu.graph.push(cpu)
        self.cpu.value.setText(f"<b>{cpu:.0f} %</b>")
        load = os.getloadavg()
        self.cpu.detail.setText(f"{self.cpu_name}{self.cpu_freq} · {psutil.cpu_count()} " + _("threads")
                                + f" · {_('Load')}: {load[0]:.2f} {load[1]:.2f} {load[2]:.2f}")
        vm, sw = psutil.virtual_memory(), psutil.swap_memory()
        self.mem.graph.push(vm.percent, sw.percent)
        self.mem.value.setText(f"<b>{vm.percent:.0f} %</b>")
        self.mem.detail.setText(_("RAM: {used} of {total} used").format(used=human_size(vm.total - vm.available), total=human_size(vm.total))
                                + " · " + _("Swap (zram/disk): {used} of {total}").format(used=human_size(sw.used), total=human_size(sw.total)))
        net = psutil.net_io_counters()
        down = (net.bytes_recv - self._net.bytes_recv) / dt_
        up = (net.bytes_sent - self._net.bytes_sent) / dt_
        self._net = net
        self.net.graph.push(down, up)
        self.net.value.setText(f"<b>↓ {human_size(down)}/s</b>  ↑ {human_size(up)}/s")
        self.net.detail.setText(_("Received: {rx} · Sent: {tx} (since boot)").format(rx=human_size(net.bytes_recv), tx=human_size(net.bytes_sent)))
        disk = psutil.disk_io_counters()
        if disk and self._disk:
            rd = (disk.read_bytes - self._disk.read_bytes) / dt_
            wr = (disk.write_bytes - self._disk.write_bytes) / dt_
            self.disk_io.graph.push(rd, wr)
            self.disk_io.value.setText(f"<b>R {human_size(rd)}/s</b>  W {human_size(wr)}/s")
            self.disk_io.detail.setText(_("Read and write rate of all drives"))
        self._disk = disk
        self._refresh_disks()

    def _refresh_disks(self) -> None:
        parts = [p for p in psutil.disk_partitions(all=False)
                 if not p.mountpoint.startswith(("/snap", "/var/lib/flatpak", "/boot/efi")) and p.fstype not in ("squashfs", "iso9660")]
        if not self._disk_rows:
            t = QLabel(_("Drives"))
            t.setObjectName("NovaSubtitle")
            self.disks_lay.addWidget(t)
        for p in parts:
            try:
                u = psutil.disk_usage(p.mountpoint)
            except OSError:
                continue
            if p.mountpoint not in self._disk_rows:
                lbl = QLabel()
                bar = QProgressBar()
                bar.setRange(0, 100)
                bar.setTextVisible(False)
                self.disks_lay.addWidget(lbl)
                self.disks_lay.addWidget(bar)
                self._disk_rows[p.mountpoint] = (lbl, bar)
            lbl, bar = self._disk_rows[p.mountpoint]
            lbl.setText(f"<b>{p.mountpoint}</b>  ({p.device}, {p.fstype}) — "
                        + _("{free} free of {total}").format(free=human_size(u.free), total=human_size(u.total)))
            bar.setValue(int(u.percent))


def _cpu_model() -> str:
    try:
        with open("/proc/cpuinfo", encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("model name"):
                    return line.split(":", 1)[1].strip()
    except OSError:
        pass
    return _("Processor")


# ------------------------------------------------------------------------ logs
class LogsTab(QWidget):
    def __init__(self):
        super().__init__()
        lay = QVBoxLayout(self)
        info = QLabel(_("Errors and warnings of the current boot from the system journal (journalctl). "
                        "Full system logs require membership in the group “adm”."))
        info.setWordWrap(True)
        info.setObjectName("NovaMuted")
        lay.addWidget(info)
        row = QHBoxLayout()
        self.level = QPushButton(_("Show warnings too"))
        self.level.setCheckable(True)
        self.level.toggled.connect(lambda _c: self.refresh())
        reload_btn = QPushButton(themed_icon("view-refresh"), _("Reload"))
        reload_btn.clicked.connect(self.refresh)
        row.addWidget(self.level)
        row.addStretch(1)
        row.addWidget(reload_btn)
        lay.addLayout(row)
        self.text = QPlainTextEdit()
        self.text.setReadOnly(True)
        self.text.setFont(self.font())
        f = self.text.font()
        f.setFamily("Hack")
        self.text.setFont(f)
        lay.addWidget(self.text, 1)
        self._loaded = False

    def refresh(self) -> None:
        prio = "warning" if self.level.isChecked() else "err"
        try:
            out = subprocess.run(["journalctl", "-b", "-p", prio, "-n", "400", "--no-pager", "-o", "short-iso"],
                                 capture_output=True, text=True, timeout=20)
            text = out.stdout.strip() or out.stderr.strip() or _("No entries – no errors in this boot.")
        except (OSError, subprocess.TimeoutExpired) as exc:
            text = str(exc)
        self.text.setPlainText(text)
        self.text.verticalScrollBar().setValue(self.text.verticalScrollBar().maximum())
        self._loaded = True


class TaskManagerWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle(_("Nova Task Manager"))
        self.resize(1000, 700)
        self.tabs = QTabWidget()
        self.tabs.setDocumentMode(True)
        self.setCentralWidget(self.tabs)
        self.procs = ProcessesTab()
        self.perf = PerformanceTab()
        self.logs = LogsTab()
        self.tabs.addTab(self.procs, themed_icon("view-list-details"), _("Processes"))
        self.tabs.addTab(self.perf, themed_icon("office-chart-line"), _("Performance"))
        self.tabs.addTab(self.logs, themed_icon("dialog-warning"), _("Error logs"))
        self.tabs.currentChanged.connect(self._tab_changed)
        self.timer = QTimer(self, interval=INTERVAL_MS)
        self.timer.timeout.connect(self.tick)
        self.timer.start()
        self.tick()

    def _tab_changed(self, idx: int) -> None:
        if self.tabs.widget(idx) is self.logs and not self.logs._loaded:
            self.logs.refresh()

    def tick(self) -> None:
        self.perf.refresh()  # keep graphs filled even when not visible
        if self.tabs.currentWidget() is self.procs:
            self.procs.refresh()


def main() -> int:
    app = create_app("org.novaos.TaskManager", _("Nova Task Manager"), "nova-taskmanager")
    win = TaskManagerWindow()
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
