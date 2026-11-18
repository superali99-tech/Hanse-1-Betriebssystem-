"""Nova Calendar – month view with local appointments and reminders."""

from __future__ import annotations

import datetime as dt
import subprocess
import sys
import time

from PyQt6.QtCore import QDate, QDateTime, QLocale, QTime, Qt
from PyQt6.QtGui import QAction, QColor, QKeySequence, QTextCharFormat
from PyQt6.QtWidgets import (
    QCalendarWidget,
    QCheckBox,
    QDateTimeEdit,
    QDialog,
    QDialogButtonBox,
    QFileDialog,
    QFormLayout,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QPushButton,
    QSpinBox,
    QToolBar,
    QVBoxLayout,
    QWidget,
)

from novaos.calendar.store import CalendarStore, CalEvent
from novaos.common.app import create_app, themed_icon
from novaos.common.i18n import _
from novaos.common.style import ACCENT


class EventDialog(QDialog):
    def __init__(self, event: CalEvent | None, day: dt.date, parent=None):
        super().__init__(parent)
        self.setWindowTitle(_("Edit appointment") if event else _("New appointment"))
        self.setMinimumWidth(460)
        form = QFormLayout(self)
        self.title = QLineEdit(event.title if event else "")
        self.title.setPlaceholderText(_("Title"))
        self.all_day = QCheckBox(_("All day"))
        self.start = QDateTimeEdit()
        self.end = QDateTimeEdit()
        for w in (self.start, self.end):
            w.setCalendarPopup(True)
            w.setDisplayFormat("dd.MM.yyyy HH:mm")
        if event:
            s = event.start if isinstance(event.start, dt.datetime) else dt.datetime.combine(event.start, dt.time(9))
            e = event.end if isinstance(event.end, dt.datetime) else dt.datetime.combine(event.end - dt.timedelta(days=1), dt.time(10))
            self.all_day.setChecked(event.all_day)
        else:
            now = dt.datetime.now()
            hour = now.hour + 1 if day == now.date() else 9
            s = dt.datetime.combine(day, dt.time(min(hour, 23)))
            e = s + dt.timedelta(hours=1)
        self.start.setDateTime(QDateTime(QDate(s.year, s.month, s.day), QTime(s.hour, s.minute)))
        self.end.setDateTime(QDateTime(QDate(e.year, e.month, e.day), QTime(e.hour, e.minute)))
        self.start.dateTimeChanged.connect(self._start_changed)
        self.location = QLineEdit(event.location if event else "")
        self.reminder = QSpinBox()
        self.reminder.setRange(-1, 10080)
        self.reminder.setSpecialValueText(_("No reminder"))
        self.reminder.setSuffix(" " + _("min before"))
        self.reminder.setValue(event.reminder_minutes if event and event.reminder_minutes is not None else (15 if not event else -1))
        self.notes = QPlainTextEdit(event.notes if event else "")
        self.notes.setFixedHeight(90)
        form.addRow(_("Title"), self.title)
        form.addRow("", self.all_day)
        form.addRow(_("Start"), self.start)
        form.addRow(_("End"), self.end)
        form.addRow(_("Location"), self.location)
        form.addRow(_("Reminder"), self.reminder)
        form.addRow(_("Notes"), self.notes)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Save | QDialogButtonBox.StandardButton.Cancel)
        buttons.accepted.connect(self._accept)
        buttons.rejected.connect(self.reject)
        form.addRow(buttons)
        self._event = event

    def _start_changed(self, value: QDateTime) -> None:
        if self.end.dateTime() <= value:
            self.end.setDateTime(value.addSecs(3600))

    def _accept(self) -> None:
        if not self.title.text().strip():
            self.title.setFocus()
            return
        if self.end.dateTime() < self.start.dateTime():
            QMessageBox.warning(self, _("Nova Calendar"), _("The end must be after the start."))
            return
        self.accept()

    def result_event(self) -> CalEvent:
        s = self.start.dateTime().toPyDateTime().replace(second=0, microsecond=0)
        e = self.end.dateTime().toPyDateTime().replace(second=0, microsecond=0)
        if self.all_day.isChecked():
            start, end = s.date(), e.date() + dt.timedelta(days=1)
        else:
            start, end = s, e
        ev = CalEvent(
            title=self.title.text().strip(),
            start=start, end=end, all_day=self.all_day.isChecked(),
            location=self.location.text().strip(), notes=self.notes.toPlainText().strip(),
            reminder_minutes=None if self.reminder.value() < 0 else self.reminder.value(),
        )
        if self._event:
            ev.uid = self._event.uid
        return ev


