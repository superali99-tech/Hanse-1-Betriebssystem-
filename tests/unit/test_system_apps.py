from novaos.store import system_apps
from novaos.updates.apt import parse_upgradable

SHOW = """Package: novaos-browser
Version: 0.1.1.92
Nova-App: Nova Browser
Nova-News: Optimierung und Stabilitätsverbesserung
Nova-News-En: Optimisation and stability improvements
Description: Nova Browser - Firefox with the NovaOS settings
 more text

Package: novaos-browser
Version: 0.1.1.91
"""


def test_fields_of_the_first_stanza():
    f = system_apps.parse_fields(SHOW)
    assert f["Version"] == "0.1.1.92" and f["Nova-News"].startswith("Optimierung")
    assert f["Description"].endswith("more text")


def test_nova_browser_pending_from_novaos_or_firefox_update():
    ups = parse_upgradable("Listing...\n"
                           "novaos-browser/stable 0.1.1.92 all [upgradable from: 0.1.1.91]\n"
                           "firefox-esr/stable-security 140.16.0esr-1~deb13u1 amd64 [upgradable from: 140.15.0esr-1~deb13u1]\n"
                           "libc6/stable 2.41-12+deb13u5 amd64 [upgradable from: 2.41-12+deb13u4]\n")
    p = system_apps.pending(ups)
    assert len(p) == 1 and p[0].app.name == "Nova Browser"
    assert sorted(p[0].packages) == ["firefox-esr", "novaos-browser"] and p[0].security
    assert system_apps.pending(parse_upgradable("Listing...\nlibc6/stable 2 amd64 [upgradable from: 1]\n")) == []


def test_new_novaos_package_counts_as_update():
    assert system_apps.parse_policy("novaos-browser:\n  Installed: (none)\n  Candidate: 0.1.1.92\n") == ("", "0.1.1.92")
    assert system_apps.parse_policy("x:\n  Installed: 1.0\n  Candidate: 1.0\n") == ("1.0", "1.0")
    found = system_apps.with_new_packages([], policy_of=lambda pkg: ("", "0.1.1.92"))
    assert len(found) == 1 and found[0].packages == ["novaos-browser"] and found[0].version == "0.1.1.92"
    assert system_apps.with_new_packages([], policy_of=lambda pkg: ("0.1.1.92", "0.1.1.92")) == []
