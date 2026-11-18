"""Entry point: ``nova-store [--search TEXT | --details APP_ID | --updates | --installed]``."""

from __future__ import annotations

import argparse
import signal
import sys


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="nova-store", description="Nova Store – Flatpak/Flathub app store of NovaOS")
    parser.add_argument("--search", metavar="TEXT", help="open the store with a search")
    parser.add_argument("--details", metavar="APP_ID", help="show the details page of an app")
    parser.add_argument("--updates", action="store_true", help="show the updates page")
    parser.add_argument("--installed", action="store_true", help="show installed apps")
    parser.add_argument("--list-updates", action="store_true", help="print available Flatpak updates and exit (no GUI)")
    parser.add_argument("url", nargs="?", help="appstream://APP_ID URL (used by other apps)")
    args, qt_args = parser.parse_known_args(argv if argv is not None else sys.argv[1:])

    if args.list_updates:
        from novaos.store.flatpak_backend import FlatpakBackend

        for u in FlatpakBackend().list_updates():
            print(f"{u.scope}\t{u.ref}")
        return 0

    from novaos.common.app import create_app
    from novaos.store.controller import StoreController
    from novaos.store.main_window import StoreWindow

    app = create_app("org.novaos.Store", "Nova Store", "nova-store")
    signal.signal(signal.SIGINT, signal.SIG_DFL)
    controller = StoreController()
    win = StoreWindow(controller)
    win.show()
    controller.start()

    target = args.details
    if args.url and args.url.startswith("appstream:"):
        target = args.url.split(":", 1)[1].lstrip("/")
    if target:
        win.when_ready(lambda: win.open_app(target))
    elif args.search:
        win.when_ready(lambda: win.search.setText(args.search))
    elif args.updates:
        win.when_ready(lambda: win.nav.setCurrentRow(win.nav.row(win.updates_item)))
    elif args.installed:
        win.when_ready(lambda: win.nav.setCurrentRow(win.nav.row(win.updates_item) - 1))
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
