import importlib.machinery
import importlib.util
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PATH = os.path.join(ROOT, "packages", "novaos-desktop", "root", "usr", "lib", "novaos", "novaos-rebrand")
loader = importlib.machinery.SourceFileLoader("novaos_rebrand", PATH)
spec = importlib.util.spec_from_loader("novaos_rebrand", loader)
rebrand = importlib.util.module_from_spec(spec)
loader.exec_module(rebrand)

DOLPHIN = """[Desktop Entry]
Type=Application
Exec=dolphin %u
Icon=system-file-manager
Name=Dolphin
Name[de]=Dolphin
GenericName=File Manager
GenericName[de]=Dateiverwaltung
MimeType=inode/directory;
Actions=new-window;

[Desktop Action new-window]
Name=Open a New Window
Name[de]=Neues Fenster öffnen
Exec=dolphin %u
"""


def test_rebrand_keeps_actions_and_exec():
    out = rebrand.rebrand_text(DOLPHIN, "Nova Files", "nova-files", "File Manager", "Dateimanager", False)
    entry, action = out.split("[Desktop Action new-window]")
    lines = entry.splitlines()
    assert "Name=Nova Files" in lines and not any(l.startswith("Name[") for l in lines)
    assert entry.endswith("X-NovaOS-Rebranded=true\n\n")  # group separator kept
    assert "Icon=nova-files" in entry and "system-file-manager" not in entry
    assert "Exec=dolphin %u" in entry and "MimeType=inode/directory;" in entry
    assert "GenericName[de]=Dateimanager" in entry
    assert "X-NovaOS-Upstream-Name=Dolphin" in entry
    assert rebrand.MARK in entry
    # actions untouched
    assert "Name=Open a New Window" in action and "Name[de]=Neues Fenster öffnen" in action


def test_rebrand_idempotent():
    once = rebrand.rebrand_text(DOLPHIN, "Nova Files", "nova-files", "File Manager", "Dateimanager", True)
    twice = rebrand.rebrand_text(once, "Nova Files", "nova-files", "File Manager", "Dateimanager", True)
    assert once == twice
    assert once.count("NoDisplay=true") == 1
