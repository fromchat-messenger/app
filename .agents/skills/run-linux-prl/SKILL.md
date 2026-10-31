---
name: run-linux-prl
description: "Build, install and debug the FromChat Linux desktop app inside a Parallels VM, without CI. Use when asked to 'build in the VM', 'install the deb', 'debug the linux app', 'test on linux arm64', 'run it locally to go faster', or when iterating on Linux tray/scaling/desktop-integration bugs that CI cannot verify. Covers prlctl exec quirks, the ProGuard OOM workaround, macOS-to-VM source transfer, and reading app diagnostics out of the VM."
---

# run-linux-prl

**Gate:** macOS with Parallels only. If the host is not `darwin`, tell the user this skill is Mac-only and stop — do not run the workflow.

Building a Linux desktop release through CI takes ~20+ minutes per iteration. This
skill builds and installs a real, installable `.deb` inside the Ubuntu VM in a few
minutes, so changes can be verified by launching the app and reading its own logs.

Use this **for verification only**. Release artifacts still come from CI.

## Why this exists

CI is the only place a full release gets built, but a 20-minute round trip makes
iterating on desktop-integration bugs (tray, HiDPI scaling, AWT behaviour)
impractical — those bugs are invisible to unit tests and only reproduce on a real
desktop session.

---

## Non-obvious constraints (read before starting)

### `prlctl exec` mangles `bash -lc` and `sh -c`

This is the single biggest time sink. The command string gets word-split, so:

- `cd <dir> && cmd` — the `cd` silently does not happen; you end up in `/`.
- `HOME` comes back **empty** in a login shell; `~` does not expand. Use absolute
  paths, e.g. `/home/<user>/.config/monitors.xml`.
- `grep -i <pattern> <file>` can lose its pattern argument entirely and print
  `Usage: grep [OPTION]... PATTERNS [FILE]...`.

**Rule: invoke commands directly, never through a shell string.**

```sh
# GOOD — works
prlctl exec "$VM" --current-user ls -la /home/parallels/.config

# BAD — silently broken
prlctl exec "$VM" --current-user bash -lc 'cd /home/parallels/src && ./gradlew ...'
```

`prlctl exec` has **no working-directory flag**. For anything multi-step, write a
script file, serve it over HTTP, and execute the script as a *single* argument:

```sh
prlctl exec "$VM" --current-user /home/parallels/tools/build.sh
```

### The VM may have no JDK, no Gradle, and no sudo

Check before assuming. Install a JDK into `$HOME` (needs no sudo). The release
build requires a JDK that ships `jmods/` — Adoptium/Temurin **25 does not**, Zulu
does. When picking a Zulu URL, filter out `musl` and `crac` builds or you will
install a JDK that cannot run on Ubuntu:

```
zulu25.36.205-ca-fx-jdk25.0.4.1-linux_aarch64.tar.gz   # glibc, correct
zulu25.36.205-ca-crac-jdk25.0.4.1-linux_musl_aarch64   # musl, wrong
```

Verify before building: `java -version`, `ls $JAVA_HOME/jmods | wc -l` (expect
~78), and `ls $JAVA_HOME/bin/jpackage`.

### macOS `tar` injects AppleDouble files that break the build

Tar'ing the repo from macOS produces `._<name>` sidecars. SQLDelight then parses
one as SQL and the build dies with a nonsense syntax error:

```
._MessageDatabase.sq:1:0 <stmt identifier clojure real> expected
```

Use `COPYFILE_DISABLE=1` and exclude them anyway.

### ProGuard: use the dev task, never a property

**Use `:app:desktop:packageDevLinuxDeb`.** It produces the same jpackage deb
without ProGuard, stamps the tray dependency, and clears the stale jpackage arg
file first.

Do **not** try to switch ProGuard off with a Gradle property. `ProguardSettings`
has no `enabled` property — `buildTypes.release.proguard { enabled = ... }` does
not compile (`Unresolved reference 'enabled'`). ProGuard is configured only on the
release build type; the debug build type has no ProGuard at all, which is exactly
why the dev task exists.

| Task | Use |
|---|---|
| `packageDevLinuxDeb` | local dev loop — **use this** |
| `packageReleaseDeb` | release artifact; ProGuard, slow, OOMs on small VMs |
| `packageReleaseLinux` | also builds rpm, which fails without `rpmbuild` |

