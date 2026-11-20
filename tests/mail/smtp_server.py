#!/usr/bin/env python3
"""Small SMTP submission server for testing Nova Mail - only for local test setups.

    tests/mail/smtp_server.py CERT KEY USERS MAILDIR_ROOT [OUTBOX]

Port 3465: TLS from the start; port 3587: STARTTLS. Login (AUTH PLAIN/LOGIN) only after TLS,
checked against USERS (lines "user@domain:{PLAIN}password", the Dovecot passwd-file of the test).
Mails to local users are written into MAILDIR_ROOT/<user>/Maildir/new (Dovecot reads them),
all others into OUTBOX. Each delivery prints one line.
"""
import base64, os, socket, socketserver, ssl, sys, threading, time

CERT, KEY, USERS, ROOT = sys.argv[1:5]
OUTBOX = sys.argv[5] if len(sys.argv) > 5 else os.path.join(ROOT, "outbox")
users = {}
for line in open(USERS):
    if ":" in line:
        u, p = line.strip().split(":", 1)
        users[u] = p.replace("{PLAIN}", "")
ctx = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
ctx.load_cert_chain(CERT, KEY)
counter = [0]


def own(path):
    """The test Dovecot runs as "vmail": hand it the files (when this server runs as root)."""
    try:
        import pwd
        pw = pwd.getpwnam("vmail")
        os.chown(path, pw.pw_uid, pw.pw_gid)
    except (KeyError, PermissionError, ImportError):
        pass


def deliver(rcpt, data):
    counter[0] += 1
    name = "%d.%d_%d.novatest" % (time.time(), os.getpid(), counter[0])
    if rcpt.lower() in users:
        d = os.path.join(ROOT, rcpt.lower(), "Maildir")
        for sub in ("new", "cur", "tmp"):
            os.makedirs(os.path.join(d, sub), exist_ok=True)
        for x in (os.path.join(ROOT, rcpt.lower()), d, os.path.join(d, "new"), os.path.join(d, "cur"), os.path.join(d, "tmp")):
            own(x)
        tmp = os.path.join(d, "tmp", name)
        with open(tmp, "wb") as f:
            f.write(data)
        own(tmp)
        os.rename(tmp, os.path.join(d, "new", name))
        print("delivered to", rcpt, len(data), "bytes", flush=True)
    else:
        os.makedirs(OUTBOX, exist_ok=True)
        with open(os.path.join(OUTBOX, name + "_" + rcpt.replace("/", "_")), "wb") as f:
            f.write(data)
        print("outbox", rcpt, len(data), "bytes", flush=True)


class Handler(socketserver.StreamRequestHandler):
    implicit = False

    def setup(self):
        if self.implicit:
            self.request = ctx.wrap_socket(self.request, server_side=True)
        self.tls = self.implicit
        self.connection = self.request
        self.rfile = self.request.makefile("rb")
        self.wfile = self.request.makefile("wb")

    def say(self, s):
        self.wfile.write((s + "\r\n").encode())
        self.wfile.flush()

    def line(self):
        l = self.rfile.readline(65536)
        if not l:
            raise EOFError
        return l.decode("utf-8", "replace").rstrip("\r\n")

    def handle(self):
        user, mail_from, rcpts = None, None, []
        try:
            self.say("220 nova.test ESMTP test server")
            while True:
                cmd = self.line()
                up = cmd.upper()
                if up.startswith("EHLO") or up.startswith("HELO"):
                    caps = ["250-nova.test", "250-SIZE 52428800", "250-8BITMIME"]
                    if not self.tls:
                        caps.append("250-STARTTLS")
                    else:
                        caps.append("250-AUTH PLAIN LOGIN")
                    caps.append("250 SMTPUTF8")
                    for c in caps:
                        self.say(c)
                elif up == "STARTTLS" and not self.tls:
                    self.say("220 go ahead")
                    self.request = ctx.wrap_socket(self.request, server_side=True)
                    self.rfile = self.request.makefile("rb")
                    self.wfile = self.request.makefile("wb")
                    self.tls = True
                elif up.startswith("AUTH"):
                    if not self.tls:
                        self.say("530 5.7.0 Must issue a STARTTLS command first")
                        continue
                    parts = cmd.split()
                    mech = parts[1].upper() if len(parts) > 1 else ""
                    if mech == "PLAIN":
                        b = parts[2] if len(parts) > 2 else (self.say("334 ") or self.line())
                        _, u, p = base64.b64decode(b).decode("utf-8").split("\0")
                    elif mech == "LOGIN":
                        self.say("334 VXNlcm5hbWU6")
                        u = base64.b64decode(self.line()).decode("utf-8")
                        self.say("334 UGFzc3dvcmQ6")
                        p = base64.b64decode(self.line()).decode("utf-8")
                    else:
                        self.say("504 unknown mechanism")
                        continue
                    if users.get(u) == p:
                        user = u
                        self.say("235 2.7.0 Authentication successful")
                    else:
                        self.say("535 5.7.8 Authentication credentials invalid")
                elif up.startswith("MAIL FROM:"):
                    if not user:
                        self.say("530 5.7.0 Authentication required")
                        continue
                    mail_from = cmd[10:].strip().strip("<>").split()[0] if cmd[10:].strip() else ""
                    rcpts = []
                    self.say("250 OK")
                elif up.startswith("RCPT TO:"):
                    r = cmd[8:].strip().strip("<>")
                    if "@" not in r or (r.endswith("@nova.test") and r.lower() not in users):
                        self.say("550 5.1.1 No such user")
                        continue
                    rcpts.append(r)
                    self.say("250 OK")
                elif up == "DATA":
                    if not rcpts:
                        self.say("503 no recipients")
                        continue
                    self.say("354 End data with <CR><LF>.<CR><LF>")
                    data = bytearray()
                    while True:
                        l = self.rfile.readline(1 << 20)
                        if l in (b".\r\n", b".\n", b""):
                            break
                        if l.startswith(b".."):
                            l = l[1:]
                        data += l
                    for r in rcpts:
                        deliver(r, b"Return-Path: <" + mail_from.encode() + b">\r\n" + bytes(data))
                    self.say("250 OK queued")
                elif up == "RSET":
                    rcpts = []
                    self.say("250 OK")
                elif up == "NOOP":
                    self.say("250 OK")
                elif up == "QUIT":
                    self.say("221 Bye")
                    return
                else:
                    self.say("500 unknown command")
        except (EOFError, ConnectionError, ssl.SSLError):
            return


class Implicit(Handler):
    implicit = True


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


for port, handler in ((3465, Implicit), (3587, Handler)):
    srv = Server(("127.0.0.1", port), handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    print("SMTP test server on 127.0.0.1:%d" % port, flush=True)
while True:
    time.sleep(3600)
