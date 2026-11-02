#!/usr/bin/env python3
"""Hostile-network QA: terminate TLS for api.fromchat.ru with a Charles-like cert and forward to the real API."""

from __future__ import annotations

import socket
import ssl
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

UPSTREAM_HOST = "api.fromchat.ru"
UPSTREAM_PORT = 443
LISTEN_HOST = "0.0.0.0"
LISTEN_PORT = 443


def _openssl(*args: str) -> None:
    subprocess.run(["openssl", *args], check=True)


def generate_mitm_material(work_dir: Path) -> tuple[Path, Path]:
    key = work_dir / "mitm.key.pem"
    cert = work_dir / "mitm.cert.pem"
    cnf = work_dir / "mitm.cnf"
    cnf.write_text(
        """
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no

[req_distinguished_name]
CN = Charles Proxy SSL Proxying Certificate
O = Charles
C = US

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = api.fromchat.ru
DNS.2 = localhost
""".strip()
        + "\n",
        encoding="utf-8",
    )
    _openssl(
        "req",
        "-x509",
        "-newkey",
        "rsa:2048",
        "-nodes",
        "-keyout",
        str(key),
        "-out",
        str(cert),
        "-days",
        "1",
        "-config",
        str(cnf),
        "-extensions",
        "v3_req",
    )
    return cert, key


def relay(a: ssl.SSLSocket, b: ssl.SSLSocket) -> None:
    sockets = [a, b]
    try:
        while True:
            readable, _, _ = socket.select(sockets, [], sockets, 60.0)
            if not readable:
                return
            for src in readable:
                dst = b if src is a else a
                data = src.recv(65536)
                if not data:
                    return
                dst.sendall(data)
    finally:
        for s in sockets:
            try:
                s.close()
            except OSError:
                pass


def handle_client(raw: socket.socket, cert: Path, key: Path) -> None:
    try:
        client_ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        client_ctx.load_cert_chain(certfile=str(cert), keyfile=str(key))
        client = client_ctx.wrap_socket(raw, server_side=True)
    except ssl.SSLError as error:
        raw.close()
        sys.stderr.write(f"client TLS handshake skipped: {error}\n")
        return
    upstream_raw = socket.create_connection((UPSTREAM_HOST, UPSTREAM_PORT), timeout=30)
    upstream_ctx = ssl.create_default_context()
    upstream_ctx.check_hostname = False
    upstream_ctx.verify_mode = ssl.CERT_NONE
    upstream = upstream_ctx.wrap_socket(
        upstream_raw,
        server_hostname=UPSTREAM_HOST,
    )
    relay(client, upstream)


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="fromchat-hostile-net-") as tmp:
        work = Path(tmp)
        cert, key = generate_mitm_material(work)
        bind = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        bind.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        bind.bind((LISTEN_HOST, LISTEN_PORT))
        bind.listen(128)
        print(
            f"Hostile TLS proxy on {LISTEN_HOST}:{LISTEN_PORT} -> {UPSTREAM_HOST}:{UPSTREAM_PORT}",
            flush=True,
        )
        print("Requires /etc/hosts: 127.0.0.1 api.fromchat.ru", flush=True)
        print("Press Ctrl+C to stop.", flush=True)
        try:
            while True:
                client, addr = bind.accept()
                thread = threading.Thread(
                    target=handle_client,
                    args=(client, cert, key),
                    daemon=True,
                )
                thread.start()
                print(f"accepted {addr[0]}:{addr[1]}", flush=True)
        except KeyboardInterrupt:
            return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PermissionError:
        print("Bind to port 443 requires root (sudo).", file=sys.stderr)
        raise SystemExit(1)
