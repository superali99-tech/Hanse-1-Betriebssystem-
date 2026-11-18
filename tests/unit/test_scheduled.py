import os

import pytest

from novaos.updates import scheduled

DAY = 86400

SIM = """NOTE: This is only a simulation!
Reading package lists...
Calculating upgrade...
The following packages will be upgraded:
  libc6 tzdata
Inst libc6 [2.41-12+deb13u4] (2.41-12+deb13u5 Debian:13.3/stable [amd64])
Inst linux-image-6.12.60+deb13-amd64 (6.12.60-1 Debian-Security:13/stable-security [amd64])
Inst tzdata [2026a-0+deb13u1] (2026b-0+deb13u1 Debian:13.3/stable-updates [all])
Conf libc6 (2.41-12+deb13u5 Debian:13.3/stable [amd64])
"""


@pytest.fixture
def state(tmp_path, monkeypatch):
    d = tmp_path / "updates"
    monkeypatch.setattr(scheduled, "STATE_DIR", str(d))
    monkeypatch.setattr(scheduled, "PREPARED", str(d / "prepared"))
    monkeypatch.setattr(scheduled, "LAST_CHECK", str(d / "last-check"))
    monkeypatch.setattr(scheduled, "LAST_RESULT", str(d / "last-result"))
    monkeypatch.setattr(scheduled, "SYSTEM_UPDATE", str(tmp_path / "system-update"))
    monkeypatch.setattr(scheduled, "CONF", str(tmp_path / "updates.conf"))
    return tmp_path


def test_parse_simulation():
    pk = scheduled.parse_simulation(SIM)
    assert [p["name"] for p in pk] == ["libc6", "linux-image-6.12.60+deb13-amd64", "tzdata"]
    assert pk[0]["old"] == "2.41-12+deb13u4" and pk[0]["new"] == "2.41-12+deb13u5"
    assert pk[1]["old"] == "" and pk[1]["security"] and not pk[0]["security"]
    assert scheduled.parse_simulation("0 upgraded, 0 newly installed") == []


def test_due_every_14_days():
    now = 1_800_000_000.0
    assert scheduled.is_due(None, now, 14)                       # never checked
    assert not scheduled.is_due(now - 13 * DAY, now, 14)
    assert scheduled.is_due(now - 14 * DAY, now, 14)
    assert scheduled.is_due(now - 14 * DAY + 3600, now, 14)      # daily timer jitter
    assert scheduled.is_due(now + 30 * DAY, now, 14)             # clock was wrong


def test_config(state):
    assert scheduled.read_config()["INTERVAL_DAYS"] == "14"      # defaults without file
    (state / "updates.conf").write_text("# comment\nENABLED=no\nINTERVAL_DAYS = \"7\"\nFLATPAK=yes\n")
    cfg = scheduled.read_config()
    assert not scheduled.is_yes(cfg["ENABLED"]) and scheduled.interval_days(cfg) == 7
    assert scheduled.interval_days({"INTERVAL_DAYS": "x"}) == 14
    assert scheduled.interval_days({"INTERVAL_DAYS": "0"}) == 1
    assert scheduled.next_check() is None                        # disabled


def test_shipped_config_is_valid():
    root = os.path.join(os.path.dirname(__file__), "..", "..")
    cfg = scheduled.read_config(os.path.join(root, "src", "data", "etc", "novaos", "updates.conf"))
    assert scheduled.is_yes(cfg["ENABLED"]) and scheduled.interval_days(cfg) == 14


def test_prepared_and_trigger(state):
    assert scheduled.pending() is None
    scheduled.write_json(scheduled.PREPARED, {"count": 3, "packages": [], "apps": []})
    assert oct(os.stat(scheduled.PREPARED).st_mode & 0o777) == "0o644"   # readable by all users
    scheduled.link_system_update()
    assert os.readlink(scheduled.SYSTEM_UPDATE) == scheduled.PREPARED
    scheduled.link_system_update()                                      # idempotent
    assert scheduled.pending()["count"] == 3
    scheduled.clear_prepared()
    assert scheduled.pending() is None and not os.path.lexists(scheduled.SYSTEM_UPDATE)


def test_clear_keeps_foreign_trigger(state):
    os.symlink("/var/lib/fwupd/pending.cap", scheduled.SYSTEM_UPDATE)   # e.g. a firmware update
    scheduled.clear_prepared()
    assert os.path.islink(scheduled.SYSTEM_UPDATE)


def test_timestamps(state):
    assert scheduled.last_check() is None
    scheduled.write_timestamp(scheduled.LAST_CHECK, 1_800_000_000)
    assert scheduled.last_check() == 1_800_000_000
    assert scheduled.next_check() == 1_800_000_000 + 14 * DAY


def test_units_reference_existing_paths():
    root = os.path.join(os.path.dirname(__file__), "..", "..", "src", "data")
    with open(os.path.join(root, "systemd-system", "novaos-offline-update.service")) as fh:
        unit = fh.read()
    assert "ConditionPathExists=" + "/var/lib/novaos/updates/prepared" in unit
    assert "/usr/libexec/novaos/nova-scheduled-update offline" in unit
    with open(os.path.join(root, "systemd", "nova-update-notifier.path")) as fh:
        assert "PathChanged=/var/lib/novaos/updates/prepared" in fh.read()
    assert scheduled.PREPARED == "/var/lib/novaos/updates/prepared"
