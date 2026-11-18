"""Start every Nova app window offscreen, check it renders, save screenshots.

Usage: QT_QPA_PLATFORM=offscreen python3 tests/ui/smoke_apps.py OUTDIR
Uses a temporary HOME so no user data is touched.
"""
import os
import sys
import tempfile

home = tempfile.mkdtemp(prefix="nova-smoke-")
os.environ["HOME"] = home
os.environ["XDG_DATA_HOME"] = os.path.join(home, ".local/share")
os.environ["XDG_CONFIG_HOME"] = os.path.join(home, ".config")
os.environ["XDG_CACHE_HOME"] = os.path.join(home, ".cache")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "src"))

import datetime as dt  # noqa: E402

from PyQt6.QtCore import QTimer  # noqa: E402
from PyQt6.QtGui import QIcon  # noqa: E402

from novaos.common.app import create_app  # noqa: E402

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
app = create_app("org.novaos.Smoke", "Smoke", "novaos")
QIcon.setThemeName("breeze")

from novaos.about.main import AboutWindow  # noqa: E402
from novaos.calendar.main import CalendarWindow  # noqa: E402
from novaos.calendar.store import CalendarStore, CalEvent  # noqa: E402
from novaos.help.main import HelpWindow  # noqa: E402
from novaos.notes.main import NoteStore, NotesWindow, notes_dir  # noqa: E402
from novaos.taskmanager.main import TaskManagerWindow  # noqa: E402
from novaos.updates.main import UpdatesWindow  # noqa: E402
from novaos.welcome.main import WelcomeWindow  # noqa: E402

store = NoteStore(notes_dir())
store.save(store.create(), "# Willkommen bei Nova Notes\n\nNotizen werden **automatisch** gespeichert.\n\n- Markdown\n- Suche\n- Export als PDF\n")
cal = CalendarStore()
today = dt.date.today()
cal.add(CalEvent("Team-Meeting", dt.datetime.combine(today, dt.time(10)), dt.datetime.combine(today, dt.time(11)), location="Büro"))
cal.add(CalEvent("Sport", dt.datetime.combine(today, dt.time(18)), dt.datetime.combine(today, dt.time(19))))

windows = {
    "notes": NotesWindow(store),
    "calendar": CalendarWindow(cal),
    "taskmanager": TaskManagerWindow(),
    "welcome": WelcomeWindow(live=True),
    "help": HelpWindow(),
    "about": AboutWindow(),
    "updates": UpdatesWindow(),
}
for w in windows.values():
    w.show()

failures = []


def snap():
    tm = windows["taskmanager"]
    tm.tabs.setCurrentIndex(1)
    for name, w in windows.items():
        img = w.grab()
        path = os.path.join(out, f"app-{name}.png")
        img.save(path)
        if img.isNull() or img.width() < 300:
            failures.append(name)
        print("saved", path, flush=True)
    windows["welcome"].go(1)
    windows["welcome"].grab().save(os.path.join(out, "app-welcome-theme.png"))
    app.exit(1 if failures else 0)


QTimer.singleShot(6000, snap)
rc = app.exec()
print("FAILED:" if failures else "all windows rendered", failures)
sys.exit(rc)
