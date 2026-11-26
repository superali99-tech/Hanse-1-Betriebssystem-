"""Confirm an e-mail address with a 6-digit code (Mein Konto, NovaOS PC).

NovaOS has no server of its own: the code is sent through the user's own e-mail account
(SMTP with TLS) to the address being added. The password is used only for that one
message and is never stored. What is stored (~/.config/novaos/account.json): the address,
whether it is confirmed, and when. Qt-independent and tested (tests/unit/test_account.py).
"""

from __future__ import annotations

import hmac
import json
import os
import re
import secrets
import smtplib
import ssl
import time
from dataclasses import dataclass
from email.message import EmailMessage

CODE_SECONDS = 10 * 60
CODE_TRIES = 5
RESEND_SECONDS = 60


@dataclass(frozen=True)
class Provider:
    key: str
    name: str
    host: str
    port: int
    starttls: bool
    help: str = ""
    app_password: bool = False   # needs an app password instead of the normal one


PROVIDERS = [
    (Provider("gmx", "GMX", "mail.gmx.net", 465, False, "https://hilfe.gmx.net/pop-imap/einschalten.html"),
     ("gmx.de", "gmx.net", "gmx.at", "gmx.ch", "gmx.eu", "gmx.info", "gmx.org")),
    (Provider("webde", "WEB.DE", "smtp.web.de", 587, True, "https://hilfe.web.de/pop-imap/einschalten.html"),
     ("web.de",)),
    (Provider("gmail", "Gmail", "smtp.gmail.com", 465, False, "https://myaccount.google.com/apppasswords", True),
     ("gmail.com", "googlemail.com")),
    (Provider("tonline", "T-Online", "securesmtp.t-online.de", 465, False,
              "https://www.telekom.de/hilfe/apps-dienste/e-mail/e-mail-programme/passwort", True),
     ("t-online.de", "magenta.de")),
    (Provider("outlook", "Outlook / Hotmail", "smtp-mail.outlook.com", 587, True,
              "https://support.microsoft.com/de-de/outlook/pop-imap-and-smtp-settings-for-outlook-com", True),
     ("outlook.com", "outlook.de", "hotmail.com", "hotmail.de", "live.com", "live.de", "msn.com")),
    (Provider("yahoo", "Yahoo", "smtp.mail.yahoo.com", 465, False,
              "https://login.yahoo.com/account/security/app-passwords", True),
     ("yahoo.com", "yahoo.de", "ymail.com", "rocketmail.com")),
    (Provider("icloud", "iCloud", "smtp.mail.me.com", 587, True, "https://account.apple.com/account/manage", True),
     ("icloud.com", "me.com", "mac.com")),
    (Provider("freenet", "freenet", "mx.freenet.de", 587, True), ("freenet.de",)),
    (Provider("vodafone", "Vodafone", "smtp.vodafonemail.de", 587, True), ("vodafone.de", "vodafonemail.de", "arcor.de")),
    (Provider("aol", "AOL", "smtp.aol.com", 465, False, "https://login.aol.com/account/security/app-passwords", True),
     ("aol.com", "aol.de")),
    (Provider("mailbox", "mailbox.org", "smtp.mailbox.org", 465, False), ("mailbox.org",)),
    (Provider("posteo", "Posteo", "posteo.de", 465, False), ("posteo.de", "posteo.net", "posteo.eu")),
    (Provider("ionos", "IONOS (1&1)", "smtp.ionos.de", 465, False), ("online.de", "onlinehome.de")),
]

