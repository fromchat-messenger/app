# SNI-over-D-Bus tray for Linux (GNOME 50+ Wayland)

Status: **design, not yet implemented** — all claims below were verified against the
`ubuntu-appindicators@ubuntu.com` sources shipped on the actual target VM
(Ubuntu 26.04.1, GNOME Shell 50.1, Parallels ARM64). Extension files referenced:
`statusNotifierWatcher.js`, `appIndicator.js`, `dbusMenu.js`,
`indicatorStatusIcon.js`, `interfaces-xml/*.xml`, `pixmapsUtils.js`.

## 1. Problem

The Linux tray today is Java AWT `SystemTray`/`TrayIcon` — XEmbed on Linux
(`XTrayIconPeer`, JDK-8341144: hard-coded 24×24, no HiDPI). Every desktop moved to
the **StatusNotifierItem (SNI)** protocol years ago; GNOME upstream removed the
XEmbed host entirely in GNOME 45. Our tray icon only shows on GNOME today because
Ubuntu's AppIndicator extension still shims XEmbed *behind a setting*
(`org.gnome.shell.extensions.appindicator legacy-tray-enabled`, currently `true` on
our VM). When Ubuntu flips that default, or on GNOME upstream, the icon vanishes
and the custom Compose popup with it — the icon has no owner to click.

SNI replaces the whole XEmbed round-trip: the shell itself hosts the icon and
renders our menu; the windows/geometry code (`DesktopTrayMenu.kt`, the whole
translucent-window dance) is not even involved on GNOME.

## 2. Evidence — what the target session actually provides

Verified via D-Bus and by reading the extension sources on the VM:

- **Watcher**: `org.kde.StatusNotifierWatcher` is owned by gnome-shell (the
  extension), object path `/StatusNotifierWatcher`;
  `IsStatusNotifierHostRegistered = true`, `ProtocolVersion = 0`.
  Existing items (snapd/updater) register with *attachment-style paths*
  (`:1.42@/org/ayatana/NotificationItem/livepatch`).
- **Registration** (`statusNotifierWatcher.js: RegisterStatusNotifierItemAsync`):
  two accepted argument styles —
  - `service` starting with `/` (ayatana style) → bus name = **caller's unique
    sender name**, object path = `service`;
  - `service` a well-known bus name (KDE style) → object path defaults to
    `/StatusNotifierItem`.
  Either works. We use the ayatana style: one unique D-Bus connection, path
  `/org/ayatana/NotificationItem/FromChat`.
- **Click semantics** (`indicatorStatusIcon.js vfunc_button_press_event`, middle
  of the story):
  | click | shell behavior |
  |---|---|
  | left single | if `Activate` method present: **menu opens after the double-click timeout** (`_waitForDoubleClick` → `menu.toggle()`); if the item has no `Activate` method: menu opens immediately |
  | left double | `_maybeHandleDoubleClick` → `appIndicator.open(x, y)` → **`Activate(x, y)`** on our item |
  | right | `menu.toggle()` — always the shell-rendered DbusMenu popup |
  | middle | `secondaryActivate` → prefers *`XAyatanaSecondaryActivate(timestamp)`* then `SecondaryActivate(x, y)` |
  | scroll | `Scroll(delta, orientation)` |
- **Activation token**: before calling `Activate` the shell calls
  `ProvideXdgActivationToken(token)` on our item if we have that method
  (`appIndicator.js provideActivationToken`) — relevant on Wayland when we launch
  a new window from the tray click.
