"""Flatpak backend of Nova Store, built on libflatpak (GObject introspection).

All functions here are blocking and must be called from worker threads (see
``store/worker.py``). Each worker thread uses its own GLib main context so
that libflatpak never dispatches into the Qt main thread (Qt iterates the
global default GLib context on Linux).

System-wide installations performed by a normal user go through Flatpak's
system helper, which asks polkit for authorization. On NovaOS members of the
``sudo`` group are allowed without password prompt (Debian's Flatpak policy);
everybody else gets an administrator password dialog from the KDE polkit
agent.
"""

from __future__ import annotations

import os
import subprocess
import threading
from dataclasses import dataclass
from typing import Callable

import gi

gi.require_version("Flatpak", "1.0")
from gi.repository import Flatpak, Gio, GLib  # noqa: E402

from novaos.common.i18n import _  # noqa: E402

DEFAULT_REMOTE = "flathub"


@dataclass
class InstalledApp:
    name: str            # flatpak id, e.g. org.mozilla.firefox
    ref: str             # app/org.mozilla.firefox/x86_64/stable
    origin: str
    branch: str
    title: str
    summary: str
    version: str
    installed_size: int
    scope: str           # "system" | "user"
    deploy_dir: str


@dataclass
class RemoteDetails:
    download_size: int
    installed_size: int
    metadata: str
    eol: str = ""
    eol_rebase: str = ""


class StoreError(Exception):
    """User presentable error."""


def friendly_error(err: Exception) -> str:
    """Turn a GLib.Error from libflatpak into a short user facing message."""
    if isinstance(err, StoreError):
        return str(err)
    msg = getattr(err, "message", None) or str(err)
    domain = getattr(err, "domain", "")
    code = getattr(err, "code", -1)
    if domain == "flatpak-error-quark":
        if code == Flatpak.Error.ABORTED:
            return _("The operation was cancelled.")
        if code == Flatpak.Error.OUT_OF_SPACE:
            return _("Not enough free disk space.")
        if code == Flatpak.Error.ALREADY_INSTALLED:
            return _("The app is already installed.")
        if code == Flatpak.Error.NOT_INSTALLED:
            return _("The app is not installed.")
        if code == Flatpak.Error.PERMISSION_DENIED:
            return _("Permission denied. Administrator rights are required.")
    if domain == "g-io-error-quark" and code == Gio.IOErrorEnum.CANCELLED:
        return _("The operation was cancelled.")
    low = msg.lower()
    if "not authorized" in low or "authoriz" in low or "polkit" in low:
        return _("Permission denied. Administrator rights are required.")
    if any(s in low for s in ("could not resolve", "network", "timeout", "timed out", "couldn't connect", "ssl", "curl")):
        return _("Network error. Please check your internet connection.") + f"\n({msg})"
    if "no space" in low:
        return _("Not enough free disk space.")
    return msg


class _ThreadContext:
    """Push a private GLib main context for the duration of a call."""

    def __enter__(self):
        self.ctx = GLib.MainContext.new()
        self.ctx.push_thread_default()
        return self.ctx

    def __exit__(self, *exc):
        self.ctx.pop_thread_default()
        return False