EMAIL_RE = re.compile(r"^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                      r"(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")


def valid_email(email: str) -> bool:
    email = (email or "").strip()
    return len(email) <= 254 and bool(EMAIL_RE.match(email)) and ".." not in email


def domain(email: str) -> str:
    return email.strip().rsplit("@", 1)[-1].lower() if "@" in (email or "") else ""


def provider_for(email: str) -> Provider:
    """The known provider of the address, otherwise smtp.<domain> with STARTTLS."""
    d = domain(email)
    for p, domains in PROVIDERS:
        if d in domains:
            return p
    return Provider("other", d, "smtp." + d, 587, True)


# ------------------------------------------------------------------ the code
@dataclass
class Code:
    value: str
    sent_at: float
    tries: int = 0

    @staticmethod
    def new(now: float | None = None) -> "Code":
        return Code(f"{secrets.randbelow(1_000_000):06d}", time.time() if now is None else now)

    def check(self, typed: str, now: float | None = None) -> str:
        """'ok', 'wrong', 'expired' or 'blocked' (too many tries). Every call counts as a try."""
        now = time.time() if now is None else now
        if self.tries >= CODE_TRIES:
            return "blocked"
        if now - self.sent_at > CODE_SECONDS or now < self.sent_at:
            return "expired"
        self.tries += 1
        t = re.sub(r"\s", "", typed or "")
        if len(t) == len(self.value) and hmac.compare_digest(t, self.value):
            return "ok"
        return "blocked" if self.tries >= CODE_TRIES else "wrong"

    def can_resend(self, now: float | None = None) -> bool:
        return (time.time() if now is None else now) - self.sent_at >= RESEND_SECONDS


def message(sender: str, to: str, code: str, german: bool) -> EmailMessage:
    m = EmailMessage()
    m["From"] = sender
    m["To"] = to
    if german:
        m["Subject"] = f"Dein NovaOS-Code: {code}"
        m.set_content(f"Hallo!\n\nDein Bestätigungscode für NovaOS lautet:\n\n    {code}\n\n"
                      "Gib ihn in „Mein Konto“ ein. Er gilt 10 Minuten.\n"
                      "Wenn du das nicht warst, kannst du diese E-Mail einfach löschen.\n\nDein NovaOS\n")
    else:
        m["Subject"] = f"Your NovaOS code: {code}"
        m.set_content(f"Hello!\n\nYour NovaOS confirmation code is:\n\n    {code}\n\n"
                      "Enter it in \"My account\". It is valid for 10 minutes.\n"
                      "If this was not you, just delete this e-mail.\n\nYour NovaOS\n")
    return m


def send_code(email: str, password: str, code: str, german: bool, timeout: int = 30,
              provider: Provider | None = None, context: ssl.SSLContext | None = None) -> tuple[bool, str]:
    """Sends the code through the user's own account (TLS, certificates checked).
    Returns (ok, reason): reason 'auth', 'connect' or the error text.
    (provider/context only for the tests: a local server with its own certificate.)"""
    p = provider or provider_for(email)
    ctx = context or ssl.create_default_context()
    msg = message(email, email, code, german)
    stage = "connect"   # the step that fails decides the message: connect / login / send
    try:
        if p.starttls:
            with smtplib.SMTP(p.host, p.port, timeout=timeout) as s:
                s.starttls(context=ctx)
                stage = "login"
                s.login(email, password)
                stage = "send"
                s.send_message(msg)
        else:
            with smtplib.SMTP_SSL(p.host, p.port, timeout=timeout, context=ctx) as s:
                stage = "login"
                s.login(email, password)
                stage = "send"
                s.send_message(msg)
        return True, ""
    except smtplib.SMTPAuthenticationError:
        return False, "auth"
    except (OSError, smtplib.SMTPException) as exc:
        if stage == "login":
            return False, "auth"     # connected, but the account did not let us in
        if stage == "connect":
            return False, "connect"  # no server, no internet or no trusted certificate
        return False, str(exc) or exc.__class__.__name__


# ------------------------------------------------------------------ storage
def store_path() -> str:
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.join(os.path.expanduser("~"), ".config")
    return os.path.join(base, "novaos", "account.json")


def load(path: str | None = None) -> dict:
    try:
        with open(path or store_path(), encoding="utf-8") as fh:
            data = json.load(fh)
            return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save(data: dict, path: str | None = None) -> None:
    path = path or store_path()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)
    os.chmod(tmp, 0o600)
    os.replace(tmp, path)
