"""Web apps in Nova Store: services without a Linux app (Claude, ChatGPT, WhatsApp).

"Add" puts a real app into the start menu that opens the service's official website in
its own window (Firefox with a separate profile per web app, so it has its own taskbar
entry and login). No unofficial clients: the data goes only to the official website.
"""

from __future__ import annotations

import os
import shutil
import subprocess
from dataclasses import dataclass

LAUNCHER = "/usr/libexec/novaos/nova-webapp"


@dataclass(frozen=True)
class WebApp:
    id: str
    name: str
    url: str
    summary_de: str
    summary_en: str
    categories: str = "Network;"

    @property
    def icon(self) -> str:
        return "novaos-webapp-" + self.id

    @property
    def wm_class(self) -> str:
        return "novaos-webapp-" + self.id

    def summary(self, german: bool) -> str:
        return self.summary_de if german else self.summary_en


WEBAPPS = (
    WebApp("claude", "Claude", "https://claude.ai/",
           "KI-Assistent von Anthropic – offizielle Web-App", "AI assistant by Anthropic – official web app",
           "Network;Office;"),
    WebApp("chatgpt", "ChatGPT", "https://chatgpt.com/",
           "KI-Chat von OpenAI – offizielle Web-App", "AI chat by OpenAI – official web app",
           "Network;Office;"),
    WebApp("whatsapp", "WhatsApp", "https://web.whatsapp.com/",
           "WhatsApp Web – einmal mit dem Handy per QR-Code verbinden", "WhatsApp Web – link once with your phone (QR code)",
           "Network;Chat;InstantMessaging;"),
)


def get(app_id: str) -> WebApp | None:
    return next((w for w in WEBAPPS if w.id == app_id), None)


def search(query: str) -> list[WebApp]:
    q = query.strip().lower()
    return [w for w in WEBAPPS if q and (q in w.name.lower() or q in w.id)] if q else []


# ------------------------------------------------------------------ desktop entry
def data_home() -> str:
    return os.environ.get("XDG_DATA_HOME") or os.path.join(os.path.expanduser("~"), ".local", "share")


def desktop_path(app: WebApp) -> str:
    return os.path.join(data_home(), "applications", f"novaos-webapp-{app.id}.desktop")


def desktop_entry(app: WebApp) -> str:
    return "\n".join([
        "[Desktop Entry]",
        "Type=Application",
        f"Name={app.name}",
        f"Comment={app.summary_en}",
        f"Comment[de]={app.summary_de}",
        f"Exec={LAUNCHER} {app.id}",
        f"Icon={app.icon}",
        "Terminal=false",
        f"Categories={app.categories}",
        f"StartupWMClass={app.wm_class}",
        f"X-NovaOS-WebApp={app.id}",
        "",
    ])


def installed(app: WebApp) -> bool:
    return os.path.exists(desktop_path(app))


def install(app: WebApp) -> None:
    path = desktop_path(app)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(desktop_entry(app))
    os.chmod(path, 0o755)  # Plasma trusts executable launchers
    _refresh_menu()


def uninstall(app: WebApp) -> None:
    try:
        os.unlink(desktop_path(app))
    except FileNotFoundError:
        pass
    _refresh_menu()


def _refresh_menu() -> None:
    for cmd in ("kbuildsycoca6", "kbuildsycoca5"):
        if shutil.which(cmd):
            subprocess.Popen([cmd, "--noincremental"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return


# ------------------------------------------------------------------ starting
def profile_dir(app: WebApp) -> str:
    return os.path.join(data_home(), "novaos", "webapps", app.id)


USER_JS = """// NovaOS web app profile: quiet start, opens only the web app
user_pref("browser.startup.homepage_override.mstone", "ignore");
user_pref("browser.aboutwelcome.enabled", false);
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("browser.tabs.warnOnClose", false);
user_pref("browser.sessionstore.resume_from_crash", false);
user_pref("datareporting.policy.dataSubmissionPolicyBypassNotification", true);
"""


def browser() -> str | None:
    for b in ("firefox-esr", "firefox"):
        if shutil.which(b):
            return b
    return None


def launch_command(app: WebApp, firefox: str) -> list[str]:
    return [firefox, "--profile", profile_dir(app), "--name", app.wm_class, "--class", app.wm_class,
            "--new-window", app.url]


def launch(app: WebApp) -> bool:
    firefox = browser()
    if not firefox:
        return False
    prof = profile_dir(app)
    os.makedirs(prof, exist_ok=True)
    user_js = os.path.join(prof, "user.js")
    if not os.path.exists(user_js):
        with open(user_js, "w", encoding="utf-8") as fh:
            fh.write(USER_JS)
    subprocess.Popen(launch_command(app, firefox), start_new_session=True,
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return True
