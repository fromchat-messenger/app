# Incognito security checks (external)

These tools live **outside** the app binary. They do not add CLI flags to FromChat.

Use them while the desktop app is running with **Incognito mode** enabled.

## 1. No plaintext writes under app data

**Linux** (requires `inotify-tools`):

```bash
./scripts/incognito-security/watch-app-data-writes.sh /workspace/build/dev-data/beta
./gradlew :app:desktop:run -Dfromchat.dev.data.dir=/workspace/build/dev-data/beta
```

The watcher exits with code **1** if anything is written under the watched tree.

## 2. Hostile network (MITM) against api.fromchat.ru

Simulates interception of **all** TLS to the official API using a Charles-like certificate. The app keeps the default server (`api.fromchat.ru`); do **not** point the client at a custom host.

```bash
sudo ./scripts/incognito-security/enable-hostile-network.sh
```

This adds `127.0.0.1 api.fromchat.ru` to `/etc/hosts` and runs a local TLS proxy on port 443.

1. Enable **Incognito** in the app (default server, HTTPS).
2. Sign in or tap **Next** on the username step — expect the in-app **Suspicious certificate** or **Cannot connect safely** Material dialog with **Connect anyway**.
3. When finished:

```bash
sudo ./scripts/incognito-security/disable-hostile-network.sh
```

Optional: `./scripts/verify-mitm-probe-cert.sh` checks the Charles-like probe cert subject used by the local proxy scripts.

## Notes

- Manual QA helpers only, not CI gates.
- Android/iOS need different storage roots for the file watcher.
