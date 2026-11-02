# Incognito security checks (external)

These tools live **outside** the app binary. They do not add CLI flags to FromChat.

Use them while the desktop app is running with **Incognito mode** enabled.

## 1. No plaintext writes under app data

While incognito is on, session data must go through the encrypted container (under the system temp directory), not into the normal app data tree.

**Linux** (requires `inotify-tools`):

```bash
# Terminal A — fail on any create/modify under dev app data (adjust path for your install)
./scripts/incognito-security/watch-app-data-writes.sh /workspace/build/dev-data/beta

# Terminal B — run the app, enable incognito, sign in, send messages, quit
./gradlew :app:desktop:run
```

Default app data roots when not using `-Dfromchat.dev.data.dir`:

- Linux: `~/.local/share/FromChat Beta` (or `FromChat`)
- macOS: `~/Library/Application Support/FromChat Beta`
- Windows: use `watch-app-data-writes.ps1` (see below)

The watcher exits with code **1** and prints the path if anything is written under the watched tree.

Optional: after incognito is active, note the random session directory under `/tmp` (or `%TEMP%`) and run:

```bash
./scripts/incognito-security/watch-temp-session-writes.sh /tmp/your-session-dir /path/to/container-file
```

Only that session directory and the container file may receive writes; any other file created inside the session dir is reported.

## 2. MITM certificate refused

Simulates a hostile TLS endpoint with a **Charles Proxy–like** certificate name. With incognito on, the app must **not** connect silently; you should see the MITM warning (or TLS blocked for official hosts).

```bash
python3 ./scripts/incognito-security/mitm_probe_server.py
```

1. In the app, set the server to `127.0.0.1` and the printed port, **HTTPS** on.
2. Enable **Incognito**, enter a username, tap Next.
3. Expect **Suspicious certificate** / blocked connection — not a normal successful probe.

Stop the server with Ctrl+C.

## Notes

- These checks are manual QA helpers, not CI gates wired into Gradle.
- Android/iOS use different storage roots; adapt the watch path or use platform file tools.
