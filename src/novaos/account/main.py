"""Mein Konto – add an e-mail address to NovaOS and confirm it with a 6-digit code.

The code really arrives by e-mail: NovaOS sends it through the user's own e-mail account
(see novaos.account.verify). The password is needed only for that and is not stored.
"""

from __future__ import annotations

import os
import pwd
import sys
import threading
import time

from PyQt6.QtCore import QObject, Qt, QTimer, pyqtSignal
from PyQt6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from novaos.account import verify
from novaos.common.app import create_app
from novaos.common.i18n import _


def german() -> bool:
    lang = os.environ.get("LANGUAGE") or os.environ.get("LC_ALL") or os.environ.get("LC_MESSAGES") or os.environ.get("LANG") or ""
    return lang.startswith("de")


def full_name() -> str:
    try:
        p = pwd.getpwuid(os.getuid())
        return (p.pw_gecos.split(",")[0] or p.pw_name).strip()
    except (KeyError, OSError):
        return os.environ.get("USER", "")


class _Bridge(QObject):
    sent = pyqtSignal(bool, str)


class AccountWindow(QWidget):
    def __init__(self, sender=verify.send_code):
        super().__init__()
        self.sender = sender
        self.code: verify.Code | None = None
        self.pending_email = ""
        self.password = ""
        self.bridge = _Bridge()
        self.bridge.sent.connect(self._sent)
        self.setWindowTitle(_("My account"))
        self.resize(620, 560)
        root = QVBoxLayout(self)
        root.setContentsMargins(28, 24, 28, 22)
        self.stack = QStackedWidget()
        root.addWidget(self.stack)
        self.stack.addWidget(self._page_overview())
        self.stack.addWidget(self._page_email())
        self.stack.addWidget(self._page_code())
        self.timer = QTimer(self)
        self.timer.timeout.connect(self._tick)
        self.timer.start(1000)
        self.show_overview()

    # --------------------------------------------------------------- pages
    def _title(self, lay: QVBoxLayout, title: str, sub: str = "") -> None:
        t = QLabel(title)
        t.setObjectName("NovaTitle")
        lay.addWidget(t)
        if sub:
            s = QLabel(sub)
            s.setObjectName("NovaMuted")
            s.setWordWrap(True)
            lay.addWidget(s)

    def _page_overview(self) -> QWidget:
        w = QWidget()
        lay = QVBoxLayout(w)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(14)
        hero = QFrame()
        hero.setObjectName("NovaHero")
        hl = QHBoxLayout(hero)
        hl.setContentsMargins(22, 20, 22, 20)
        hl.setSpacing(16)
        name = full_name()
        self.avatar = QLabel((name[:1] or "?").upper())
        self.avatar.setFixedSize(64, 64)
        self.avatar.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.avatar.setStyleSheet("background: rgba(255,255,255,0.25); border-radius: 32px; color: white; "
                                  "font-size: 24pt; font-weight: 800;")
        hl.addWidget(self.avatar)
        tl = QVBoxLayout()
        n = QLabel(name or _("My account"))
        n.setStyleSheet("font-size: 18pt; font-weight: 800;")
        self.hero_mail = QLabel("")
        tl.addWidget(n)
        tl.addWidget(self.hero_mail)
        hl.addLayout(tl, 1)
        lay.addWidget(hero)

        card = QFrame()
        card.setObjectName("NovaPanel")
        cl = QVBoxLayout(card)
        cl.setContentsMargins(20, 16, 20, 16)
        h = QLabel(_("E-mail address"))
        h.setObjectName("NovaSubtitle")
        cl.addWidget(h)
        self.mail_line = QLabel("")
        self.mail_line.setWordWrap(True)
        self.mail_line.setStyleSheet("font-size: 12pt;")
        cl.addWidget(self.mail_line)
        row = QHBoxLayout()
        self.btn_add = QPushButton(_("Add e-mail"))
        self.btn_add.setObjectName("NovaPrimary")
        self.btn_add.clicked.connect(lambda: self.show_email_page(""))
        self.btn_remove = QPushButton(_("Remove"))
        self.btn_remove.clicked.connect(self.remove_email)
        row.addWidget(self.btn_add)
        row.addWidget(self.btn_remove)
        row.addStretch(1)
        cl.addLayout(row)
        lay.addWidget(card)
        note = QLabel(_("Your e-mail address stays on this computer. NovaOS does not send it anywhere – the code "
                        "is sent through your own e-mail account."))
        note.setObjectName("NovaMuted")
        note.setWordWrap(True)
        lay.addWidget(note)
        lay.addStretch(1)
        return w

    def _page_email(self) -> QWidget:
        w = QWidget()
        lay = QVBoxLayout(w)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(10)
        self._title(lay, _("Add e-mail"), _("We send a 6-digit code to this address. Then you enter it here."))
        lay.addSpacing(6)
        lay.addWidget(QLabel(_("E-mail address")))
        self.f_email = QLineEdit()
        self.f_email.setObjectName("NovaField")
        self.f_email.setPlaceholderText("name@web.de")
        self.f_email.textChanged.connect(self._provider_hint)
        lay.addWidget(self.f_email)
        lay.addWidget(QLabel(_("Password of this e-mail account")))
        self.f_pass = QLineEdit()
        self.f_pass.setObjectName("NovaField")
        self.f_pass.setEchoMode(QLineEdit.EchoMode.Password)
        self.f_pass.returnPressed.connect(self.send)
        lay.addWidget(self.f_pass)
        self.hint = QLabel("")
        self.hint.setObjectName("NovaMuted")
        self.hint.setWordWrap(True)
        self.hint.setOpenExternalLinks(True)
        lay.addWidget(self.hint)
        self.email_msg = QLabel("")
        self.email_msg.setWordWrap(True)
        self.email_msg.setStyleSheet("color: #E5484D;")
        lay.addWidget(self.email_msg)
        lay.addStretch(1)
        row = QHBoxLayout()
        back = QPushButton(_("Back"))
        back.clicked.connect(self.show_overview)
        self.btn_send = QPushButton(_("Send code"))
        self.btn_send.setObjectName("NovaPrimary")
        self.btn_send.clicked.connect(self.send)
        row.addWidget(back)
        row.addStretch(1)
        row.addWidget(self.btn_send)
        lay.addLayout(row)
        return w

    def _page_code(self) -> QWidget:
        w = QWidget()
        lay = QVBoxLayout(w)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(10)
        self._title(lay, _("Enter the code"))
        self.code_info = QLabel("")
        self.code_info.setWordWrap(True)
        self.code_info.setObjectName("NovaMuted")
        lay.addWidget(self.code_info)
        lay.addSpacing(10)
        self.f_code = QLineEdit()
        self.f_code.setMaxLength(6)
        self.f_code.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.f_code.setPlaceholderText("123456")
        self.f_code.setStyleSheet("font-size: 26pt; font-weight: 700; letter-spacing: 10px; padding: 10px; "
                                  "border-radius: 16px;")
        self.f_code.returnPressed.connect(self.confirm)
        self.f_code.textChanged.connect(lambda t: self.confirm() if len(t.strip()) == 6 else None)
        lay.addWidget(self.f_code)
        self.code_msg = QLabel("")
        self.code_msg.setWordWrap(True)
        self.code_msg.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lay.addWidget(self.code_msg)
        lay.addStretch(1)
        row = QHBoxLayout()
        back = QPushButton(_("Back"))
        back.clicked.connect(lambda: self.show_email_page(self.pending_email))
        self.btn_resend = QPushButton(_("Send again"))
        self.btn_resend.clicked.connect(self.send)
        self.btn_confirm = QPushButton(_("Confirm"))
        self.btn_confirm.setObjectName("NovaPrimary")
        self.btn_confirm.clicked.connect(self.confirm)
        row.addWidget(back)
        row.addStretch(1)
        row.addWidget(self.btn_resend)
        row.addWidget(self.btn_confirm)
        lay.addLayout(row)
        return w

    # ------------------------------------------------------------ overview
    def show_overview(self) -> None:
        data = verify.load()
        email = data.get("email", "")
        if email and data.get("verified"):
            self.mail_line.setText(email + "   <span style='color:#22A06B; font-weight:700'>✓ " + _("confirmed") + "</span>")
            self.hero_mail.setText(email + "  ✓")
            self.btn_add.setText(_("Change"))
        elif email:
            self.mail_line.setText(email + "   <span style='color:#D9822B'>" + _("not confirmed") + "</span>")
            self.hero_mail.setText(email)
            self.btn_add.setText(_("Confirm"))
        else:
            self.mail_line.setText(_("No e-mail address yet."))
            self.hero_mail.setText(_("NovaOS on this computer"))
            self.btn_add.setText(_("Add e-mail"))
        self.btn_remove.setVisible(bool(email))
        self.stack.setCurrentIndex(0)

    def remove_email(self) -> None:
        verify.save({})
        self.show_overview()

    # ----------------------------------------------------------- send code
    def show_email_page(self, email: str) -> None:
        data = verify.load()
        self.f_email.setText(email or ("" if data.get("verified") else data.get("email", "")))
        self.f_pass.clear()
        self.email_msg.setText("")
        self._provider_hint()
        self.stack.setCurrentIndex(1)
        (self.f_pass if self.f_email.text() else self.f_email).setFocus()

    def _provider_hint(self) -> None:
        email = self.f_email.text().strip()
        if not verify.valid_email(email):
            self.hint.setText(_("Use the password you sign in to your e-mail with. It is only used to send the code "
                                "and is not stored."))
            return
        p = verify.provider_for(email)
        text = _("The code is sent through {provider}. The password is only used for that and is not stored.").format(
            provider=p.name if p.key != "other" else p.host)
        if p.app_password:
            text += " " + _("{provider} needs an app password for this:").format(provider=p.name)
        elif p.key in ("gmx", "webde"):
            text += " " + _("At {provider}, “POP3/IMAP access” must be switched on once:").format(provider=p.name)
        if p.help:
            text += f" <a href='{p.help}'>{_('Instructions')}</a>"
        self.hint.setText(text)

    def send(self) -> None:
        if self.stack.currentIndex() == 2:  # "Send again"
            if self.code and not self.code.can_resend():
                return
            email, password = self.pending_email, self.password
        else:
            email, password = self.f_email.text().strip(), self.f_pass.text()
            if not verify.valid_email(email):
                self.email_msg.setText(_("Please enter a valid e-mail address."))
                return
            if not password:
                self.email_msg.setText(_("Please enter the password of your e-mail account."))
                return
        self.pending_email, self.password = email, password
        self.code = verify.Code.new()
        self.btn_send.setEnabled(False)
        self.btn_send.setText(_("Sending…"))
        self.email_msg.setText("")
        code, ger = self.code.value, german()

        def work():
            ok, reason = self.sender(email, password, code, ger)
            self.bridge.sent.emit(ok, reason)

        threading.Thread(target=work, daemon=True).start()

    def _sent(self, ok: bool, reason: str) -> None:
        self.btn_send.setEnabled(True)
        self.btn_send.setText(_("Send code"))
        if not ok:
            self.code = None
            p = verify.provider_for(self.pending_email)
            if reason == "auth":
                msg = _("The e-mail account did not accept the password.")
                if p.app_password:
                    msg += " " + _("{provider} needs an app password – see “Instructions” above.").format(provider=p.name)
            elif reason == "connect":
                msg = _("No connection to the e-mail server. Are you online?")
            else:
                msg = _("The code could not be sent: {reason}").format(reason=reason)
            self.email_msg.setText(msg)
            self.stack.setCurrentIndex(1)
            return
        verify.save({"email": self.pending_email, "verified": False})
        self.code_info.setText(_("We sent a code to {email}. Look in your inbox (or spam folder) and enter it here. "
                                 "It is valid for 10 minutes.").format(email=self.pending_email))
        self.code_msg.setText("")
        self._last_typed = None
        self.f_code.clear()
        self.stack.setCurrentIndex(2)
        self.f_code.setFocus()
        self._tick()

    # -------------------------------------------------------------- confirm
    def confirm(self) -> None:
        typed = self.f_code.text().strip()
        if not self.code or typed == getattr(self, "_last_typed", None):
            return  # the same code again (Enter after the automatic check) is not another try
        self._last_typed = typed
        result = self.code.check(typed)
        if result == "ok":
            self.password = ""
            self.code = None
            verify.save({"email": self.pending_email, "verified": True, "verified_at": int(time.time())})
            self.show_overview()
            return
        self.f_code.selectAll()
        self.code_msg.setStyleSheet("color: #E5484D;")
        self.code_msg.setText({
            "wrong": _("This code is not right. Please check it and try again."),
            "expired": _("The code has expired. Tap “Send again”."),
            "blocked": _("Too many tries. Tap “Send again” for a new code."),
        }[result])

    def _tick(self) -> None:
        if self.stack.currentIndex() != 2 or not self.code:
            self.btn_resend.setEnabled(self.stack.currentIndex() == 2)
            return
        wait = int(verify.RESEND_SECONDS - (time.time() - self.code.sent_at))
        self.btn_resend.setEnabled(wait <= 0)
        self.btn_resend.setText(_("Send again") if wait <= 0 else _("Send again ({s} s)").format(s=wait))


def main() -> int:
    app = create_app("org.novaos.Account", _("My account"), "user-identity")
    win = AccountWindow()
    win.show()
    if "--add-email" in sys.argv:
        win.show_email_page("")
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
