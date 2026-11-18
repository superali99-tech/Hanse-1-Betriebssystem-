"""Thread helpers: run blocking backend calls without freezing the UI."""

from __future__ import annotations

import traceback
from typing import Any, Callable

from PyQt6.QtCore import QObject, QRunnable, QThreadPool, pyqtSignal


class _Signals(QObject):
    done = pyqtSignal(object)
    failed = pyqtSignal(object)
    progress = pyqtSignal(int, str)


class Task(QRunnable):
    """Run ``fn(*args, **kwargs)`` in the global thread pool.

    If ``fn`` accepts a ``progress`` keyword it receives a callback that
    forwards ``(percent, text)`` to the ``progress`` signal (thread safe).
    """

    def __init__(self, fn: Callable[..., Any], *args, with_progress: bool = False, **kwargs):
        super().__init__()
        self.fn, self.args, self.kwargs = fn, args, kwargs
        self.signals = _Signals()
        if with_progress:
            self.kwargs["progress"] = lambda p, t: self.signals.progress.emit(int(p), str(t))
        self.setAutoDelete(True)

    def run(self):  # runs in worker thread
        try:
            result = self.fn(*self.args, **self.kwargs)
        except Exception as exc:  # noqa: BLE001 - forwarded to UI
            exc.traceback_text = traceback.format_exc()  # type: ignore[attr-defined]
            self.signals.failed.emit(exc)
        else:
            self.signals.done.emit(result)


_live: set[Task] = set()


def run(fn, *args, on_done=None, on_error=None, on_progress=None, with_progress=False, **kwargs) -> Task:
    task = Task(fn, *args, with_progress=with_progress, **kwargs)
    _live.add(task)  # keep the Python wrapper (and its signals) alive

    def _cleanup(*_):
        _live.discard(task)

    if on_done:
        task.signals.done.connect(on_done)
    if on_error:
        task.signals.failed.connect(on_error)
    if on_progress:
        task.signals.progress.connect(on_progress)
    task.signals.done.connect(_cleanup)
    task.signals.failed.connect(_cleanup)
    QThreadPool.globalInstance().start(task)
    return task
