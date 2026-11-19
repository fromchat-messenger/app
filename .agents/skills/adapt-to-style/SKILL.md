---
name: adapt-to-style
description: Adapts Kotlin/Compose code (diff, single file, or multiple files) to FromChat CODE_STYLE.md strictly without changing behavior. Prioritizes inlining single-use bindings and idiomatic Kotlin. Use when cleaning up style, refactoring for conventions, adapting a diff to code style, or when the user mentions adapt-to-style, code style cleanup, or style pass.
---

# Adapt to style

Refactor target code to match `[CODE_STYLE.md](../../../CODE_STYLE.md)` at the repository root. **Do not change behavior.**

## Priorities (always first)

These two rules outweigh other cleanup when time or diff size is tight — do them **before** formatting, packaging, or component reuse:

1. **Inline single-use bindings** ([CODE_STYLE.md §1](../../../CODE_STYLE.md#1-inline-single-use-bindings)) — any `val`, `var`, local function, lambda holder, or `@Composable` referenced **exactly once** in the file belongs at the use site. Do not leave intermediate names that exist only to feed the next line.
2. **Idiomatic Kotlin** ([CODE_STYLE.md §3](../../../CODE_STYLE.md#3-kotlin-idioms) and [§8](../../../CODE_STYLE.md#8-function-bodies)) — replace verbose Java-style control flow with stdlib helpers (`runCatching`, `buildList` / `buildMap` / `buildSet`, `apply` / `also`, `takeIf` / `takeUnless`, scope functions) and **expression bodies** where the guide allows. Prefer one clear expression over nested `if`/`else` blocks when readability stays the same.

Respect §1 “keep a name when” (side effects, must-not-run-twice, non-obvious boundaries). When inlining would change evaluation order or double-invoke a composable, keep the binding.

## Writing new code

When implementing features (not a style-only pass):

1. Read `CODE_STYLE.md` before writing.
2. **Default: no single-use `val`s** — write the expression at the call site unless §1 says to keep a name.
3. **Default: idiomatic Kotlin** — match neighboring files and use stdlib builders / expression forms from the start.
4. **Do not ask the user style questions** — apply the guide and use your judgment.

If the user used this skill in a prompt asking to implement/fix something, adhere to coding style as part of the implementation, with inlining and Kotlin idioms applied in the same edit.

## Style adaptation pass

When cleaning up an existing diff or file set:

### Before you start

1. Read `CODE_STYLE.md` fully (at minimum §1, §3, §8).
2. Identify scope: git diff, named files, or a directory.
3. Read surrounding files in the same package for precedent.

### Refactor checklist

Apply in order. **Steps 1–2 are mandatory** for every file in scope; do not skip them for “cosmetic” passes.

- [ ] **Inline (priority)** — remove or fold every `val`/`var`/local/`@Composable` used exactly once (§1). Scan the whole file, not only changed lines.
- [ ] **Kotlin functions (priority)** — non-idiomatic patterns → stdlib idioms and expression bodies where appropriate (§3, §8). Examples: empty `try/catch` → `runCatching`; manual mutable collections → `buildList`/`buildMap`; trivial `if`/`return` → `when` or expression body.
- [ ] **Merge** screen-only helper files into their parent screen file (§2).
- [ ] **Group** related multi-file features into sub-packages where appropriate (§4).
- [ ] **Reuse** existing project components; remove one-off wrappers (§5).
- [ ] **Strings** — no new hardcoded user-visible copy; use compose resources (§6).
- [ ] **Formatting** — one blank line between composables; `private` screen helpers; no `.dp` named constants (§7).

### Uncertainty log

Only during a **style adaptation pass** — not when writing new code.

When unsure how to refactor something:

1. Create or append to `.cursor/code_style_progress_<YYYYMMDD-HHmm>.md` (use current local time).
2. For each item:
  ```markdown
   ## relative/path/File.kt
   - Unsure: [specific construct and why]
   - Chosen approach: [what you did for now]
  ```
3. Continue refactoring — do not block on open questions.
4. After all files are done, **re-read** the progress file and ask the user the listed questions.

### Constraints

- No behavior, API, or logic changes.
- No magic-string sanitization of real user/message data.
- Minimal diff: only what style requires — but **do not** preserve single-use bindings or verbose Kotlin “to keep the diff small.”
- Do not extract new abstractions that would be used once (inlining beats extracting).
- Do not split files that §2 says should be merged.
- Do not change public API for style-only passes.

### Validation

After Android-affecting changes, run per [AGENTS.md](../../../AGENTS.md) (compile gate and device verify when applicable):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:shared:compileAndroidMain :app:desktop:compileKotlin
```

Fix compile errors before finishing. Skill-only edits to this file do not require the compile gate.

### Output

Summarize:

- Files touched and main style changes (call out inlining and idioms explicitly).
- Any entries from the progress file that need user decisions.
- Build result (or N/A for docs-only).