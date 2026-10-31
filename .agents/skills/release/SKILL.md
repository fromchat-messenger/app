---
name: release
description: "Ship a FromChat app release via GitHub Actions (workflow_dispatch). Use when the user asks to release, cut a version, or run the release workflow. Invoke as /release."
---

# release

Canonical path: [`.github/workflows/release.yml`](../../.github/workflows/release.yml) (`workflow_dispatch`, `version` input).

**Fork-safe repo:** never hardcode `fromchat-messenger/android`. Resolve the origin remote:

```bash
REPO=$(gh repo view --remote origin --json nameWithOwner -q .nameWithOwner)
```

**Fork caveat:** GitHub Actions and repository secrets must exist on the repo you dispatch against; forks may not have the same setup as upstream.

**Version source:** root [`build.gradle.kts`](../../build.gradle.kts) — `extra["versionName"]` and `extra["versionCode"]`.

## Preflight (required — do not dispatch if any check fails)

Try to fix failures yourself when the fix does not change product logic (wrong tag placement, typo in version line, compile errors from bad imports). If human judgment is required, **stop** and explain; do **not** run the workflow.

### 1. Local compile

From the app repo root, with JDK 21+ (`JAVA_HOME` appropriate for your OS — see [AGENTS.md](../../AGENTS.md) §4):

```bash
./gradlew :app:shared:compileAndroidMain :app:shared:compileKotlinIosArm64 :app:desktop:compileKotlin
```

All three tasks must succeed. A failed compile wastes ~17+ minutes of CI.

### 2. Version bumped vs latest tag

- Determine the latest release tag: `git tag -l 'v*' --sort=-v:refname | head -1` (or empty if none).
- Read `versionName` at **HEAD** from `build.gradle.kts` (must match the `-f version=` you will pass to the workflow, without a `v` prefix).
- Compare to `versionName` at that tag: `git show '<tag>':build.gradle.kts` and extract the same line. If there is no prior tag, `versionName` at HEAD must still be intentional for this release.
- **Require:** `versionName` at HEAD **differs** from the latest tag’s file (a real bump). **Require:** workflow `version` input equals HEAD `versionName`.

If the user wants a release but the file was not bumped, bump `versionName` / `versionCode` in `build.gradle.kts` only when the user explicitly asked for that version — otherwise stop and ask.

### 3. Release tag on the right commit

Let `TAG="v${versionName}"` (same version as preflight §2).

- **Require:** annotated or lightweight tag `TAG` exists locally or on `origin`.
- **Require:** `git rev-parse "$TAG"` equals the commit you are releasing (typically `HEAD` after commit; after push, `origin/<branch>` must contain that commit).

If **`TAG` exists but points at a different commit:** stop. Use the **AskQuestion** tool (or ask in chat if unavailable): *“Can I delete the tag and recreate it on the current commit?”* — options e.g. Yes / No. Only if approved: delete locally (`git tag -d "$TAG"`), delete on remote if pushed (`git push origin ":refs/tags/$TAG"`), then `git tag "$TAG"` on the intended commit and push the tag. Never force-move a tag without explicit approval.

If **`TAG` is missing** but version and commit are otherwise ready: create `git tag "$TAG"` on the intended commit and push the tag before dispatch (unless the user’s process says otherwise — then ask).

Do **not** dispatch `release.yml` until §1–§3 pass.

## Order (after preflight)

1. **`git status`** — if there are uncommitted changes the user wants in the release, commit them first (no secrets in the commit).

2. **`git push -u origin HEAD`** (and push the release tag if you created or moved it).

3. **`gh`** — install if missing. If `gh auth status` fails, run `gh auth login`.

4. **Dispatch** — prefer the default branch for cache reuse; if dispatching from another branch, warn that Actions caches are scoped to that ref only (see workflow comments).

   ```bash
   REPO=$(gh repo view --remote origin --json nameWithOwner -q .nameWithOwner)
   gh workflow run release.yml -R "$REPO" --ref "<branch>" \
     -f version="<version>" \
     -f skip_publish="false" \
     -f force_update_release="false" \
     -f rebuild="false"
   ```

   Inputs match `release.yml`: `version` (required), `skip_publish`, `force_update_release`, `rebuild`.

5. **Report** the workflow run URL to the user. CI creates/updates the GitHub Release for `TAG` during publish; do not rely on dispatch alone if preflight required a local tag on this commit.
