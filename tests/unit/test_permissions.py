from novaos.common.i18n import set_language
from novaos.store import permissions as P

META = """[Application]
name=org.example.App
runtime=org.freedesktop.Platform/x86_64/24.08

[Context]
shared=network;ipc;
sockets=x11;pulseaudio;
devices=dri;
filesystems=home;xdg-download:ro;!host;

[Session Bus Policy]
org.freedesktop.Notifications=talk
org.freedesktop.Flatpak=talk
"""


def setup_module():
    set_language("en")


def test_analyze_levels():
    perms = P.analyze(META)
    titles = [p.title for p in perms]
    assert perms[0].level == P.HIGH  # sorted by severity
    assert "Network" in titles
    assert "Legacy display system (X11)" in titles  # x11 without wayland
    assert "Home folder" in titles
    assert not any("!host" in p.detail for p in perms)
    assert P.summary(perms)[0] == P.HIGH


def test_wayland_app_is_low():
    meta = "[Context]\nsockets=wayland;fallback-x11;\ndevices=dri;\n"
    perms = P.analyze(meta)
    assert all(p.level == P.LOW for p in perms)
    assert P.summary(perms)[0] == P.LOW


def test_empty():
    assert P.analyze("") == []
    assert P.summary([])[0] == P.LOW
