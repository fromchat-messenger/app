---
name: run-windows-prl
description: "Build, sync, and install the FromChat Windows desktop app inside a Parallels Windows VM from macOS. Use for local Windows installer iteration without CI."
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

## Typical loop

1. Edit on Mac under `Desktop/FromChat/app`
2. `./scripts/sync-to-windows-vm.sh gradle` (or `all` — both purge stale Kotlin sources automatically)
3. `prlctl exec "$VM_NAME" cmd /c "C:\FromChat\app\scripts\build-windows-vm.cmd beta"` (or `gradle` for release)
4. Pick up the installer on the **Windows Desktop** in the VM (or run step 3 again — copy runs at end of beta/gradle)

App images remain under `app\desktop\build\compose\binaries\` in the VM workspace.

## Agent workflow

When driving the VM from macOS:

1. `./scripts/sync-to-windows-vm.sh all` (or rely on `build-windows-vm.cmd beta` / `gradle`, which sync first)
2. `prlctl exec "$VM_NAME" cmd /c "C:\FromChat\app\scripts\build-windows-vm.cmd beta"` (or `gradle` for release)
3. If copy was skipped: `./scripts/copy-installer-to-mac-desktop.sh` — confirm `Copied installer to Windows Desktop: …`
4. On **Kotlin compile failure** with paths missing on the Mac tree: run `purge-sources`, sync again, retry build once
5. Do **not** hand-delete random VM folders except via `purge-sources` — avoids wiping `build/` caches
