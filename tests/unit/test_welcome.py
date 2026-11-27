"""When the big NovaOS 2 card appears."""

import pytest

wm = pytest.importorskip("novaos.welcome.main")


class FakeSettings:
    def __init__(self, **values):
        self.values = values

    def value(self, key, default=None, type=None):  # noqa: A002 - QSettings signature
        return self.values.get(key, default)


@pytest.mark.parametrize("edition,values,shown", [
    (1, {"done": True}, False),                                      # NovaOS 1: no card
    (2, {"done": False}, False),                                     # assistant not done yet
    (2, {"done": True, "seen_edition": 2}, True),                    # saw the first card: once more
    (2, {"done": True, "nova2_tour": wm.TOUR_VERSION}, False),       # saw the new card
])
def test_card_shown_once_per_version(monkeypatch, edition, values, shown):
    monkeypatch.setattr(wm, "edition", lambda: edition)
    monkeypatch.setattr(wm, "SETTINGS", FakeSettings(**values))
    monkeypatch.setattr(wm.sys, "argv", ["nova-welcome", "--autostart"])
    assert wm.show_nova2_tour(live=False) is shown


def test_no_old_wording():
    import inspect

    assert "just like before" not in inspect.getsource(wm)
