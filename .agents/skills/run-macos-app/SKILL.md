---
name: run-macos-app
description: "Build and install the FromChat macOS desktop app locally (native, not Parallels). Use after UI or desktop changes when you need to verify the release distributable on macOS."
---

# run-macos-app

**Gate:** macOS only (native host, not Parallels). If the host is not `darwin`, tell the user this skill is Mac-only and stop — do not run the workflow.

## Mode (user attached this skill)

When this skill is active as the turn **mode**, you **must** end every turn by running the install chain below (after any code changes in that turn), unless the user explicitly says not to install/run. Fix build failures and retry; if the disk is full, free safe space under the repo (e.g. `app/desktop/build/compose`) and retry — never skip the install without telling the user why.

## Workflow

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
