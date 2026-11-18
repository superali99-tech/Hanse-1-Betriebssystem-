import os
import re
import subprocess
import sys

from novaos.locale.de import MESSAGES

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def test_all_strings_translated():
    res = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "extract-strings.py"), "--missing", "de"],
                         capture_output=True, text=True)
    assert res.returncode == 0, "untranslated strings:\n" + res.stdout


def test_placeholders_preserved():
    for src, dst in MESSAGES.items():
        assert set(re.findall(r"\{\w+\}", src)) == set(re.findall(r"\{\w+\}", dst)), src