class CalendarWindow(QMainWindow):
    def __init__(self, store: CalendarStore):
        super().__init__()
        self.store = store
        self.setWindowTitle(_("Nova Calendar"))
        self.resize(1100, 720)
        tb = QToolBar()
        tb.setMovable(False)
        tb.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextBesideIcon)
        self.addToolBar(tb)
        a_new = QAction(themed_icon("appointment-new", "list-add"), _("New appointment"), self, shortcut=QKeySequence.StandardKey.New)
        a_new.triggered.connect(self.new_event)
        a_today = QAction(themed_icon("go-jump-today"), _("Today"), self, shortcut=QKeySequence("Ctrl+T"))
        a_today.triggered.connect(lambda: self.cal.setSelectedDate(QDate.currentDate()))
        a_import = QAction(themed_icon("document-import"), _("Import…"), self)
        a_import.triggered.connect(self.import_ics)
        a_export = QAction(themed_icon("document-export"), _("Export…"), self)
        a_export.triggered.connect(self.export_ics)
        for a in (a_new, a_today, a_import, a_export):
            tb.addAction(a)

        central = QWidget()
        self.setCentralWidget(central)
        lay = QHBoxLayout(central)
        lay.setContentsMargins(12, 12, 12, 12)
        lay.setSpacing(16)
        self.cal = QCalendarWidget()
        self.cal.setGridVisible(False)
        self.cal.setVerticalHeaderFormat(QCalendarWidget.VerticalHeaderFormat.ISOWeekNumbers)
        self.cal.setFirstDayOfWeek(Qt.DayOfWeek.Monday)
        self.cal.selectionChanged.connect(self.refresh_day)
        self.cal.currentPageChanged.connect(lambda y, m: self.mark_days())
        self.cal.activated.connect(lambda d: self.new_event())
        f = self.cal.font()
        f.setPointSizeF(f.pointSizeF() * 1.25)
        self.cal.setFont(f)
        lay.addWidget(self.cal, 3)

        side = QFrame()
        side.setObjectName("NovaPanel")
        sl = QVBoxLayout(side)
        sl.setContentsMargins(16, 14, 16, 14)
        self.day_label = QLabel()
        self.day_label.setObjectName("NovaSubtitle")
        sl.addWidget(self.day_label)
        self.events = QListWidget()
        self.events.itemDoubleClicked.connect(lambda it: self.edit_event(it.data(Qt.ItemDataRole.UserRole)))
        sl.addWidget(self.events, 1)
        row = QHBoxLayout()
        b_add = QPushButton(themed_icon("list-add"), _("Add"))
        b_add.setObjectName("NovaPrimary")
        b_add.clicked.connect(self.new_event)
        b_edit = QPushButton(themed_icon("document-edit"), _("Edit"))
        b_edit.clicked.connect(lambda: self._with_selected(self.edit_event))
        b_del = QPushButton(themed_icon("edit-delete"), _("Delete"))
        b_del.clicked.connect(lambda: self._with_selected(self.delete_event))
        for b in (b_add, b_edit, b_del):
            row.addWidget(b)
        sl.addLayout(row)
        lay.addWidget(side, 2)
        self.mark_days()
        self.refresh_day()

    def selected_day(self) -> dt.date:
        return self.cal.selectedDate().toPyDate()

    def mark_days(self) -> None:
        self.cal.setDateTextFormat(QDate(), QTextCharFormat())
        fmt = QTextCharFormat()
        fmt.setFontWeight(700)
        fmt.setForeground(QColor(ACCENT))
        fmt.setFontUnderline(True)
        for d in self.store.days_with_events(self.cal.yearShown(), self.cal.monthShown()):
            self.cal.setDateTextFormat(QDate(d.year, d.month, d.day), fmt)

    def refresh_day(self) -> None:
        day = self.selected_day()
        self.day_label.setText(QLocale.system().toString(QDate(day.year, day.month, day.day), "dddd, d. MMMM yyyy"))
        self.events.clear()
        events = self.store.on_day(day)
        if not events:
            item = QListWidgetItem(_("No appointments. Double-click a day to add one."))
            item.setFlags(Qt.ItemFlag.NoItemFlags)
            self.events.addItem(item)
        for e in events:
            when = _("All day") if e.all_day else f"{e.start:%H:%M} – {e.end:%H:%M}"
            text = f"{when}   {e.title}"
            if e.location:
                text += f"\n📍 {e.location}"
            item = QListWidgetItem(text)
            item.setData(Qt.ItemDataRole.UserRole, e)
            item.setToolTip(e.notes)
            self.events.addItem(item)

    def _with_selected(self, fn) -> None:
        item = self.events.currentItem()
        if item and item.data(Qt.ItemDataRole.UserRole):
            fn(item.data(Qt.ItemDataRole.UserRole))

    def new_event(self) -> None:
        dlg = EventDialog(None, self.selected_day(), self)
        if dlg.exec():
            self.store.add(dlg.result_event())
            self.mark_days()
            self.refresh_day()

    def edit_event(self, event: CalEvent) -> None:
        if not event:
            return
        dlg = EventDialog(event, self.selected_day(), self)
        if dlg.exec():
            self.store.update(dlg.result_event())
            self.mark_days()
            self.refresh_day()

    def delete_event(self, event: CalEvent) -> None:
        if QMessageBox.question(self, _("Delete appointment"), _("Delete “{title}”?").format(title=event.title)) \
                == QMessageBox.StandardButton.Yes:
            self.store.delete(event.uid)
            self.mark_days()
            self.refresh_day()

    def import_ics(self) -> None:
        path, _f = QFileDialog.getOpenFileName(self, _("Import calendar"), "", _("iCalendar (*.ics)"))
        if path:
            try:
                n = self.store.import_file(path)
            except Exception as exc:  # noqa: BLE001
                QMessageBox.warning(self, _("Nova Calendar"), str(exc))
                return
            QMessageBox.information(self, _("Nova Calendar"), _("{n} appointments imported.").format(n=n))
            self.mark_days()
            self.refresh_day()

    def export_ics(self) -> None:
        path, _f = QFileDialog.getSaveFileName(self, _("Export calendar"), "nova-calendar.ics", _("iCalendar (*.ics)"))
        if path:
            self.store.export_file(path)


def reminder_daemon() -> int:
    """Background reminder loop (started by a systemd user service)."""
    window = dt.timedelta(seconds=75)
    last_mtime = 0.0
    notified: set[tuple[str, str]] = set()
    store = CalendarStore()
    while True:
        try:
            import os

            mtime = os.path.getmtime(store.path) if os.path.exists(store.path) else 0
            if mtime != last_mtime:
                store.load()
                last_mtime = mtime
            for e in store.due_reminders(dt.datetime.now(), window):
                key = (e.uid, str(e.start))
                if key in notified:
                    continue
                notified.add(key)
                body = f"{e.start:%H:%M}" + (f" · {e.location}" if e.location else "")
                subprocess.run(["notify-send", "-a", _("Nova Calendar"), "-i", "nova-calendar", e.title, body],
                               check=False)
        except Exception as exc:  # noqa: BLE001
            print("nova-calendar reminders:", exc, file=sys.stderr)
        time.sleep(60)


def main() -> int:
    if "--reminders" in sys.argv:
        return reminder_daemon()
    app = create_app("org.novaos.Calendar", _("Nova Calendar"), "nova-calendar")
    win = CalendarWindow(CalendarStore())
    win.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
