#!/usr/bin/env python3
"""A tiny ntfy-compatible relay for testing Nova Family without internet - only for local tests.

    tests/family/fake_ntfy.py CERT KEY [PORT]

Supports what Nova Family uses: POST /<topic> (publish), GET /<topic>/json?poll=1&since=<id|all|12h>
and GET /<topic>/json (stream with keepalive). Messages are kept in memory.
"""
import json, secrets, ssl, sys, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

CERT, KEY = sys.argv[1], sys.argv[2]
PORT = int(sys.argv[3]) if len(sys.argv) > 3 else 8445
topics = {}
cond = threading.Condition()


def after(lst, since):
    if not since or since == "all":
        return list(lst)
    if since[-1] in "hms" and since[:-1].isdigit():
        n = int(since[:-1]) * {"h": 3600, "m": 60, "s": 1}[since[-1]]
        return [m for m in lst if m["time"] >= time.time() - n]
    for i, m in enumerate(lst):
        if m["id"] == since:
            return lst[i + 1:]
    return list(lst)


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def do_POST(self):
        topic = urlparse(self.path).path.strip("/")
        body = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode("utf-8")
        m = {"id": secrets.token_hex(6), "time": int(time.time()), "event": "message", "topic": topic, "message": body}
        with cond:
            topics.setdefault(topic, []).append(m)
            cond.notify_all()
        print("publish", topic[:16], len(body), flush=True)
        out = json.dumps(m).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def do_GET(self):
        u = urlparse(self.path)
        parts = u.path.strip("/").split("/")
        if len(parts) != 2 or parts[1] != "json":
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        topic, q = parts[0], parse_qs(u.query)
        since = q.get("since", [""])[0]
        self.send_response(200)
        self.send_header("Content-Type", "application/x-ndjson")
        if "poll" in q:
            with cond:
                msgs = after(topics.get(topic, []), since)
            out = "".join(json.dumps(m) + "\n" for m in msgs).encode()
            self.send_header("Content-Length", str(len(out)))
            self.end_headers()
            self.wfile.write(out)
            return
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()

        def chunk(s):
            b = s.encode()
            self.wfile.write(b"%x\r\n%s\r\n" % (len(b), b))
            self.wfile.flush()
        try:
            chunk(json.dumps({"id": secrets.token_hex(6), "time": int(time.time()), "event": "open", "topic": topic}) + "\n")
            with cond:
                sent = len(topics.get(topic, [])) if not since else len(topics.get(topic, [])) - len(after(topics.get(topic, []), since))
            while True:
                with cond:
                    cond.wait(timeout=30)
                    lst = topics.get(topic, [])
                    new = lst[sent:]
                    sent = len(lst)
                for m in new:
                    chunk(json.dumps(m) + "\n")
                if not new:
                    chunk(json.dumps({"id": secrets.token_hex(6), "time": int(time.time()), "event": "keepalive", "topic": topic}) + "\n")
        except (BrokenPipeError, ConnectionResetError, ssl.SSLError):
            return


srv = ThreadingHTTPServer(("127.0.0.1", PORT), H)
ctx = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
ctx.load_cert_chain(CERT, KEY)
srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
print("fake ntfy on https://127.0.0.1:%d" % PORT, flush=True)
srv.serve_forever()
