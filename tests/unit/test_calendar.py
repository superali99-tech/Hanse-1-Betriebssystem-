import datetime as dt

from novaos.calendar.store import CalendarStore, CalEvent


def test_roundtrip(tmp_path):
    path = str(tmp_path / "cal.ics")
    s = CalendarStore(path)
    s.add(CalEvent("Zahnarzt", dt.datetime(2026, 11, 20, 9, 30), dt.datetime(2026, 11, 20, 10, 0),
                   location="Praxis", reminder_minutes=30))
    s.add(CalEvent("Urlaub", dt.date(2026, 12, 1), dt.date(2026, 12, 4), all_day=True, reminder_minutes=None))
    s2 = CalendarStore(path)
    assert len(s2.events) == 2
    z = next(e for e in s2.events if e.title == "Zahnarzt")
    assert z.location == "Praxis" and z.reminder_minutes == 30
    assert z.start == dt.datetime(2026, 11, 20, 9, 30)
    u = next(e for e in s2.events if e.title == "Urlaub")
    assert u.all_day and u.occurs_on(dt.date(2026, 12, 3)) and not u.occurs_on(dt.date(2026, 12, 4))
    assert s2.days_with_events(2026, 12) == {dt.date(2026, 12, 1), dt.date(2026, 12, 2), dt.date(2026, 12, 3)}


def test_update_delete_reminders(tmp_path):
    s = CalendarStore(str(tmp_path / "cal.ics"))
    e = CalEvent("Meeting", dt.datetime(2026, 1, 5, 14, 0), dt.datetime(2026, 1, 5, 15, 0), reminder_minutes=15)
    s.add(e)
    due = s.due_reminders(dt.datetime(2026, 1, 5, 13, 45, 30), dt.timedelta(seconds=75))
    assert [x.title for x in due] == ["Meeting"]
    assert s.due_reminders(dt.datetime(2026, 1, 5, 13, 40), dt.timedelta(seconds=75)) == []
    e.title = "Meeting (verschoben)"
    s.update(e)
    assert CalendarStore(s.path).events[0].title == "Meeting (verschoben)"
    s.delete(e.uid)
    assert CalendarStore(s.path).events == []


def test_import_export(tmp_path):
    a = CalendarStore(str(tmp_path / "a.ics"))
    a.add(CalEvent("X", dt.datetime(2026, 2, 1, 8), dt.datetime(2026, 2, 1, 9)))
    a.export_file(str(tmp_path / "export.ics"))
    b = CalendarStore(str(tmp_path / "b.ics"))
    assert b.import_file(str(tmp_path / "export.ics")) == 1
    assert b.import_file(str(tmp_path / "export.ics")) == 0  # no duplicates
