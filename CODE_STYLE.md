# FromChat Android — Code Style

Canonical style reference for Kotlin / Compose Multiplatform code in this repository.

Contributors are **not required** to follow this guide — but sticking to it saves me cleanup time, so it’s appreciated when you do.

This file is created mostly for AI agents to write good and readable code.

## Contents

1. [Inline single-use bindings](#1-inline-single-use-bindings)
2. [Merge small screen helpers into the main file](#2-merge-small-screen-helpers-into-the-main-file)
3. [Kotlin idioms](#3-kotlin-idioms)
4. [Packages for related files](#4-packages-for-related-files)
5. [Reuse project abstractions](#5-reuse-project-abstractions)
6. [User-visible strings](#6-user-visible-strings)
7. [Compose layout and formatting](#7-compose-layout-and-formatting)
8. [Function bodies](#8-function-bodies)
9. [General principles](#9-general-principles)

---

## 1. Inline single-use bindings

If a `val`, `var`, local function, or `@Composable` is referenced **exactly once** in the file, inline it at the use site.

Do **not** introduce a named binding only used once.

```kotlin
// ❌ BAD — used once
val padding = MaterialTheme.spacing.medium
Box(modifier = Modifier.padding(padding))

// ✅ GOOD
Box(modifier = Modifier.padding(MaterialTheme.spacing.medium))
```

```kotlin
// ❌ BAD — composable used once
@Composable
private fun ProfileHeaderTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall)
}

@Composable
fun ProfileScreen() {
    ProfileHeaderTitle(text = title)
}

// ✅ GOOD — inline at the single call site
@Composable
fun ProfileScreen() {
    Text(text = title, style = MaterialTheme.typography.headlineSmall)
}
```

**Keep a name when:**

- The expression has side effects and must not run twice.
- Inlining hides a non-obvious boundary (crypto, network, animation controller, pager math).
- Inlining hurts scanability (long chain, non-obvious subexpression).

---

## 2. Merge small screen helpers into the main file

Screen-local helpers that exist only to serve one screen should live in that screen's file, not a separate file.

Merge into the parent screen file when **all** are true:

- Used only by that screen (or its direct private helpers in the same file).
- Not shared across features or modules.
- The combined file stays readable after the merge.

```kotlin
// ❌ BAD — ProfileActionButtonRow.kt used only from ProfileScreen.kt
// ✅ GOOD — private composables at the bottom of ProfileScreen.kt
```

Extract to a separate file only when shared by **two or more** screens/features, or when the screen file would become unwieldy even after inlining.

---

## 3. Kotlin idioms

Prefer standard library helpers over verbose Java-style patterns.

```kotlin
// ❌ BAD
try {
    cache.evict(key)
} catch (_: Exception) {
}

// ✅ GOOD
runCatching { cache.evict(key) }
```

```kotlin
// ❌ BAD
val items = mutableListOf<Item>()
items.add(header)
for (row in rows) items.add(row)
items.add(footer)

// ✅ GOOD
val items = buildList {
    add(header)
    addAll(rows)
    add(footer)
}
```

Use `buildList`, `buildMap`, `buildSet`, `apply`, `also`, `takeIf`, `takeUnless`, scoped functions, and expression bodies where they match surrounding code.

---

## 4. Packages for related files

When several files belong to one feature, group them in a **package directory** instead of scattering at the parent level.

```
// ❌ BAD
ui/profile/ProfileScreen.kt
ui/profile/ProfileRoutes.kt
ui/profile/ProfileBioMarkdown.kt
ui/profile/EditProfileScreen.kt   // edit is a sub-flow

// ✅ GOOD
ui/profile/ProfileScreen.kt
ui/profile/ProfileRoutes.kt
ui/profile/bio/ProfileBioMarkdown.kt
ui/profile/edit/EditProfileScreen.kt
```

**File names**

- One primary type per file; **file name matches the primary type** (e.g. `ProfileScreen.kt` → `ProfileScreen`).
- Renames are rare — only when called out explicitly (e.g. `Utils.kt` → `ApiUtils.kt` when the type is renamed).
- Do not create a package for a single tiny file that only exists to be merged per §2.

**Sub-packages**

- Use sub-packages for sub-features (`edit`, `bio`, `menu`, `overlay`, …).
- Keep entry/bootstrap types at sensible roots when they are not feature-local (e.g. desktop `FromChat.kt` default package for WM_CLASS).

**Target layout (shared `ru.fromchat` — move files to match when touching packages)**

| Area | Package / folder | Notes |
|------|------------------|--------|
| Logging | `ru.fromchat.logging` | `Logger*` types |
| Local API / WS | `ru.fromchat.api.local` | e.g. `WebSocketManager`, keep-alive helpers |
| API sync | `ru.fromchat.api.sync` | `*Sync*`, deferred startup network, update sync |
| Push | `ru.fromchat.api.push` | FCM registration |
| API helpers | `ru.fromchat.api.util` | shared API utilities (`ApiUtils.kt`) |
| API client | `ru.fromchat.api` | `ApiClient.kt` stays at package root |
| Chat message UI | `ru.fromchat.ui.chat.message` | message rows, mouse interaction helpers |
| UI theme | `ru.fromchat.ui.theme` | |
| UI welcome | `ru.fromchat.ui.welcome` | |
| JVM popups | `ru.fromchat.ui.popup.jvm` | |
| Main shell | `ru.fromchat.ui.main` | screen shells; `desktop/`, `nav/` subdirs |
| Chat chrome | `ru.fromchat.ui.chat` | `menu/`, `message/`, `chrome/`, `media/`; root e.g. `Avatar.kt` |
| Calls | `ru.fromchat.ui.calls` | `overlay/`, `web/`; root e.g. incoming notify |
| Profile | `ru.fromchat.ui.profile` | `edit/`; badges/widgets; root screens/routes |
| App root | `ru.fromchat.ui` | `App.kt` at package root |

**Stay at `ru.fromchat` root (for now):** `AppForeground`; iOS `MainViewController`, `IosApplicationBootstrap`.

---

## 5. Reuse project abstractions

Prefer existing project components and utilities over new wrappers:

- `ru.fromchat.utils` — clipboard, `Modifier.conditional`, settings, crypto helpers, list UI (`Category`, `ListItem`, …)
- `ru.fromchat.ui.components` — shared UI primitives.
- `apiRequest` / existing API client patterns.

Match naming, imports, and structure of adjacent files in the same package.

---

## 6. User-visible strings

No hardcoded user-visible copy in shared UI. Use Compose Multiplatform resources:

- `app/shared/src/commonMain/composeResources/values/strings.xml`
- `app/shared/src/commonMain/composeResources/values-ru/strings.xml`

Exception: debug API screen (`ru.fromchat.ui.debug`).

---

## 7. Compose layout and formatting

### Blank lines between composables

Separate **every** `@Composable` in a file with **one** blank line — top-level and `private`.

```kotlin
@Composable
fun Header() { ... }

@Composable
fun Body() { ... }
```

### File size

No hard line limit. Merge or split based on readability.

### Visibility

Screen-local composables merged into a screen file are `private`.

### Layout / dimension constants

Do **not** introduce named constants for bare `.dp` values — use literals inline.

```kotlin
// ❌ BAD
private val CardPadding = 16.dp
Box(modifier = Modifier.padding(CardPadding))

// ✅ GOOD
Box(modifier = Modifier.padding(16.dp))
```

For non-trivial layout values (ratios, spring specs, derived calculations), use top-level `private const` or `private val` in the same file.

---

## 8. Function bodies

- If a function contains **only** a `return` statement, always use an expression body (`=`).
- If the logic is a progressive data transform chainable with `let` / `apply` / `also` / `run`, prefer an expression body.
- Otherwise use a block body.

```kotlin
// ✅ GOOD — single return
private fun label(user: User) = user.visibleUsername ?: stringResource(Res.string.user_fallback)

// ✅ GOOD — chain
private fun normalized(input: String) = input.trim().takeIf { it.isNotEmpty() }?.lowercase().orEmpty()
```

---

## 9. General principles

- Do not strip or rewrite data by comparing to hard-coded UI placeholder strings.
- Do not introduce abstractions used only once (same rule as §1).
- When a convention is ambiguous, match neighboring files in the same package.

---

For automated style cleanup on an existing diff, use the **`adapt-to-style`** skill (`.agents/skills/adapt-to-style/`). User-visible copy: §6.
