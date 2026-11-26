from novaos.updates.apt import parse_helper_line, parse_upgradable

APT_OUT = """Listing...
firefox-esr/stable-security 140.16.0esr-1~deb13u1 amd64 [upgradable from: 140.15.0esr-1~deb13u1]
libc6/stable 2.41-12+deb13u5 amd64 [upgradable from: 2.41-12+deb13u4]
tzdata/stable-updates,stable-updates 2026b-0+deb13u1 all [upgradable from: 2026a-0+deb13u1]
"""


def test_parse_upgradable():
    ups = parse_upgradable(APT_OUT)
    assert [u.name for u in ups] == ["firefox-esr", "libc6", "tzdata"]  # security first
    assert ups[0].security and not ups[1].security
    assert ups[0].old == "140.15.0esr-1~deb13u1" and ups[0].new == "140.16.0esr-1~deb13u1"


def test_helper_lines():
    assert parse_helper_line("PROGRESS 42 Unpacking foo\n") == ("progress", 42, "Unpacking foo")
    assert parse_helper_line("ERROR broken")[0] == "error"
    assert parse_helper_line("DONE")[0] == "done"
    assert parse_helper_line("LOG Get:1 http://deb.debian.org")[0] == "log"


def test_whatsnew_reads_german_or_english(tmp_path):
    from novaos.updates.main import whatsnew
    f = tmp_path / "whatsnew-pc.txt"
    f.write_text("# comment\nGlas-Design|Glass design\n\nNur Deutsch\n", encoding="utf-8")
    assert whatsnew(str(f), german=True) == ["Glas-Design", "Nur Deutsch"]
    assert whatsnew(str(f), german=False) == ["Glass design", "Nur Deutsch"]
    assert whatsnew(str(tmp_path / "missing.txt"), german=True) == []
