---
name: run-macos-app
description: "Build and install the FromChat macOS desktop app locally (native, not Parallels). Use after UI or desktop changes when you need to verify the release distributable on macOS."
---

# run-macos-app

**Gate:** macOS only (native host, not Parallels). If the host is not `darwin`, tell the user this skill is Mac-only and stop — do not run the workflow.

From the app repo root, after UI or desktop changes:

1. Set `JAVA_HOME` to Android Studio JBR:

   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ```

2. Build the release distributable:

   ```bash
   ./gradlew :app:desktop:createReleaseDistributable
   ```

3. Bundle path: `app/desktop/build/compose/binaries/main-release/app/FromChat.app`

4. Quit any running instance:

   ```bash
   osascript -e 'tell application "FromChat" to quit'
   sleep 2
   ```

5. Install to Applications:

   ```bash
   ditto "app/desktop/build/compose/binaries/main-release/app/FromChat.app" /Applications/FromChat.app
   ```

6. Launch:

   ```bash
   open -a FromChat
   ```
