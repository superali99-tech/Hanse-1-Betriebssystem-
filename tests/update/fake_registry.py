#!/usr/bin/env python3
"""Small stand-in for ghcr.io to test the NovaOS Mobile software update end to end.

Implements the parts of the OCI distribution API that mobile/tools/publish_update.sh
(push) and the app (Registry.java / Updates.java: anonymous pull) use:
  GET  /token?scope=repository:NAME:pull[,push]   anonymous pull only while "public";
                                                  push needs HTTP basic auth
  HEAD/GET /v2/NAME/blobs/DIGEST                 GET redirects to /storage/... like ghcr,
                                                 and the storage rejects Authorization headers
  POST /v2/NAME/blobs/uploads/, PUT ...?digest=  monolithic upload
  GET/PUT /v2/NAME/manifests/TAG
  POST /admin/public?on=0|1                      switch the package visibility (test only)

Usage: fake_registry.py PORT CERT KEY   (HTTPS on 127.0.0.1:PORT, file storage on PORT+1)
"""
import hashlib, http.server, json, ssl, sys, threading, uuid
from urllib.parse import urlparse, parse_qs

blobs, manifests, uploads = {}, {}, {}
state = {"public": True}
TOKEN_PULL, TOKEN_PUSH = "pull-token", "push-token"
lock = threading.Lock()


class H(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.command, self.path))

    def send(self, code, body=b"", ctype="application/json", headers=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def denied(self, code=401):
        self.send(code, json.dumps({"errors": [{"code": "DENIED", "message": "denied"}]}).encode())

    def bearer(self):
        a = self.headers.get("Authorization", "")
        return a[7:] if a.startswith("Bearer ") else None

    def body(self):
        n = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(n) if n else b""

    def do_GET(self):
        self.handle_any()

    def do_HEAD(self):
        self.handle_any()

    def do_POST(self):
        self.handle_any()

    def do_PUT(self):
        self.handle_any()

    def handle_any(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        p = u.path
        if p == "/token":
            scope = q.get("scope", [""])[0]
            if "push" in scope:
                if not self.headers.get("Authorization", "").startswith("Basic "):
                    return self.denied()
                return self.send(200, json.dumps({"token": TOKEN_PUSH}).encode())
            if not state["public"]:
                return self.denied(403)
            return self.send(200, json.dumps({"token": TOKEN_PULL}).encode())
        if p == "/admin/public" and self.command == "POST":
            state["public"] = q.get("on", ["1"])[0] == "1"
            return self.send(200, b"{}")
        if p.startswith("/storage/"):
            if self.headers.get("Authorization"):
                return self.send(400, b"only one auth mechanism allowed", "text/plain")
            d = p[len("/storage/"):]
            if d not in blobs:
                return self.send(404, b"", "text/plain")
            return self.send(200, blobs[d], "application/octet-stream")
        if not p.startswith("/v2/"):
            return self.send(404, b"")
        tok = self.bearer()
        writing = self.command in ("POST", "PUT")
        if tok != TOKEN_PUSH and not (tok == TOKEN_PULL and not writing and state["public"]):
            return self.denied()
        if "/blobs/uploads/" in p:
            if self.command == "POST":
                uid = str(uuid.uuid4())
                uploads[uid] = b""
                name = p[len("/v2/"):p.index("/blobs/uploads/")]
                return self.send(202, b"", headers={"Location": "/v2/%s/blobs/uploads/%s" % (name, uid)})
            uid = p.rsplit("/", 1)[1]
            data = self.body()
            digest = q.get("digest", [""])[0]
            if "sha256:" + hashlib.sha256(data).hexdigest() != digest:
                return self.send(400, b'{"errors":[{"code":"DIGEST_INVALID"}]}')
            with lock:
                blobs[digest] = data
            return self.send(201, b"", headers={"Docker-Content-Digest": digest})
        if "/blobs/" in p:
            d = p.rsplit("/", 1)[1]
            if d not in blobs:
                return self.send(404, b'{"errors":[{"code":"BLOB_UNKNOWN"}]}')
            if self.command == "HEAD":
                return self.send(200, b"", headers={"Docker-Content-Digest": d})
            # like ghcr.io: the file itself comes from another server (here: the next port)
            host = (self.headers.get("Host") or "127.0.0.1:%d" % port).rsplit(":", 1)[0]
            return self.send(307, b"", headers={"Location": "https://%s:%d/storage/%s?sig=test" % (host, port + 1, d)})
        if "/manifests/" in p:
            tag = p.rsplit("/", 1)[1]
            if self.command == "PUT":
                data = self.body()
                m = json.loads(data)
                for ref in [m["config"]] + m["layers"]:
                    if ref["digest"] not in blobs:
                        return self.send(400, b'{"errors":[{"code":"MANIFEST_BLOB_UNKNOWN"}]}')
                with lock:
                    manifests[tag] = data
                return self.send(201, b"")
            if tag not in manifests:
                return self.send(404, b'{"errors":[{"code":"MANIFEST_UNKNOWN"}]}')
            return self.send(200, manifests[tag], "application/vnd.oci.image.manifest.v1+json")
        return self.send(404, b"")


port, cert, key = int(sys.argv[1]), sys.argv[2], sys.argv[3]
ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
ctx.load_cert_chain(cert, key)
servers = []
for p in (port, port + 1):  # registry, storage
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", p), H)
    srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
    servers.append(srv)
threading.Thread(target=servers[1].serve_forever, daemon=True).start()
servers[0].serve_forever()
