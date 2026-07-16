# Exchat Agent Rules

This file contains project-scoped rules, constraints, and instructions for the agent working on Exchat.

---

## Android / KMP Rules

### Project context (quick)
- This is a **messaging app**.
- The Kotlin Multiplatform shared code lives under:
  - `app/shared/src/commonMain/kotlin/` (cross-platform logic + Compose UI)
  - `app/shared/src/androidMain/kotlin/` (Android-specific implementations)
  - `app/shared/src/iosMain/kotlin/` (iOS-specific implementations, when present)
- Android app module: `app/android/`
- Shared UI strings (Compose resources):
  - `app/shared/src/commonMain/composeResources/values/strings.xml`
  - `app/shared/src/commonMain/composeResources/values-ru/strings.xml`

### Build / validation (required before finishing)
- After changes, run this command (and fix all errors):
  - `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:shared:compileAndroidMain :app:shared:compileKotlinIosArm64`
- If it fails, investigate and try to fix the issue yourself (don’t stop at reporting the failure).

### After Android-affecting changes: build + run on device (Mobile MCP)
If you changed anything under `app:android`, `app:shared` (`commonMain` / `androidMain`), or `utils:shared` that affects Android:
- **Build** debug APK: `./gradlew :app:android:assembleDebug` (artifact: `app/android/build/outputs/apk/debug/android-debug.apk`).
- **Install + launch via Cursor Mobile MCP** (configured name **`Mobile MCP`** in `mcp.json`; in Agent MCP tool calls the server id is often **`user-Mobile MCP`**—use whatever id your session lists for `@mobilenext/mobile-mcp`). **Read each tool’s schema**, then:
  1. **`mobile_list_available_devices`** — pick the target **Android** `device` id(s) (prefer a **physical** device when validating UI; use an emulator when the task needs it or no phone is listed).
  2. **`mobile_install_app`** — `device`, `path` = **absolute** path to `android-debug.apk` under the repo (e.g. `<workspace>/app/android/build/outputs/apk/debug/android-debug.apk`).
  3. **`mobile_launch_app`** — `device`, `packageName` = **`ru.fromchat.beta`** (debug application id; not `ru.fromchat`).
- Smoke-test the affected flows after launch.
- If **`mobile_install_app`** fails on an **emulator** with insufficient storage, use **“Android emulator storage”** (below), then retry.
- Use raw **`adb install` / `am start`** only if Mobile MCP is unavailable after checking MCP status—say so in the reply.

### Calls (LiveKit / `Call*`, call UI, foreground call service, call audio/video)
Whenever the task touches **calls** (e.g. `CallMediaLayer`, `CallForegroundService`, LiveKit wiring, in-call UI, mic/camera/screen-share for calls):
- **Build** debug: `./gradlew :app:android:assembleDebug`
- **Install + launch with Mobile MCP** on **every Android target** you will use for validation (same three tools as above: **`mobile_list_available_devices`** → **`mobile_install_app`** (`device` + absolute `path` to `android-debug.apk`) → **`mobile_launch_app`** (`device`, `packageName` = **`ru.fromchat.beta`**)). Use the MCP **server id** your environment exposes (e.g. **`user-Mobile MCP`**). Repeat for **each** Android `device` id (emulator **and** phone when both appear in `mobile_list_available_devices`).
- Do **not** treat the task as finished until this has been run on **both** an **emulator** and a **physical device** whenever both are available (start/boot the missing one, list devices again, then install + launch on each). If install fails on the emulator, follow **“Android emulator storage”** below, then retry install + launch. If it still fails after a real cleanup attempt, report what you tried.
- If only one class of device is listed, deploy to every listed Android device and state clearly what was missing.

### Android emulator storage (no approval needed)
When an **Android emulator** hits **`INSTALL_FAILED_INSUFFICIENT_STORAGE`** or otherwise has too little free space for a debug install, you may **do whatever is needed to free space on that emulator only** without asking the user first: uninstall third-party apps (`adb -s <emu> uninstall …`), clear caches (`adb shell pm trim-caches` / `rm` under emulator-owned paths the shell can reach), delete arbitrary files **inside that AVD**, or **wipe the AVD / `emulator -wipe-data`** if that is the fastest fix. Treat the emulator as disposable dev state.
- **Never** use this “anything goes” approach on a **physical device** (only uninstall/clear what the user explicitly asked for, or use normal Mobile MCP flows).

### Never touch Gradle caches
- Do not read, list, search, copy, or modify anything under Gradle cache paths (e.g. `~/.gradle/caches`, `**/.gradle/caches`).

### No magic-string “sanitization” of real data
- Never strip/null/rewrite stored or displayed values by comparing to hard-coded UI/placeholder strings.
- Fix at the source (don’t persist placeholders) or use explicit migrations/sentinels—never natural-language matching.

### UI strings (shared module)
- No hardcoded user-visible copy in shared UI. Use Compose Multiplatform resources:
  - `app/shared/src/commonMain/composeResources/values/strings.xml` (default)
  - `app/shared/src/commonMain/composeResources/values-ru/strings.xml` (Russian)
  - Keep keys in sync across both files.
- Exception: Debug API screen (`ru.fromchat.ui.debug`) may hardcode strings.

### Prefer utils package APIs
Prefer `com.pr0gramm3r101.utils` when available (before custom solutions), especially:
- Clipboard: `supportClipboardManagerImpl` / `SupportClipboardManager.setText`
- Strings: `String.toAnnotatedString()`
- Compose: `Modifier.conditional`, `CompositionLocal.invoke()`

### Debug HTTP logging
- Prefer Ktor `HttpClient` for debug/instrumentation HTTP logging.
- Do not use `HttpURLConnection` or raw `OkHttpClient` for debug-only logging.
- Centralize behind `ru.fromchat.debug.DebugLogger`, best-effort only (never crash the app).

### Codebase hygiene
- Prefer **official docs** first for typical Jetpack Compose / Compose Multiplatform patterns; if docs cover it, follow them.
- Avoid breaking iOS via Android-only APIs in `commonMain` (keep platform code in `androidMain` / `iosMain` as needed).
- Do not introduce hardcoded user-visible strings outside the Debug API exception (see “UI strings” above).

### Skills (when to use)
- Use the **Material 3 skill** when implementing or changing Compose UI using Material3 components, theming, tokens, or accessibility.
- Use the **using-computer skill** only when the user asks me to control the computer / click through UI / take screenshots.
- Use **Cursor “create-rule/create-skill/create-hook” skills** only when you explicitly ask to create or modify Cursor rules/skills/hooks.

---

## Code Style Rules

Before writing or editing Kotlin / Compose code in this repo:

1. **Read** [CODE_STYLE.md](file:///Users/macbook/Documents/GitHub/exchat/CODE_STYLE.md) at the repository root.
2. **Follow** it — write idiomatic, well-structured code from the start; match neighboring files when a rule is ambiguous.
3. **Do not ask the user style questions** when implementing new code.

For style cleanup on an existing diff or file set, use the **`adapt-to-style`** skill.

---

## Tooling Constraints (Never use `rg`)

Do **not** run `rg` / ripgrep in terminal commands. It is unreliable in this environment.

### Use instead
- **Cursor Grep tool** — preferred for searching the codebase
- **`grep -r`** — if a shell search is truly needed
- **Glob / SemanticSearch** — for finding files or concepts

```bash
# ❌ BAD
rg "probeCurrentServer" app/shared

# ✅ GOOD — use the Grep tool, or:
grep -r "probeCurrentServer" app/shared
```
