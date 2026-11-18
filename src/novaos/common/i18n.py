"""Minimal translation layer for the NovaOS apps.

Source strings are English. Translations live in ``novaos/locale/<lang>.py``
as a plain ``MESSAGES`` dict, which keeps the apps free of a build step.
``tests/test_i18n.py`` checks that every ``_("...")`` string used in the
sources has a German translation.
"""

from __future__ import annotations

import importlib
import os

SUPPORTED = ("de",)


def detect_language() -> str:
    for var in ("LANGUAGE", "LC_ALL", "LC_MESSAGES", "LANG"):
        value = os.environ.get(var, "")
        if value and value not in ("C", "POSIX", "C.UTF-8"):
            return value.split(":")[0].split(".")[0].split("_")[0].lower()
    return "en"


LANG = detect_language()
_catalog: dict[str, str] = {}
if LANG in SUPPORTED:
    try:
        _catalog = importlib.import_module(f"novaos.locale.{LANG}").MESSAGES
    except Exception:  # pragma: no cover - broken catalog must not kill apps
        _catalog = {}


def _(text: str) -> str:
    """Translate ``text`` into the session language."""
    return _catalog.get(text, text)


def ngettext(singular: str, plural: str, n: int) -> str:
    return _(singular) if n == 1 else _(plural)


def set_language(lang: str) -> None:
    """Switch catalog at runtime (used by tests)."""
    global LANG, _catalog
    LANG = lang
    if lang in SUPPORTED:
        _catalog = importlib.import_module(f"novaos.locale.{lang}").MESSAGES
    else:
        _catalog = {}
