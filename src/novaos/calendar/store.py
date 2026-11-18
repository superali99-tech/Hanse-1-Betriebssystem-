"""Local calendar storage in iCalendar (RFC 5545) format.

Events are stored in ``~/.local/share/nova-calendar/calendar.ics`` so they
can be exported/imported by any other calendar program.
"""

from __future__ import annotations

import datetime as dt
import os
import uuid
from dataclasses import dataclass, field

from icalendar import Alarm, Calendar, Event


def data_path() -> str:
    base = os.environ.get("XDG_DATA_HOME", os.path.expanduser("~/.local/share"))
    path = os.path.join(base, "nova-calendar")
    os.makedirs(path, exist_ok=True)
    return os.path.join(path, "calendar.ics")


@dataclass
class CalEvent:
    title: str
    start: dt.datetime | dt.date
    end: dt.datetime | dt.date
    all_day: bool = False
    location: str = ""
    notes: str = ""
    reminder_minutes: int | None = 15
    uid: str = field(default_factory=lambda: f"{uuid.uuid4()}@novaos")

    def occurs_on(self, day: dt.date) -> bool:
        s = self.start if isinstance(self.start, dt.date) and not isinstance(self.start, dt.datetime) else self.start.date()
        e = self.end if isinstance(self.end, dt.date) and not isinstance(self.end, dt.datetime) else self.end.date()
        if self.all_day:
            return s <= day < max(e, s + dt.timedelta(days=1))
        return s <= day <= e

    def sort_key(self):
        if self.all_day:
            return (0, dt.datetime.combine(self.start, dt.time()))
        return (1, self.start)


def _to_local_naive(value):
    if isinstance(value, dt.datetime):
        if value.tzinfo is not None:
            value = value.astimezone().replace(tzinfo=None)
        return value
    return value


class CalendarStore:
    def __init__(self, path: str | None = None):
        self.path = path or data_path()
        self.events: list[CalEvent] = []
        self.load()

    def load(self) -> None:
        self.events = []
        if not os.path.exists(self.path):
            return
        with open(self.path, "rb") as fh:
            cal = Calendar.from_ical(fh.read())
        self.events = self._parse(cal)

    @staticmethod
    def _parse(cal: Calendar) -> list[CalEvent]:
        out = []
        for comp in cal.walk("VEVENT"):
            start = comp.get("dtstart").dt
            end_prop = comp.get("dtend")
            all_day = not isinstance(start, dt.datetime)
            if end_prop is not None:
                end = end_prop.dt
            elif all_day:
                end = start + dt.timedelta(days=1)
            else:
                end = start + dt.timedelta(hours=1)
            reminder = None
            for alarm in comp.walk("VALARM"):
                trig = alarm.get("trigger")
                if trig is not None and isinstance(trig.dt, dt.timedelta):
                    reminder = int(-trig.dt.total_seconds() // 60)
                    break
            out.append(CalEvent(
                title=str(comp.get("summary", "")),
                start=_to_local_naive(start),
                end=_to_local_naive(end),
                all_day=all_day,
                location=str(comp.get("location", "")),
                notes=str(comp.get("description", "")),
                reminder_minutes=reminder,
                uid=str(comp.get("uid", f"{uuid.uuid4()}@novaos")),
            ))
        return out

    def to_calendar(self, events: list[CalEvent] | None = None) -> Calendar:
        cal = Calendar()
        cal.add("prodid", "-//NovaOS//Nova Calendar//DE")
        cal.add("version", "2.0")
        for e in events if events is not None else self.events:
            ev = Event()
            ev.add("uid", e.uid)
            ev.add("summary", e.title)
            ev.add("dtstamp", dt.datetime.now(dt.timezone.utc))
            ev.add("dtstart", e.start)
            ev.add("dtend", e.end)
            if e.location:
                ev.add("location", e.location)
            if e.notes:
                ev.add("description", e.notes)
            if e.reminder_minutes is not None and not e.all_day:
                alarm = Alarm()
                alarm.add("action", "DISPLAY")
                alarm.add("description", e.title)
                alarm.add("trigger", dt.timedelta(minutes=-e.reminder_minutes))
                ev.add_component(alarm)
            cal.add_component(ev)
        return cal

    def save(self) -> None:
        tmp = self.path + ".tmp"
        with open(tmp, "wb") as fh:
            fh.write(self.to_calendar().to_ical())
        os.replace(tmp, self.path)

    # ---------------------------------------------------------------- editing
    def add(self, event: CalEvent) -> None:
        self.events.append(event)
        self.save()

    def update(self, event: CalEvent) -> None:
        self.events = [event if e.uid == event.uid else e for e in self.events]
        self.save()

    def delete(self, uid: str) -> None:
        self.events = [e for e in self.events if e.uid != uid]
        self.save()

    def on_day(self, day: dt.date) -> list[CalEvent]:
        return sorted((e for e in self.events if e.occurs_on(day)), key=CalEvent.sort_key)

    def days_with_events(self, year: int, month: int) -> set[dt.date]:
        days = set()
        first = dt.date(year, month, 1)
        for i in range(31):
            d = first + dt.timedelta(days=i)
            if d.month != month:
                break
            if any(e.occurs_on(d) for e in self.events):
                days.add(d)
        return days

    def import_file(self, path: str) -> int:
        with open(path, "rb") as fh:
            cal = Calendar.from_ical(fh.read())
        known = {e.uid for e in self.events}
        new = [e for e in self._parse(cal) if e.uid not in known]
        self.events.extend(new)
        self.save()
        return len(new)

    def export_file(self, path: str) -> None:
        with open(path, "wb") as fh:
            fh.write(self.to_calendar().to_ical())

    def due_reminders(self, now: dt.datetime, window: dt.timedelta) -> list[CalEvent]:
        """Events whose reminder time falls into (now - window, now]."""
        due = []
        for e in self.events:
            if e.all_day or e.reminder_minutes is None or not isinstance(e.start, dt.datetime):
                continue
            t = e.start - dt.timedelta(minutes=e.reminder_minutes)
            if now - window < t <= now:
                due.append(e)
        return due
