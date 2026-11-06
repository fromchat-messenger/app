---
name: run-macos-app
description: "Build and install the FromChat macOS desktop app locally (native, not Parallels). Use after UI or desktop changes when you need to verify the release distributable on macOS."
---

# run-macos-app

**Gate:** macOS only (native host, not Parallels). If the host is not `darwin`, tell the user this skill is Mac-only and stop — do not run the workflow.

From the **app repo root** (where `./gradlew` lives), run this **one command** (debug distributable — no ProGuard; faster for local UI checks):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && \
./gradlew :app:desktop:createDistributable && \
(osascript -e 'tell application "FromChat" to quit' 2>/dev/null || true) && \
sleep 2 && \
ditto "app/desktop/build/compose/binaries/main/app/FromChat.app" /Applications/FromChat.app && \
open -a FromChat
```

- **Bundle path** (for manual copy or troubleshooting): `app/desktop/build/compose/binaries/main/app/FromChat.app`
- The `osascript` line is best-effort if FromChat was not running; the chain still continues.