class FlatpakBackend:
    def __init__(self, remote: str = DEFAULT_REMOTE):
        self.remote = remote
        self.arch = Flatpak.get_default_arch()
        self._lock = threading.Lock()

    # ------------------------------------------------------------ installations
    def installation(self, scope: str = "system") -> Flatpak.Installation:
        if scope == "user":
            return Flatpak.Installation.new_user(None)
        return Flatpak.Installation.new_system(None)

    def installations(self) -> list[tuple[str, Flatpak.Installation]]:
        out = [("system", self.installation("system"))]
        try:
            out.append(("user", self.installation("user")))
        except GLib.Error:
            pass
        return out

    def has_remote(self, scope: str = "system") -> bool:
        try:
            self.installation(scope).get_remote_by_name(self.remote, None)
            return True
        except GLib.Error:
            return False

    def appstream_dir(self, scope: str = "system") -> str:
        inst = self.installation(scope)
        return os.path.join(inst.get_path().get_path(), "appstream", self.remote, self.arch, "active")

    def appstream_age(self, scope: str = "system") -> float | None:
        """Seconds since the catalog was last updated, None if missing."""
        d = self.appstream_dir(scope)
        for name in ("appstream.xml.gz", "appstream.xml"):
            p = os.path.join(d, name)
            if os.path.exists(p):
                import time

                return time.time() - os.path.getmtime(os.path.dirname(p) + "/" + name)
        return None

    def update_appstream(self, scope: str = "system") -> bool:
        """Download the latest catalog (network). Returns True if it changed."""
        with _ThreadContext():
            inst = self.installation(scope)
            res = inst.update_appstream_sync(self.remote, self.arch, None)
            if isinstance(res, tuple):
                return bool(res[1]) if len(res) > 1 else bool(res[0])
            return bool(res)

    # ---------------------------------------------------------------- queries
    def list_installed(self) -> list[InstalledApp]:
        apps: list[InstalledApp] = []
        with _ThreadContext():
            for scope, inst in self.installations():
                try:
                    refs = inst.list_installed_refs_by_kind(Flatpak.RefKind.APP, None)
                except GLib.Error:
                    continue
                for r in refs:
                    apps.append(
                        InstalledApp(
                            name=r.get_name(),
                            ref=r.format_ref(),
                            origin=r.get_origin() or "",
                            branch=r.get_branch() or "",
                            title=r.get_appdata_name() or r.get_name(),
                            summary=r.get_appdata_summary() or "",
                            version=r.get_appdata_version() or "",
                            installed_size=int(r.get_installed_size() or 0),
                            scope=scope,
                            deploy_dir=r.get_deploy_dir() or "",
                        )
                    )
        apps.sort(key=lambda a: a.title.lower())
        return apps

    def installed_names(self) -> dict[str, InstalledApp]:
        return {a.name: a for a in self.list_installed()}

    def list_updates(self) -> list[InstalledApp]:
        """Installed apps *and runtimes* with a newer version on the remote."""
        out: list[InstalledApp] = []
        with _ThreadContext():
            for scope, inst in self.installations():
                try:
                    refs = inst.list_installed_refs_for_update(None)
                except GLib.Error:
                    continue
                for r in refs:
                    out.append(
                        InstalledApp(
                            name=r.get_name(),
                            ref=r.format_ref(),
                            origin=r.get_origin() or "",
                            branch=r.get_branch() or "",
                            title=r.get_appdata_name() or r.get_name(),
                            summary=r.get_appdata_summary() or "",
                            version=r.get_appdata_version() or "",
                            installed_size=int(r.get_installed_size() or 0),
                            scope=scope,
                            deploy_dir=r.get_deploy_dir() or "",
                        )
                    )
        return out

    def remote_details(self, ref: str, scope: str = "system") -> RemoteDetails:
        """Sizes, end-of-life state and sandbox metadata of a remote app."""
        with _ThreadContext():
            inst = self.installation(scope)
            parsed = Flatpak.Ref.parse(ref)
            rref = inst.fetch_remote_ref_sync(
                self.remote, parsed.get_kind(), parsed.get_name(), parsed.get_arch(), parsed.get_branch(), None
            )
            meta = rref.get_metadata()
            text = meta.get_data().decode("utf-8", "replace") if meta is not None else ""
            if not text:
                try:
                    data = inst.fetch_remote_metadata_sync(self.remote, parsed, None)
                    text = data.get_data().decode("utf-8", "replace")
                except GLib.Error:
                    text = ""
            return RemoteDetails(
                download_size=int(rref.get_download_size() or 0),
                installed_size=int(rref.get_installed_size() or 0),
                metadata=text,
                eol=rref.get_eol() or "",
                eol_rebase=rref.get_eol_rebase() or "",
            )

    def installed_metadata(self, app: InstalledApp) -> str:
        path = os.path.join(app.deploy_dir, "metadata")
        try:
            with open(path, encoding="utf-8") as fh:
                return fh.read()
        except OSError:
            return ""

    def list_unused(self) -> list[str]:
        out: list[str] = []
        with _ThreadContext():
            for _scope, inst in self.installations():
                try:
                    out.extend(r.format_ref() for r in inst.list_unused_refs(None, None))
                except (GLib.Error, AttributeError):
                    pass
        return out

    # ------------------------------------------------------------ transactions
    def run_transaction(
        self,
        kind: str,
        refs: list[str],
        scope: str = "system",
        progress: Callable[[int, str], None] | None = None,
        cancellable: Gio.Cancellable | None = None,
    ) -> None:
        """Install, update or uninstall ``refs``. Raises StoreError on failure.

        ``progress(percent, text)`` is called from the worker thread.
        """
        progress = progress or (lambda p, t: None)
        errors: list[str] = []
        with self._lock, _ThreadContext():
            inst = self.installation(scope)
            trans = Flatpak.Transaction.new_for_installation(inst, cancellable)
            trans.add_default_dependency_sources()
            for ref in refs:
                if kind == "install":
                    trans.add_install(self.remote, ref, None)
                elif kind == "update":
                    trans.add_update(ref, None, None)
                elif kind == "uninstall":
                    trans.add_uninstall(ref)
                else:
                    raise ValueError(kind)

            state = {"ops": [], "index": 0}

            def on_ready(t):
                state["ops"] = list(t.get_operations())
                progress(0, _("Preparing…"))
                return True

            def on_new_operation(t, op, prog):
                ops = state["ops"] or list(t.get_operations())
                try:
                    idx = [o.get_ref() for o in ops].index(op.get_ref())
                except ValueError:
                    idx = state["index"]
                state["index"] = idx
                total = max(1, len(ops))
                name = op.get_ref().split("/")[1] if "/" in op.get_ref() else op.get_ref()
                otype = op.get_operation_type()
                if otype == Flatpak.TransactionOperationType.UNINSTALL:
                    verb = _("Removing")
                elif otype == Flatpak.TransactionOperationType.UPDATE:
                    verb = _("Updating")
                else:
                    verb = _("Downloading")
                prog.set_update_frequency(200)

                def changed(p):
                    pct = int(((idx + p.get_progress() / 100.0) / total) * 100)
                    progress(min(99, pct), f"{verb} {name} ({idx + 1}/{total})")

                prog.connect("changed", changed)
                changed(prog)

            def on_error(t, op, error, details):
                errors.append(friendly_error(error))
                # Non fatal errors (e.g. a failed runtime update) do not abort
                return bool(details & Flatpak.TransactionErrorDetails.NON_FATAL)

            trans.connect("ready", on_ready)
            trans.connect("new-operation", on_new_operation)
            trans.connect("operation-error", on_error)
            trans.connect("choose-remote-for-ref", lambda t, for_ref, rt_ref, remotes: 0)
            trans.connect("add-new-remote", lambda t, reason, from_id, name, url: False)
            try:
                trans.run(cancellable)
            except GLib.Error as e:
                raise StoreError(errors[0] if errors else friendly_error(e)) from e
        progress(100, _("Done"))

    def install(self, ref: str, scope: str = "system", **kw) -> None:
        self.run_transaction("install", [ref], scope, **kw)

    def uninstall(self, ref: str, scope: str = "system", **kw) -> None:
        self.run_transaction("uninstall", [ref], scope, **kw)

    def update(self, refs: list[str], scope: str = "system", **kw) -> None:
        self.run_transaction("update", refs, scope, **kw)

    # ----------------------------------------------------------------- launch
    @staticmethod
    def launch(name: str, scope: str = "system") -> bool:
        args = ["flatpak", "run"]
        if scope == "user":
            args.append("--user")
        args.append(name)
        try:
            subprocess.Popen(args, start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return True
        except OSError:
            return False

    @staticmethod
    def refresh_menu() -> None:
        """Make Plasma pick up new/removed .desktop files immediately."""
        for tool in ("kbuildsycoca6", "kbuildsycoca5"):
            try:
                subprocess.Popen([tool], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
                return
            except OSError:
                continue
