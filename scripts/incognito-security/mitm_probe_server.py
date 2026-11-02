#!/usr/bin/env python3
"""Local HTTPS server with a Charles Proxy-like certificate for manual incognito MITM QA."""

from __future__ import annotations

import json
import ssl
import subprocess
import sys
import tempfile
from http.server import BaseHTTPRequestHandler, HTTPServer


def _openssl(*args: str) -> None:
    subprocess.run(["openssl", *args], check=True)


def generate_mitm_material(work_dir: str) -> tuple[str, str]:
    key = f"{work_dir}/mitm.key.pem"
    cert = f"{work_dir}/mitm.cert.pem"
    subj = "/CN=Charles Proxy SSL Proxying Certificate/O=Charles/C=US"
    _openssl(
        "req",
        "-x509",
        "-newkey",
        "rsa:2048",
        "-nodes",
        "-keyout",
        key,
        "-out",
        cert,
        "-days",
        "1",
        "-subj",
        subj,
    )
    return cert, key


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path.split("?", 1)[0] != "/instance_id":
            self.send_response(404)
            self.end_headers()
            return
        body = json.dumps({"instance_id": "00000000-0000-4000-8000-000000000001"}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args: object) -> None:
        sys.stderr.write(f"{self.address_string()} - {fmt % args}\n")


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="fromchat-mitm-probe-") as work_dir:
        cert, key = generate_mitm_material(work_dir)
        httpd = HTTPServer(("127.0.0.1", 0), Handler)
        port = httpd.server_address[1]
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(certfile=cert, keyfile=key)
        httpd.socket = context.wrap_socket(httpd.socket, server_side=True)
        print(f"MITM probe listening on https://127.0.0.1:{port}/instance_id", flush=True)
        print("Set server to 127.0.0.1, this port, HTTPS. Enable incognito and probe login.", flush=True)
        print("Press Ctrl+C to stop.", flush=True)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            return 0


if __name__ == "__main__":
    raise SystemExit(main())
