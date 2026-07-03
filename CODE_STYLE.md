# FromChat Android — Code Style

Canonical style reference for Kotlin / Compose Multiplatform code in this repository.

Contributors are **not required** to follow this guide — but sticking to it saves me cleanup time, so it’s appreciated when you do.

This file is created mostly for AI agents to write good and readable code.

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

Rules:

- One primary type per file; file name matches the primary type.
- Sub-packages for sub-features (e.g. `edit`, `bio`, `panels/dm`).
- Do not create a package for a single tiny file that only exists to be merged per §2.

---

## 5. Reuse project abstractions

Prefer existing project components and utilities over new wrappers:

- `com.pr0gramm3r101.utils` — clipboard, `Modifier.conditional`, etc.
- `com.pr0gramm3r101.components` — `Category`, `ListItem`, etc.
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

