---
name: run-windows-prl
description: "Build, sync, and install the FromChat Windows desktop app inside a Parallels Windows VM from macOS. Use for local Windows installer iteration without CI. When using prlctl exec from the host, pass ORG_GRADLE_PROJECT_gitCommitSha and gitHubRepository from the Mac git clone."
---

# run-windows-prl

**Gate:** macOS with Parallels only. If the host is not `darwin`, tell the user this skill is Mac-only and stop — do not run the workflow.

Build and package the Windows desktop app inside the Parallels VM. Gradle outputs stay under `C:\FromChat\app\...`; after **beta** / **gradle**, the newest **`FromChat-Setup-&lt;versionName&gt;.exe`** is copied to the **Windows user Desktop** (`C:\Users\…\Desktop`).

Beta installer file name uses root **`versionName`** (e.g. `1.2.0-beta20` → `FromChat-Setup-1.2.0-beta20.exe`). There is no extra `-beta2` suffix.

## Prerequisites

- Parallels VM named `Windows 11` (override with `FROMCHAT_WINDOWS_VM`)
- Mac workspace shared into the VM at `C:\Mac\Home\Desktop\FromChat\app` (see `scripts/sync-windows-vm.cmd`)
- Local VM copy at `C:\FromChat\app`

## Stale sources (important)

Sync uses Robocopy **`/E`**, not `/MIR`, so **`build/`, `.gradle/`, and `target/` are preserved** but **files you delete or move on the Mac are not removed** under `C:\FromChat\app`. Leftover `.kt` paths (old packages, renamed modules) cause bogus compile errors (`Conflicting overloads`, `Unresolved reference`, duplicate `expect`).

**`all` and `gradle` sync scopes** call **`purge-sources` first**: delete `app\shared\src`, `app\desktop\src`, and `app\android\src` in the VM, then robocopy from the share. Gradle caches and `build/` outputs are untouched.

Manual purge only (no robocopy):

```bash
./scripts/sync-to-windows-vm.sh purge-sources
```

Or inside the VM: `C:\FromChat\app\scripts\sync-windows-vm.cmd purge-sources`

If compile errors still mention paths that do not exist on the Mac, purge again and re-sync before rebuilding.

## Sync from Mac (host)

From the app repo root on macOS:

```bash
./scripts/sync-to-windows-vm.sh [all|rust|scripts|gradle|purge-sources]
```

This runs `prlctl exec` → `C:\FromChat\app\scripts\sync-windows-vm.cmd`.