- **Menu client** (`dbusMenu.js`): implements the *client* side of
  `com.canonical.dbusmenu` (its shipped XML is authoritative for the wire
  contract — see §4). After building the root layout it calls
  `AboutToShow(0)` unconditionally ("Dropbox requires us to call AboutToShow(0)
  first"), and calls `Event(root, 'opened'/'closed', …)` when the popup opens and
  closes, `Event(id, 'clicked', …)` on activation.
- **Icons** (`pixmapsUtils.js getBestPixmap`): data bytes are **ARGB32,
  big-endian** (`argbToRgba` flips ARGB→RGBA), `rowStride = width * 4` assumed,
  and the shell picks the **smallest pixmap ≥ preferred size**, else the largest.
  Panel tray size is ~16 logical px (Yaru/Ubuntu: 2× → 32, 3× → 48), so supply
  **32×32 and 48×48**; only one pixmap is ever read at a time (the array is
  re-fetched when we emit `NewIcon`).
- **Tooltips**: the shipped XML comments `ToolTip`/`NewToolTip` *out* — GNOME
  shows no tooltip. KDE (which reads ToolTip) will show it, so still set it.
- **No left-click-setting**: extension prefs only expose icon/legacy-tray pages —
  there is no "click mode" knob; the behavior matrix above is hard-coded.

## 3. Architecture

### Library (preflight-checked)

- **`org.freedesktop.dbus:dbus-java-core` (hypfvieh, ≥ 5.x, MIT)** +
  `dbus-java-transport-native-unixsocket`. Pure Java, no GTK/JNA, maintained;
  handles marshalling, object export, properties and signals. Note the version
  compat matrix at implementation time (dbus-java 5 needs Java 11+, ours is 21).
- Alternatives rejected:
  - *dorkbox SystemTray* — JNA+GTK-3 bind to `libayatana-appindicator3`, a
    legacy GTK3 stack Ubuntu is actively deprecating; drags in win32/mac native
    bits; hides the protocol instead of fixing it.
  - *raw hand-rolled D-Bus client* — thousands of lines of marshalling; waste.
  - *status quo* — depends on the legacy XEmbed shim being enabled (§1).

### Code layout (new package `ru.fromchat.desktop.tray`)

| file | role |
|---|---|
| `SniDBus.kt` | D-Bus interfaces (`SniItemIface`, `SniMenuIface`, `SniWatcherIface`) — method-only declarations; properties live in the hand-rolled `Properties` impls below because dbus-java binds `@DBusBoundProperty` getters by their raw Java type, which cannot express `a(iiay)` from Kotlin |
| `SniTypes.kt` | wire structs, ARGB32 pixmap packing, `sniPixmaps()` widening, menu-item property maps |
| `SniProperties.kt` | shared `org.freedesktop.DBus.Properties` implementation served by each exported object (explicit-signed `Variant` values) |
| `SniItemImpl.kt` | `org.kde.StatusNotifierItem` export: properties, tooltip state, `Activate`/`SecondaryActivate` → callbacks |
| `SniMenuImpl.kt` | `com.canonical.dbusmenu` export: static tree `status | sep | Show | About | sep | Quit`, `GetLayout`/`Event` handlers, label updates via `ItemsPropertiesUpdated` |
| `SniTray.kt` | `SniTrayController.tryInstall/updateStatus/close`: connection lifecycle, watcher registration, `NameOwnerChanged` re-register, EDT-hopped callbacks |
| `DesktopTrayMenu.kt` | decision point in `DesktopTrayHost`: Linux → SNI first, AWT/XEmbed fallback only when the session bus / watcher is missing. Windows/macOS unchanged |

`Main.kt` keeps selecting Compose `Tray` on macOS, and replaces the Linux/Windows
branch with `DesktopTrayHost` → SNI decision inside it (Windows never takes the
SNI path; decision gated to Linux).

### dbus-java 5.2.2 implementation notes (two trapping quirbs, both solved in pure Kotlin)

1. **`@DBusBoundProperty` is unusable from Kotlin.** The annotation's `type` argument needs
   class literals like `SniPixmapStruct[].class` — not expressible as Kotlin annotation
   arguments — and the fallback binding, the getter's raw Java type, exports raw `List`
   as `av` or refuses the export. Solution: no bound properties at all; each exported object
   *also implements* `org.freedesktop.DBus.Properties` (`SniPropertiesProvider`) with values
   built as `Variant(value, "a(iiay)")` etc., so dbus-java marshals exactly the wire types of
   §4. Remote watcher properties (`IsStatusNotifierHostRegistered`) are read through the
   standard `Properties` proxy the same way; `SniWatcherIface` only carries the register call.
2. **Exported-method return structs go through an erased-field XML generator**
   (`ExportedObject.createReturnArguments` uses `Field#getType` on the *outer* struct), so any
   `Pair`-like struct with `List`/`Map` fields dies at `exportObject` with
   `Exporting non-exportable type: java.util.List` even though deeper struct recursion is
   generic-aware. Solution: make outer return structs implement `dbus-java`'s
   `DBusSerializable` (`SniGetLayoutResult`, `SniAboutToShowGroupResult`) — the wire shape is
   then derived from the `deserialize` signature with full generic info, and the runtime
   expands `serialize()` to exactly the reply args of §4. (`GetLayout` → `u` + `(ia{sv}av)`,
   `AboutToShowGroup` → `ai` + `ai`, verified in the exported introspection XML.)

### Lifecycle

- Connection attaches lazily on first need (desktop app start, after the
  AWT/Toolkit is up), on a daemon thread, never blocking startup: tray is a nice-to-have — any D-Bus
  failure logs a warning and leaves the window-only UX intact.
- `SystemTray.isSupported()` stays the first check for the *fallback* path only.
- App quit → `connection.close()` so D-Bus doesn't outlive us; single-instance
  lock unaffected (no well-known name ownership; caller-side unique names are fine
  with both the extension and plasmashell).
- Watcher restart: on `NameOwnerChanged` for the watcher, re-export (cheap) and
  re-call `RegisterStatusNotifierItem`. The shell also re-scans existing
  implementations after registration, so duplicates must be idempotent (extension
  warns but tolerates a duplicate register; we just re-register once).

## 4. The wire contract (exact, from the shipped XML)

### `org.kde.StatusNotifierItem` (export at `/org/ayatana/NotificationItem/FromChat`)

```
Properties: Category "ApplicationStatus"; Id "FromChat"; Title "FromChat";
            Status "Active"; WindowId 0; IconThemePath "";
            ItemIsMenu false; Menu "/org/ayatana/NotificationItem/FromChat/Menu"
            IconPixmap a(iiay)           // see §2 icon packaging
            ToolTip   (sa(iiay)ss)       // icon struct, title, description
Methods:    Activate(i x, i y)             → showMainWindow()
            SecondaryActivate(i x, i y)    → showMainWindow()
            ContextMenu(i x, i y)          → no-op (shell opens our Menu itself)
            ProvideXdgActivationToken(s)   → stash token, publish via GDK?
                                           (v1: no-op with log)
            Scroll(i, s)                   → no-op
Signals:    NewIcon() once after registration (pixmap refresh trigger)
```

Note on `Activate` presence: with `Activate` exported, GNOME adds the
double-click wait on single left-press (menu toggle ~0.4 s later) and double-click
becomes "raise main window". Without it the menu opens instantly but nothing else
can trigger show. We export `Activate` (matches Telegram/Steam behavior and KDE
where left-click *is* activation), and give the menu a prominent "Show" row so
single-click users are served too.

### `com.canonical.dbusmenu` (export at `Menu` path above)

Shipped XML (authoritative — the shell only calls what its own XML declares):

```
GetLayout(i parentId, i recursionDepth, as propertyNames)
      → (u revision, (ia{sv}av) layout)
      // NB: children arrive as Variants of recursive nodes — NOT the
      // spec's flattened (ia{sv}ia{sv}); dbus-java must be told the
      // (ia{sv}av) node type explicitly
GetGroupProperties(ai ids, as propertyNames) → a(ia{sv})
GetProperty(i id, s name)                   → v
Event(i id, s eventId, v data, u timestamp) → (void)
EventGroup(a(isvu) events)                  → ai idErrors
AboutToShow(i id)                           → b needUpdate
AboutToShowGroup(ai ids)                    → (ai updatesNeeded, ai idErrors)
Properties: Version u (protocol v2 ⇒ respond 2); TextDirection "ltr";
            Status "normal"; IconThemePath as (empty array)
Signals:    LayoutUpdated(u revision, i parent)   — on tree changes (v1: status label only → property updates, no tree change)
            ItemsPropertiesUpdated(a(ia{sv}) updated, a(ias) removed)
            ItemActivationRequested(i id, u timestamp) — optional, unused
```

Client flow to expect (from `dbusMenu.js`):

1. `GetLayout(0, -1, ['type','children-display'])` for the skeleton;
2. `GetGroupProperties(ids=[], propertyNames: 'label','visible','enabled','type','children-display','icon-name','icon-data','toggle-type','toggle-state',…)`;
3. `AboutToShow(0)` — respond `needUpdate=false` once the snapshot is stable
   (returning `true` makes the client re-fetch the full layout);
4. popup open/close → `root Event 'opened'/'closed'`; item click →
   `Event(id,'clicked', v(int 0), timestamp)`.

Client rendering rules worth respecting:

- item `type = "separator"` → separator; `children-display = "submenu"` → submenu
  (we ship both; no `AboutToShowGroup` calls expected for an empty-separator tree);
- `label` mnemonic: client rewrites `_x` → plain `x` (first match only) — keep `_`
  out of translated labels or allow it as a mnemonic;
- `enabled=false` renders insensitive (our status row);
- `icon-name` is read via the **shell theme** only (`St.Icon.icon_name`), and
  `icon-data` must be **PNG bytes** (client feeds them to `GdkPixbuf`) — v1 ships
  text-only items (no icons per item) to keep the first cut small; item icons can
  come later as PNGs rendered off-screen.

Property-change plumbing: status row label changes → `ItemsPropertiesUpdated([ (1, {label: s}), …], [])`
emitted by the model diff section.

## 5. Verification plan (hard gates, per the screenshot rule)

1. **D-Bus presence gates** (scripted, via MCP exec):
   - watcher props before/after registration: our item id string appears in
     `RegisteredStatusNotifierItems`;
   - `busctl --user tree`/`gdbus introspect --session --dest
     <unique-name> --object-path /org/ayatana/NotificationItem/FromChat` returns
     exported `StatusNotifierItem` + `Menu` interfaces.
2. **Visual gate** (`parallels_vm_capture`, mandatory before claiming success):
   app running → icon visible in the top bar → open the popup (mouse press on the
   icon) → screenshot; then screenshot after closing.
3. **Interaction gates** (execution + screenshot):
   - menu "Show" raises the main window (screenshot changes);
   - menu "Quit" exits the app (`pgrep FromChat` → 0 — historically `pkill -f`
     killed shells: never use it);
   - window-close-to-tray still re-shows from the tray.
4. **Regression gates**: hardware-accelerated render still healthy afterwards
   (`grep -c` of `Fallback to next API`/`Shader compilation error` in
   `launch.log` must stay `0`, `healthy=1` line present) + screenshot of the main
   window; `MESA_GL_VERSION_OVERRIDE=3.0` wrapper path (`/usr/local/bin/fromchat-hwgl`)
   unaffected.
5. **Fallback regression**: with the watcher hidden (`gdbus call … Unregister`
   is not possible; simulate by renaming host extension off) `SystemTray` path
   still compiles — unit-test the decision table instead of GUI-testing it.

## 6. Risks / open questions

- **dbus-java node typing**: `GetLayout` children being `(ia{sv}av)` (variant
  recursion) must be exactly right; a wrong signature makes the shell log
  `Failed to read layout` and drop the menu. Verify against a debug
  `busctl intercept`… realistically: iterate against the real shell and read its
  logger (`journalctl --user -u gnome-shell | grep -i appindicator`).
- **dbus-java Properties interface**: we implement manual `Properties.Get/Set/GetAll`
  responders (the library's `DBusInterface`/`ExportedObject` may already provide it
  — confirm with a smoke test; the tool must not re-implement what it exports).
- Deb/jpackage bundling of the 2 extra jars (dbus-java-core,
  unixsocket transport + jnr-style peer deps? none: the **hollow** native
  transport uses the same `libunixsocket-java`? check dbus-java 5's unixsocket
  transport's own dependencies at implementation time) — `createDistributable`
  already ships `lib/` jars, so it should be automatic; verify in the built deb.
- **KDE plasma** (not our target VM) uses `com.canonical.dbusmenu` too, so the one
  implementation covers it; XFCE (X11) may still be XEmbed-hosted → keep fallback.
- Future: item icons per menu row (PNG bytes), `Scroll`→workspace switching,
  attention state (e.g. connection-error ordering number badge on the icon),
  upstream report
  if we find extension-side bugs in `GetGroupProperties` handling (we ship the
  whole property map anyway, sidestepping partial-property merges).
