# AGENTS.md — FromChat app repo

Instructions for AI agents working in this repository.

## 1. Repo map

- **Messaging app** — Kotlin Multiplatform + Compose Multiplatform.
- **`app/shared/`** — shared logic and UI
  - `src/commonMain/kotlin/` — cross-platform
  - `src/androidMain/kotlin/` — Android
  - `src/iosMain/kotlin/` — iOS (when present)
  - Strings: `src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`
- **`app/android/`** — Android application module
- **`app/desktop/`** — Desktop (JVM) application
- **`utils/`** — removed; shared helpers live under `app/shared/…/ru/fromchat/utils/`

## 2. Code style and search

Before writing or editing Kotlin / Compose:

1. **Read** [CODE_STYLE.md](./CODE_STYLE.md) at the repo root.
2. **Follow** it — match neighboring files when a rule is ambiguous.
3. **Do not ask the user style questions** when implementing new code.

For style cleanup on an existing diff or file set, use the **`adapt-to-style`** skill (`.agents/skills/adapt-to-style/`).

**Shell search:** do **not** run `rg` / ripgrep in terminal commands (unreliable in some environments). Prefer your editor’s search tools, **`grep -r`**, or **Glob**.

**Temporary scripts:** one-off automation (bulk renames, sed helpers, move scripts) must live under the **system temp directory** (e.g. `$TMPDIR`, `/tmp`, `%TEMP%`) — **never** under the repo (including `.cursor/`). Delete temp scripts when done.

## 3. Skills

Conventions:

- Folder name = YAML `name:` = kebab-case.
- **Mac-only skills:** first step — if host ≠ `darwin`, stop and say the skill is Mac-only.
- **`run-*-prl`:** macOS **and** Parallels (not native-only).

| Skill | Path | Use when |
|-------|------|----------|
| `adapt-to-style` | `.agents/skills/adapt-to-style/` | Style pass without behavior changes |
| `material-3-skill` | `.agents/skills/material-3-skill/` | Material 3 Compose UI (vendor submodule) |
| `pre-mortem` | `.agents/skills/pre-mortem/` | Stress-test plans (see skill triggers) |
| `run-linux-prl` | `.agents/skills/run-linux-prl/` | Build/debug Linux desktop in Parallels VM |
| `run-windows-prl` | `.agents/skills/run-windows-prl/` | Build Windows desktop in Parallels VM |
| `run-macos-app` | `.agents/skills/run-macos-app/` | Install/run native macOS `.app` after desktop UI changes |
| `release` | `.agents/skills/release/` | Ship via CI — follow the **`release`** skill |

Also: **Material 3 skill** for M3 components/theming. Add or change agent skills/hooks only when the user asks.

## 4. Build and validation

**Compile gate** (required after Kotlin/shared changes; fix failures yourself):

```bash
export JAVA_HOME="<JDK 21+; on macOS with Android Studio, often /Applications/Android Studio.app/Contents/jbr/Contents/Home>"
./gradlew :app:shared:compileAndroidMain :app:shared:compileKotlinIosArm64 :app:desktop:compileKotlin
```

Set `JAVA_HOME` to a JDK 21+ on your OS (the path above is a **macOS** example only).

**Calls** (LiveKit / `Call*`, call UI, foreground call service, in-call audio/video): after changes, build debug APK and run the **Android device verify** workflow (section 5) on **every** Android target you use — emulator **and** physical device when both are available; for call work, at least **two** devices. Do not treat the task as finished until deploy succeeds on each chosen device.

**Android emulator storage** (emulators only, no approval needed): on `INSTALL_FAILED_INSUFFICIENT_STORAGE`, free space on that emulator (uninstall apps, trim caches, wipe AVD if needed). **Never** use this approach on a **physical device**.

**Android emulator GPU:** do **not** launch with `-gpu swiftshader*` / software Vulkan stacks. Use AVD defaults (`hw.gpu.mode=auto`) or `-gpu host` when you must pass `-gpu`. SwiftShader wastes CPU/RAM and is not part of device verify.

**Never touch Gradle caches** — do not read/list/search/copy/modify `~/.gradle/caches` or `**/.gradle/caches`.

**No magic-string “sanitization”** — never strip/null/rewrite stored or displayed values by comparing to hard-coded UI/placeholder strings; fix at the source or use explicit migrations/sentinels.

**UI strings:** no hardcoded user-visible copy in shared UI (Compose resources; keep EN/RU keys in sync). Exception: `ru.fromchat.ui.debug`.

**Prefer** `ru.fromchat.utils` (clipboard, `Modifier.conditional`, …) when available.

**Debug HTTP logging:** Ktor `HttpClient` via `ru.fromchat.debug.DebugLogger` (best-effort; never crash). No raw `HttpURLConnection` / `OkHttpClient` for debug-only logging.

**Hygiene:** official Compose/CM docs first; no Android-only APIs in `commonMain`; keep platform code in `androidMain` / `iosMain`.

## 5. Android device verify

**When:** after Android-affecting work (`app:android`, `app:shared` common/androidMain), or when the user asks to verify on devices.

**Rules:**

- Do **not** spawn subagents for this flow.
- If install fails because the APK is missing, find the correct output path and retry.
- Investigate failures yourself; do not ask the user to pick devices unless they named one.

**Step 1 (parallel):**

- From the **app repo root**, run `./gradlew :app:android:assembleDebug` once (no extra flags like `--no-daemon`). Use a JDK 21+ (`JAVA_HOME` as in section 4 — **macOS** Android Studio JBR path is only an example).
- List connected Android devices (Mobile MCP `mobile_list_available_devices`, or `adb devices`).

**Step 2 — devices:** prefer **physical** Android devices if any are listed; otherwise emulators. **Calls** work → at least **two** devices from the list.

**Step 3 (one parallel batch only):** for each chosen device, install and launch `ru.fromchat.beta`:

- Install: debug APK at `app/android/build/outputs/apk/debug/android-debug.apk` (use an **absolute** path; layout is the same relative to repo root on all OSes).
- Launch: package `ru.fromchat.beta`.

When using Mobile MCP, run **all** `mobile_install_app` and `mobile_launch_app` calls in **one** batch. With `adb`: install then `am start` per device.

**Done:** report completion only after **every** chosen device succeeds; retry on failure.

## 6. Scripts and release

- **Canonical release:** [`.github/workflows/release.yml`](./.github/workflows/release.yml) (`workflow_dispatch`, `version` input). Use the **`release`** skill — fork-safe `gh workflow run` on `origin`.
- **Preflight (mandatory):** do **not** dispatch CI until the **`release`** skill checks pass: local compile gate (section 4), version bump in root [`build.gradle.kts`](./build.gradle.kts) vs latest tag, and release tag on the correct commit. If a check fails, fix what you can without behavior changes; otherwise stop and ask the user.
- **Kept VM/CI scripts:** see `scripts/` (Windows VM chain, `ensure-windows-arm64-jdk.cmd`, etc.) — used by `run-*-prl` skills and CI.
