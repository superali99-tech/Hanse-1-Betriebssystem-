#!/usr/bin/env python3
"""List all translatable _("...") strings of the Nova apps.

Usage: tools/extract-strings.py [--missing de]
Uses the Python tokenizer, so only real string literals passed to _() count.
"""
import ast
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "novaos")


def strings() -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}
    for dirpath, _dirs, files in os.walk(ROOT):
        if "locale" in dirpath:
            continue
        for f in files:
            if not f.endswith(".py"):
                continue
            path = os.path.join(dirpath, f)
            tree = ast.parse(open(path, encoding="utf-8").read(), path)
            for node in ast.walk(tree):
                if (isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == "_"
                        and node.args and isinstance(node.args[0], ast.Constant) and isinstance(node.args[0].value, str)):
                    found.setdefault(node.args[0].value, []).append(os.path.relpath(path, ROOT))
    return found


if __name__ == "__main__":
    found = strings()
    if len(sys.argv) > 2 and sys.argv[1] == "--missing":
        sys.path.insert(0, os.path.join(ROOT, ".."))
        import importlib
        cat = importlib.import_module(f"novaos.locale.{sys.argv[2]}").MESSAGES
        missing = [s for s in found if s not in cat]
        for s in missing:
            print(repr(s))
        sys.exit(1 if missing else 0)
    for s in sorted(found):
        print(repr(s))
