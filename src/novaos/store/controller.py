"""Nova Store application logic (independent of the widgets)."""

from __future__ import annotations

import time
from collections import deque
from dataclasses import dataclass

from gi.repository import Gio
from PyQt6.QtCore import QObject, QSettings, QTimer, pyqtSignal

from novaos.common.i18n import _
from novaos.store import worker
from novaos.store.appstream import AppInfo, Catalog
from novaos.store.flatpak_backend import FlatpakBackend, InstalledApp, StoreError, friendly_error
from novaos.store.ratings import RatingsService

CATALOG_MAX_AGE = 6 * 3600
UPDATE_CHECK_INTERVAL_MS = 14 * 24 * 3600 * 1000  # every 2 weeks, like the system updates


@dataclass
class Operation:
    kind: str        # install | uninstall | update
    key: str         # app id used by the UI (flatpak name)
    refs: list[str]
    scope: str
    title: str
    cancellable: Gio.Cancellable | None = None
    progress: int = 0
    status: str = ""


class StoreController(QObject):
    catalog_loading = pyqtSignal(str)
    catalog_ready = pyqtSignal()
    catalog_failed = pyqtSignal(str)
    installed_changed = pyqtSignal()
    updates_changed = pyqtSignal(int)
    ratings_ready = pyqtSignal()
    op_queued = pyqtSignal(str)
    op_progress = pyqtSignal(str, int, str)
    op_finished = pyqtSignal(str, bool, str)

    def __init__(self, backend: FlatpakBackend | None = None, parent: QObject | None = None):
        super().__init__(parent)
        self.backend = backend or FlatpakBackend()
        self.catalog = Catalog()
        self.ratings = RatingsService()
        self.installed: dict[str, InstalledApp] = {}
        self.updates: list[InstalledApp] = []
        self.last_update_check: float = 0
        self.settings = QSettings("NovaOS", "nova-store")
        self._queue: deque[Operation] = deque()
        self._current: Operation | None = None
        self._timer = QTimer(self)
        self._timer.timeout.connect(self.check_updates)

    # ----------------------------------------------------------------- settings
    @property
    def ratings_enabled(self) -> bool:
        return self.settings.value("ratings_enabled", True, type=bool)

    @ratings_enabled.setter
    def ratings_enabled(self, value: bool) -> None:
        self.settings.setValue("ratings_enabled", bool(value))

    @property
    def auto_update_check(self) -> bool:
        return self.settings.value("auto_update_check", True, type=bool)

    @auto_update_check.setter
    def auto_update_check(self, value: bool) -> None:
        self.settings.setValue("auto_update_check", bool(value))
        self._apply_timer()

    @property
    def scope(self) -> str:
        return self.settings.value("install_scope", "system", type=str)

    @scope.setter
    def scope(self, value: str) -> None:
        self.settings.setValue("install_scope", "user" if value == "user" else "system")

    def _apply_timer(self) -> None:
        if self.auto_update_check:
            self._timer.start(UPDATE_CHECK_INTERVAL_MS)
        else:
            self._timer.stop()

    # ------------------------------------------------------------------ startup
    def start(self, force_refresh: bool = False) -> None:
        self.catalog_loading.emit(_("Loading app catalog…"))
        worker.run(self._load_catalog, force_refresh, on_done=self._catalog_loaded, on_error=self._catalog_error)

    def _load_catalog(self, force_refresh: bool) -> Catalog:
        b = self.backend
        if not b.has_remote():
            raise StoreError(_("The Flathub software source is not configured."))
        age = b.appstream_age()
        if force_refresh or age is None or age > CATALOG_MAX_AGE:
            try:
                b.update_appstream()
            except Exception as exc:  # noqa: BLE001
                if b.appstream_age() is None:
                    raise StoreError(_("The app catalog could not be downloaded.") + "\n" + friendly_error(exc)) from exc
        cat = Catalog()
        cat.load_dir(b.appstream_dir(), b.remote)
        if not len(cat):
            raise StoreError(_("The app catalog is empty."))
        return cat

    def _catalog_loaded(self, cat: Catalog) -> None:
        self.catalog = cat
        self.catalog_ready.emit()
        self.refresh_installed()
        if self.ratings_enabled:
            self.load_ratings()
        self.check_updates()
        self._apply_timer()

    def _catalog_error(self, exc: Exception) -> None:
        self.catalog_failed.emit(friendly_error(exc))

    def load_ratings(self) -> None:
        worker.run(self.ratings.load, on_done=lambda n: self.ratings_ready.emit(), on_error=lambda e: None)

    def refresh_installed(self) -> None:
        worker.run(self.backend.installed_names, on_done=self._installed_loaded, on_error=lambda e: None)

    def _installed_loaded(self, installed: dict) -> None:
        self.installed = installed
        self.installed_changed.emit()

    def check_updates(self) -> None:
        worker.run(self.backend.list_updates, on_done=self._updates_loaded, on_error=lambda e: None)

    def _updates_loaded(self, updates: list) -> None:
        self.updates = updates
        self.last_update_check = time.time()
        self.updates_changed.emit(len(updates))

    # ---------------------------------------------------------------- helpers
    def app_for(self, name: str) -> AppInfo | None:
        return self.catalog.find_by_flatpak_name(name)

    def is_installed(self, name: str) -> bool:
        return name in self.installed

    def has_update(self, name: str) -> bool:
        return any(u.name == name for u in self.updates)

    def operation_for(self, key: str) -> Operation | None:
        if self._current and self._current.key == key:
            return self._current
        return next((o for o in self._queue if o.key == key), None)

    # ------------------------------------------------------------- operations
    def install(self, app: AppInfo) -> None:
        self._enqueue(Operation("install", app.flatpak_name, [app.ref], self.scope, app.name))

    def uninstall(self, name: str) -> None:
        inst = self.installed.get(name)
        if not inst:
            return
        self._enqueue(Operation("uninstall", name, [inst.ref], inst.scope, inst.title))

    def update(self, names: list[str] | None = None) -> None:
        targets = [u for u in self.updates if names is None or u.name in names]
        for scope in ("system", "user"):
            refs = [u.ref for u in targets if u.scope == scope]
            if refs:
                key = names[0] if names and len(names) == 1 else "__all__"
                self._enqueue(Operation("update", key, refs, scope, _("Updates")))

    def remove_unused(self) -> None:
        def job(progress):
            refs = self.backend.list_unused()
            if refs:
                self.backend.run_transaction("uninstall", refs, "system", progress=progress)
            return len(refs)

        self._enqueue(Operation("uninstall_unused", "__unused__", [], "system", _("Unused runtimes")), job=job)

    def cancel(self, key: str) -> None:
        op = self.operation_for(key)
        if not op:
            return
        if op is self._current and op.cancellable:
            op.cancellable.cancel()
        elif op in self._queue:
            self._queue.remove(op)
            self.op_finished.emit(key, False, _("The operation was cancelled."))

    def _enqueue(self, op: Operation, job=None) -> None:
        if self.operation_for(op.key):
            return
        op.job = job  # type: ignore[attr-defined]
        self._queue.append(op)
        self.op_queued.emit(op.key)
        self._next()

    def _next(self) -> None:
        if self._current or not self._queue:
            return
        op = self._current = self._queue.popleft()
        op.cancellable = Gio.Cancellable()
        job = getattr(op, "job", None)
        if job:
            fn = job
            args: tuple = ()
            kwargs: dict = {}
        else:
            fn = self.backend.run_transaction
            args = (op.kind, op.refs, op.scope)
            kwargs = {"cancellable": op.cancellable}
        worker.run(
            fn, *args, with_progress=True,
            on_progress=lambda p, t, o=op: self._progress(o, p, t),
            on_done=lambda _r, o=op: self._done(o, True, ""),
            on_error=lambda e, o=op: self._done(o, False, friendly_error(e)),
            **kwargs,
        )

    def _progress(self, op: Operation, pct: int, text: str) -> None:
        op.progress, op.status = pct, text
        self.op_progress.emit(op.key, pct, text)

    def _done(self, op: Operation, ok: bool, message: str) -> None:
        self._current = None
        if ok and op.kind in ("install", "uninstall", "uninstall_unused"):
            self.backend.refresh_menu()
        self.op_finished.emit(op.key, ok, message)
        self.refresh_installed()
        if op.kind in ("update", "install", "uninstall"):
            self.check_updates()
        self._next()
