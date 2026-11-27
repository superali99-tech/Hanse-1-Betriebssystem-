import os

from novaos.store import webapps


def test_three_official_web_apps():
    ids = [w.id for w in webapps.WEBAPPS]
    assert ids == ["claude", "chatgpt", "whatsapp"]
    assert webapps.get("whatsapp").url == "https://web.whatsapp.com/"
    assert all(w.url.startswith("https://") for w in webapps.WEBAPPS)
    assert [w.id for w in webapps.search("chat")] == ["chatgpt"] and webapps.search("") == []


def test_desktop_entry_and_launch_command(tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))
    app = webapps.get("claude")
    entry = webapps.desktop_entry(app)
    assert "Exec=/usr/libexec/novaos/nova-webapp claude" in entry and "StartupWMClass=novaos-webapp-claude" in entry
    assert not webapps.installed(app)
    monkeypatch.setattr(webapps, "_refresh_menu", lambda: None)
    webapps.install(app)
    assert webapps.installed(app) and os.access(webapps.desktop_path(app), os.X_OK)
    webapps.uninstall(app)
    assert not webapps.installed(app)
    cmd = webapps.launch_command(app, "firefox-esr")
    assert cmd[0] == "firefox-esr" and cmd[-1] == "https://claude.ai/" and "--profile" in cmd and "novaos-webapp-claude" in cmd