Why it matters: a 3 GB VM gives ProGuard's forked JVM a ~768 MB default heap and
it dies with `OutOfMemoryError`. `AbstractProguardTask` exposes a `maxHeapSize`
property, but **setting it reflectively does not reach the forked command** —
a documented dead end.

### Do NOT delete `build/compose/tmp` to fix a bundling error

This looks like the obvious fix and is a trap. `compose/tmp` holds the
bundler's **inputs** (`packageDeb/libs`, the runtime image, resources) *and* the
generated jpackage args file. Deleting it removes those inputs, Gradle then
considers them up-to-date and never repopulates them, and jpackage fails with:

```
Bundler DEB Bundle skipped because of a configuration problem: The configured
main jar does not exist desktop-<hash>.jar in the input directory
```

That message reads exactly like stale state, so it invites the wrong fix. Note the
symptom is identical whether the args file is stale or its input directory was
removed — check `ls app/desktop/build/compose/tmp/packageDeb/libs` before touching
anything.

The correct fix for a genuinely dirty tree is a clean build directory
(`rm -rf app/desktop/build`), which regenerates both the inputs and the args file
together. Note that a `doFirst { delete ... }` on the wrapper task is also wrong:
task dependencies run first, so it would execute only after `packageDeb` had
already failed.

### Debug builds differ from release builds

A locally built deb is **not** a release artifact:

- un-obfuscated, with verbose logging
- named `FromChat-dev-linux-<arch>.deb` so it is not mistaken for a release
- `Version:` is `1.2.0` (jpackage drops the `-beta` suffix), so `dpkg -l` cannot
  tell you which build is installed. Verify by checking for a new class/marker
  inside the jars instead.

---

## Transferring source to the VM

`prlctl` has no file-copy command. Serve over HTTP from the host and pull:

```sh
# host
cd /path/to/repo && COPYFILE_DISABLE=1 tar -czf /tmp/src.tar.gz \
  --exclude='.git' --exclude='build' --exclude='.gradle' --exclude='.kotlin' \
  --exclude='.idea' --exclude='dmg-background' --exclude='._*' .
cd /tmp && python3 -m http.server 18080 --bind 0.0.0.0 &

# guest
prlctl exec "$VM" --current-user wget -q -O /home/parallels/tools/src.tar.gz \
  http://10.211.55.2:18080/src.tar.gz
```

The guest reaches the host at the Parallels gateway (`10.211.55.2` on a
`10.211.55.0/24` guest network). Get the address with `ip -4 addr show`.

Excluding `build` matters — a naive tar is ~120 MB; excluding nested `build`
dirs brings it to ~30 MB.

---

## Build script

Always stream logs (`| tee`) rather than redirecting to a file and grepping
afterwards. A `set -e` plus redirected output once failed with **no output at
all**, hiding the actual error.

```sh
#!/bin/sh
D=/home/parallels/tools
SRC=/home/parallels/src
J=/home/parallels/tools/zulu25.36.205-ca-fx-jdk25.0.4.1-linux_aarch64

rm -rf "$SRC"; mkdir -p "$SRC" || exit 90
tar -xzf "$D/src.tar.gz" -C "$SRC" 2>/dev/null
find "$SRC" -name '._*' -delete 2>/dev/null

cd "$SRC" || exit 92
export JAVA_HOME="$J"
export PATH="$J/bin:/usr/bin:/bin"
export GRADLE_USER_HOME=/home/parallels/.gradle
./gradlew :app:desktop:packageDevLinuxDeb --no-daemon --console=plain \
    -PversionName=1.2.0-beta17 2>&1 | tee /home/parallels/build.log
```

`tee` (not `>` and **never** a `| grep`) — it keeps the log *and* streams it live.
Piping build output through `grep`/`tail` is the same class of mistake as
redirecting it to a file: Gradle buffers when not attached to a TTY, so filtering
the output kills progress reporting and hides exactly the lines you needed. When a
build is slow, you want to watch it, not read about it afterwards.

The deb lands in the project's `desktopDistDir` as
`FromChat-dev-linux-<arch>.deb`; the task logs the absolute path.

---

## The fast iteration loop (host → deb → running app)

After the first full setup, iterating should take ~2 minutes per cycle. The battle-tested
loop, in this exact order:

