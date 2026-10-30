---
name: run-windows-prl
description: "Build, sync, and install the FromChat Windows desktop app inside a Parallels Windows VM from macOS. Use for local Windows installer iteration without CI."
---

# run-windows-prl

**Gate:** macOS with Parallels only. If the host is not `darwin`, tell the user this skill is Mac-only and stop — do not run the workflow.

Build and package the Windows desktop app inside the Parallels VM. Artifacts stay under Gradle output paths in the VM (`C:\FromChat\app\...`); nothing is copied to the Mac Desktop.

## Prerequisites

- Parallels VM named `Windows 11` (override with `FROMCHAT_WINDOWS_VM`)
- Mac workspace shared into the VM at `C:\Mac\Home\Desktop\FromChat\app` (see `scripts/sync-windows-vm.cmd`)
- Local VM copy at `C:\FromChat\app`

## Sync from Mac (host)

From the app repo root on macOS:

```bash
./scripts/sync-to-windows-vm.sh [all|rust|scripts|gradle]
```

This runs `prlctl exec` → `C:\FromChat\app\scripts\sync-windows-vm.cmd`.

## Build inside the VM

Run via `prlctl exec "$VM_NAME" cmd /c "..."` or an interactive VM session. Entry script: `C:\FromChat\app\scripts\build-windows-vm.cmd`.

| Target | What it does |
|--------|----------------|
| `sync [all\|rust\|scripts\|gradle]` | Robocopy from Mac share → `C:\FromChat\app` |
| `rust` | Sync `windows-setup`, `cargo build --release --workspace` |
| `beta` | Sync all, `:app:desktop:packageBetaWindows -PbetaDesktop` (debug app + beta setup, no ProGuard) |
| `install` | `beta` then `run-beta-installer.cmd` (upgrade install + launch) |
| `pack` | Sync rust scope, `:app:desktop:packSetupOnly` (fast UI loop) |
| `gradle` | Sync gradle scope, `:app:desktop:packageReleaseWindows` |

Before ARM64 beta builds that bundle x64, stage prebuilt x64 app-image if needed:

```cmd
scripts\stage-windows-x64-prebuilt.cmd <path-to-x64-app-image>
```

JDK on VM: `scripts\ensure-windows-arm64-jdk.cmd` (also used by CI).

## Typical loop

1. Edit on Mac under `Desktop/FromChat/app`
2. `./scripts/sync-to-windows-vm.sh gradle` (or `all` for full tree)
3. In VM: `C:\FromChat\app\scripts\build-windows-vm.cmd beta` or `install` to test

Installers and app images appear under `app\desktop\build\compose\binaries\` in the VM workspace.
