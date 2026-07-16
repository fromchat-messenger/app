---
name: adapt-to-style
description: Adapts Kotlin/Compose code (diff, single file, or multiple files) to FromChat CODE_STYLE.md strictly without changing behavior. Use when cleaning up style, refactoring for conventions, adapting a diff to code style, or when the user mentions adapt-to-style, code style cleanup, or style pass.
---

# Adapt to style

Refactor target code to match `[CODE_STYLE.md](../../../CODE_STYLE.md)` at the repository root. **Do not change behavior.**

## Writing new code

When implementing features (not a style-only pass):

1. Read `CODE_STYLE.md` before writing.
2. Follow it from the start — inline single-use bindings, idiomatic Kotlin, match neighboring files.
3. **Do not ask the user style questions** — apply the guide and use your judgment.

If the user used this skill in a prompt asking to implement/fix something, you should just adhere to the coding style.

## Style adaptation pass

When cleaning up an existing diff or file set:

### Before you start

1. Read `CODE_STYLE.md` fully.
2. Identify scope: git diff, named files, or a directory.
3. Read surrounding files in the same package for precedent.

### Refactor checklist

Apply in order:

- [ ] **Inline** `val`/`var`/locals/`@Composable` used exactly once in the file (§1).
- [ ] **Merge** screen-only helper files into their parent screen file (§2).
- [ ] **Replace** non-idiomatic Kotlin with idioms: `runCatching {}`, `buildList {}`, `buildMap {}`, etc. (§3).
- [ ] **Group** related multi-file features into sub-packages where appropriate (§4).
- [ ] **Reuse** existing project components; remove one-off wrappers (§5).
- [ ] **Strings** — no new hardcoded user-visible copy; use compose resources (§6).
- [ ] **Formatting** — one blank line between composables; `private` screen helpers; no `.dp` named constants (§7).
- [ ] **Expression bodies** where §8 applies.

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
- Minimal diff: only what style requires.
- Do not extract new abstractions that would be used once.
- Do not split files that §2 says should be merged.
- Do not change public API for style-only passes.

### Validation

After Android-affecting changes, run per `android.mdc`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:shared:compileAndroidMain :app:shared:compileKotlinIosArm64
```

Fix compile errors before finishing.

### Output

Summarize:

- Files touched and main style changes.
- Any entries from the progress file that need user decisions.
- Build result.