| Scope | Purges Kotlin `src` | Robocopy |
|-------|---------------------|----------|
| `all` (default) | yes | full app tree |
| `gradle` | yes | full app tree |
| `rust` | no | `app\desktop\windows-setup` only |
| `scripts` | no | `scripts\` only |
| `purge-sources` | yes | no |

## Build inside the VM

Run via `prlctl exec "$VM_NAME" cmd /c "..."` or an interactive VM session. Entry script: `C:\FromChat\app\scripts\build-windows-vm.cmd`.

| Target | What it does |
|--------|----------------|
| `sync [all\|rust\|scripts\|gradle\|purge-sources]` | Robocopy and/or purge (see table above) |
| `rust` | Sync `windows-setup`, `cargo build --release --workspace` |
| `beta` | Sync `all` (includes purge), `:app:desktop:packageBetaWindows -PbetaDesktop`, **copy setup EXE to Windows Desktop** |
| `install` | `beta` then `run-beta-installer.cmd` (upgrade install + launch from Desktop or dist) |
| `pack` | Sync rust scope, `:app:desktop:packSetupOnly` (fast UI loop; no Desktop copy) |
| `gradle` | Sync gradle scope (includes purge), `:app:desktop:packageReleaseWindows`, **copy setup EXE to Windows Desktop** |

Copy newest `FromChat-Setup-*.exe` from `app\desktop\build\distributions\release` → logged-in user’s **Windows Desktop**:

```bash
./scripts/copy-installer-to-mac-desktop.sh
```

(Wrapper that runs `scripts\copy-installers-to-desktop.cmd` in the VM.) Or inside the VM:

```cmd
C:\FromChat\app\scripts\copy-installers-to-desktop.cmd
```

Before ARM64 beta builds that bundle x64, stage prebuilt x64 app-image if needed:

```cmd
scripts\stage-windows-x64-prebuilt.cmd <path-to-x64-app-image>
```

JDK on VM: `scripts\ensure-windows-arm64-jdk.cmd` (also used by CI).

## Git metadata when using `prlctl exec` (important)

Root `build.gradle.kts` resolves `gitCommitSha` / `gitHubRepository` from Gradle properties, then `GITHUB_*` env, then **`git`** in the project dir. Robocopy **excludes `.git`**, so the VM tree has no repo for a local fallback anyway.

`prlctl exec … cmd /c …` runs as the **Windows system profile**. **`git` is usually not on PATH** there, so Gradle fails at configuration with `A problem occurred starting process 'command 'git''`. An **interactive VM login** may work if Git for Windows is on the user PATH — do not rely on that for agent-driven builds.

From macOS, inject the same env CI uses (read values from the Mac clone) **on the same `cmd /c` line** before `build-windows-vm.cmd`:

```bash
VM_NAME="${FROMCHAT_WINDOWS_VM:-Windows 11}"
ROOT="$(git -C /path/to/app rev-parse --show-toplevel)"
SHA="$(git -C "$ROOT" rev-parse HEAD)"
REPO="$(git -C "$ROOT" remote get-url origin | sed -E 's#.*github.com[:/]([^/]+)/([^/.]+).*#\1/\2#' | sed 's/\.git$//')"
prlctl exec "$VM_NAME" cmd /c "set ORG_GRADLE_PROJECT_gitCommitSha=${SHA}&& set ORG_GRADLE_PROJECT_gitHubRepository=${REPO}&& C:\FromChat\app\scripts\build-windows-vm.cmd beta"
```

Use `beta` or `gradle` as the last argument. Applies to any target that runs `gradlew.bat` (`beta`, `gradle`, `pack`, `install`).

## Typical loop

1. Edit on Mac under `Desktop/FromChat/app`
2. From app repo root, run **`build-windows-vm.cmd beta`** or **`gradle`** via `prlctl exec` with **git env vars** (see **Git metadata**). Both targets sync (with purge) before Gradle.
3. Pick up **`FromChat-Setup-<versionName>.exe`** on the **Windows Desktop** in the VM.

For **installer only** (no upgrade install / launch), use **`beta`** or **`gradle`**, not **`install`**.

App images remain under `app\desktop\build\compose\binaries\` in the VM workspace.

## Agent workflow

When driving the VM from macOS:

1. Confirm VM is running (`prlctl list -a`); default name `Windows 11`, override with `FROMCHAT_WINDOWS_VM`.
2. Run **`build-windows-vm.cmd beta`** or **`gradle`** via `prlctl exec`, **always** prefixing `ORG_GRADLE_PROJECT_gitCommitSha` and `ORG_GRADLE_PROJECT_gitHubRepository` from the Mac repo (see **Git metadata**). Sync is included in those targets — a separate `./scripts/sync-to-windows-vm.sh` is optional.
3. Confirm log line `Copied installer to Windows Desktop: …`. If copy was skipped: `./scripts/copy-installer-to-mac-desktop.sh`.
4. Default to **`beta`** when the user wants an installer on the Desktop without installing the app; use **`gradle`** only when they ask for release packaging.
5. On **Kotlin compile failure** with paths missing on the Mac tree: run `purge-sources`, sync again, retry build once (still with git env vars on `prlctl exec`).
6. Do **not** hand-delete random VM folders except via `purge-sources` — avoids wiping `build/` caches