1. **Sanity-compile on the Mac first** (~15 s): `./gradlew :app:shared:compileKotlinJvm :app:desktop:compileKotlin`.
   Do not upload to the VM until this is green.
2. **Tar and serve once**: `COPYFILE_DISABLE=1 tar -czf /tmp/src.tar.gz …` from the host.
   Keep a detached `python3 -m http.server 18080/18081` running on the host; the guest
   `wget`s from `10.211.55.2` (Parallels gateway).
3. **Build in the VM with ONE job at a time.** Repeated MCP `exec_start` calls spawn
   concurrent builds that race and wipe each other's output (`builddeb-inner.sh` deletes the
   src dir at start, so the deb vanishes mid-cycle). Before starting a build, verify no
   stale `builddeb`/`GradleDaemon` processes remain; if one is root-owned, kill it
   **elevated** (`sudo -S` with the user's password) — plain `pkill` as `parallels`
   silently fails and leaves a zombie daemon that breaks the next run.
4. **Completion signal is TWO conditions**: `grep "Dev deb" builddeb.log` **and** zero
   `builddeb-inner` processes. The log line alone can be a leftover from the previous cycle,
   and the deb file existing is not a signal at all (a fresh build wipes the output dir
   first). Don't trust `ps -ef | grep -c` counts — the grep matches itself; the clean
   pattern is running the check inside a script file.
5. **Install + relaunch via prepared script files** in `/home/parallels/tmp/`
   (`install2.sh` = dpkg -i + relaunch with GUI env + env verification;
   `final-check.sh` = window identity via `wmctrl -lx` + `/proc/<pid>/environ` env grep +
   `.desktop` Exec line + jar-payload check). Write them on the host, serve over HTTP, run
   in one `prlctl exec` call. Never inline this logic into the exec string — it will be
   word-split and mangled.
6. **Verify the payload, not the install**: `dpkg` reports success for *any* deb. Confirm
   the change you just built actually shipped, e.g.
   `unzip -l /opt/fromchat/lib/app/shared-*.jar | grep <NewClassMarker>` or
   `unzip -l /opt/fromchat/lib/app/desktop-*.jar | grep GraphicsApi_jvmKt.class`, plus
   `tr '\0' '\n' < /proc/$(pgrep -x FromChat)/environ | grep -E 'MESA|vblank'` for runtime
   env. Identity check: `wmctrl -lx` should show `FromChat.FromChat — FromChat Beta`.

Runtime-environment fixes are **baked into the app itself**, not applied around it:

- The skiko/virgl workaround is a **build-time patch**:
  `app/desktop/skiko-patch/org/jetbrains/skiko/GraphicsApi_jvmKt.java` (always-true
  `isVideoCardSupported`) is compiled by a Linux-only `compileSkikoVirglPatch` Gradle task
  into `desktop.jar` (first on the jpackage app classpath → shadows skiko-awt's copy).
  Opt-out: `-PskikoVirglWorkaround=false`. This survives every reinstall — there is no
  runtime repatch script to re-run after `dpkg -i`.
- `MESA_GL_VERSION_OVERRIDE=3.0` (+ experimentally `vblank_mode=3`) is baked by
  `mesaGlVersionOverridePrefix()` (`app/desktop/.../Main.kt`, ARM64 Linux only) into the
  app-generated `.desktop` files, so `Exec=env MESA_GL_VERSION_OVERRIDE=3.0
  /opt/fromchat/bin/FromChat`. No `/usr/local/bin` wrapper. When relaunching by hand,
  replicate that env via the launcher script (`launch2.sh`).
- Recurring gotcha: `XAUTHORITY` filename (`.mutter-Xwaylandauth.*`) changes between
  sessions — always re-derive with a glob, never hardcode.
- If the Gradle daemon dies mid-build (`Gradle build daemon disappeared`) — usually
  collateral from killing stale processes — just rerun; with warm caches a dev deb
  takes ~1.5 min.

Log path note: the dev app's directory literally contains a wildcard
(`~/.local/share/FromChat*/fromchat/logs/current.log`) — glob it, and quote it
(`the space`) when grepping.

---

## Installing

`sudo -n` fails (`interactive authentication is required`), so the password must
be piped. Ask the user for it; do not work around sudo.

```sh
printf '%s\n' "$1" | sudo -S -p '' apt-get install -y "$DEB"
```

Verify the payload actually contains the change you just built — a stale build is
a common false negative:

```sh
dpkg-deb -x "$DEB" /tmp/df
jar tf /tmp/df/opt/fromchat/lib/app/desktop-*.jar | grep <NewClassName>
```

---

## Launching inside the GUI session

A bare `prlctl exec` is **not** in the graphical session, so it has no
`GDK_SCALE`/`WAYLAND_DISPLAY` and will misreport desktop state. Launch with the
session's environment:

```sh
export XDG_RUNTIME_DIR=/run/user/1000
export DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus
export DISPLAY=:0
export WAYLAND_DISPLAY=wayland-0
export XAUTHORITY=/run/user/1000/.mutter-Xwaylandauth.$(ls /run/user/1000 | grep mutter | head -1)
export XDG_CURRENT_DESKTOP=ubuntu:GNOME
nohup /opt/fromchat/bin/FromChat > /home/parallels/launch.log 2>&1 &
```

Note the app's stdout carries `INFO:`/`WARN:` lines directly — convenient when
the app fails before it opens its own log.

---

## Reading diagnostics

Rotate the old log first, otherwise a 5 MB file makes greps slow and mixes runs:

```sh
mv ~/.local/share/FromChat/fromchat/logs/current.log{,.prev}
```

| Location | Path |
|---|---|
| Linux | `~/.local/share/FromChat/fromchat/logs/current.log` |
| macOS | `~/Library/Application Support/FromChat/fromchat/logs/current.log` |

Useful tags: `DesktopScale`, `DesktopTray`, `DesktopIcon`, `WebSocketManager`.

---

## HiDPI scaling on Linux (hard-won)

Do **not** assume `GDK_SCALE` exists. On GNOME with mutter's
`scale-monitor-framebuffer` experimental feature, it is often absent entirely.

Check, in this order:

1. `xrandr` → a virtual monitor reports `0mm x 0mm`, so DPI-based inference
   degenerates to 96/96 = 1.0.
2. `~/.config/monitors.xml` → `<scale>1.6666666269302368</scale>`. This is mutter's
   persisted value and the authoritative one.

**`<scale>` is written BEFORE `<primary>`** inside `<logicalmonitor>`. A regex
that requires the scale tag to appear after `<primary>yes</primary>` silently
returns null. Match the whole block, then extract:

```kotlin
val block = Regex("""<logicalmonitor>(.*?)</logicalmonitor>""", RegexOption.DOT_MATCHES_ALL)
val primary = blocks.firstOrNull { it.contains("<primary>yes</primary>") }
scaleTag.find(primary ?: blocks.first())
```

**The decisive detail: AWT and Compose disagree, and forcing `sun.java2d.uiScale`
is NOT the fix.** On this setup AWT reported scale `2.0` (it read `Xft.dpi:192`
from the X server) while Compose reported `density=1.0`. Setting the AWT property
is a no-op for layout. The value that matters is Compose's `LocalDensity`:

```kotlin
CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) { ... }
```

Always log both sides and compare, rather than trusting one:

```
compose density=?  awt uiScale=?  detected=?
```

Ignore candidates at or below 1.0 when selecting a scale — an unscaled desktop
also reports exactly 1.0, so they carry no information.

---

## Gotchas that cost real time

- `Dimension.densityDpi` does not resolve on this project's classpath, and
  `Toolkit.getScreenResolution()` resolves to `Int`, not `Dimension`. Avoid that
  API; prefer reading `monitors.xml` directly.
- `Toolkit` may fail to resolve as an import in some files in this module even
  though it works elsewhere — do not assume a compile error means a missing
  dependency.
- Kotlin init scripts cannot reference plugin classes (`Unresolved reference
  'compose'`) because the plugin is not on the init script's compile classpath.
- `ps -ef | grep -c builddeb` returned via `prlctl exec` counts the grep itself — a
  "3" can mean zero real builds. Use `pgrep -f` inside a script file, or accept the
  ambiguity and rely on the log marker instead.
- Don't run a persistence/repatch hack after installing: if a fix needs to be re-applied
  by hand after every `dpkg -i`, move it into the build instead (see the skiko patch and
  the baked `MESA_*` Exec env above).
- Never `sleep` waiting on a background job; wait for the completion notification.