---
name: release
description: "Ship a FromChat app release via GitHub Actions (workflow_dispatch). Use when the user asks to release, cut a version, or run the release workflow. Invoke as /release."
---

# release

Canonical path: [`.github/workflows/release.yml`](../../.github/workflows/release.yml) (`workflow_dispatch`, `version` input).

**Fork-safe repo:** never hardcode `fromchat-messenger/android`. From the app repo root (current directory must be the git checkout):

```bash
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
```

Do **not** use `gh repo view --remote origin` — that flag is invalid on some `gh` versions and the command fails before dispatch.

**Fork caveat:** GitHub Actions and repository secrets must exist on the repo you dispatch against; forks may not have the same setup as upstream.

**Version source:** root [`build.gradle.kts`](../../build.gradle.kts) — `extra["versionName"]` and `extra["versionCode"]`.

## Preflight (required — do not dispatch if any check fails)

Try to fix failures yourself when the fix does not change product logic (wrong tag placement, typo in version line, compile errors from bad imports). If human judgment is required, **stop** and explain; do **not** run the workflow.

### 1. Local compile (KMP)

From the app repo root, with JDK 21+ (`JAVA_HOME` appropriate for your OS — see [AGENTS.md](../../AGENTS.md) §4):

```bash
./gradlew :app:shared:compileAndroidMain :app:shared:compileKotlinIosArm64 :app:desktop:compileKotlin
```

All three tasks must succeed. A failed compile wastes ~17+ minutes of CI.

**`commonMain` on iOS/Native** (often missed when code is JVM-tested only):

- `@Volatile` → `import kotlin.concurrent.Volatile` (not `java.util.concurrent` / bare `Volatile`).
- `Dispatchers.IO` in shared code → `import kotlinx.coroutines.IO` (IO is internal without that import on Native).
- Throwable type names → `error::class.simpleName`, not `error.javaClass`.

### 2. Local ProGuard (desktop — matches CI “Build JAR”)

`compileKotlin` does **not** run desktop release shrinking. CI fails on:

```bash
./gradlew :app:desktop:exportReleaseProguardForCi --no-daemon --console=plain
```

(Same task as the **Build JAR** job step “Compile and ProGuard release desktop jars”.)

**Typical ProGuard break when adding shared models:**

- `@Serializable` data classes that reference a type **without** `@Serializable` (e.g. a plain `interface Auth`) — ProGuard drops the interface and aborts with “unresolved references” / “Please correct the above warnings first”.
- **Fix:** mark the hierarchy root `@Serializable` (e.g. `sealed interface Auth`) and/or add `-keep` rules in [`app/desktop/proguard-rules.pro`](../../app/desktop/proguard-rules.pro). Log path on failure: `app/desktop/build/compose/logs/proguardReleaseJars/`.

**macOS local vs CI:** `exportReleaseProguardForCi` on a Mac with JetBrains Runtime (JBR/JCEF) may still fail with dozens of `com.sun.javafx` / `com.sun.glass` “descriptor class” warnings that **do not** reproduce on `ubuntu-latest` CI. Use the **Build JAR** job on GitHub as the release gate when local ProGuard only fails on Mac JBR noise after app-level fixes are in.

**Incognito / SQLCipher on desktop:** `jvmMain` uses `sqlite-jdbc-crypt` (Willena) plus SQLDelight’s JDBC driver, which normally pulls `org.xerial:sqlite-jdbc`. Two JDBC stacks → ~300 ProGuard “duplicate program class `org.sqlite.*`” errors on CI. Exclude xerial from the SQLDelight driver and keep only `sqlite-jdbc-crypt` (see `app/shared/build.gradle.kts` `jvmMain.dependencies`).

**~40 unresolved references on CI:** JDK 25 + Compose Desktop Glass/font JNI “descriptor class” notes (and BouncyCastle ASN.1 on SSH PEM). `app/desktop/proguard-rules.pro` keeps `org.bouncycastle.**`, narrow `com.sun.glass.ui` / `com.sun.javafx.font`, and uses `-ignorewarnings` so release ProGuard does not abort (verified via `exportReleaseProguardForCi`).

### 3. Version bumped vs latest tag

- Determine the latest release tag: `git tag -l 'v*' --sort=-v:refname | head -1` (or empty if none).
- Read `versionName` at **HEAD** from `build.gradle.kts` (must match the `-f version=` you will pass to the workflow, without a `v` prefix).
- Compare to `versionName` at that tag: `git show '<tag>':build.gradle.kts` and extract the same line. If there is no prior tag, `versionName` at HEAD must still be intentional for this release.
- **Require:** `versionName` at HEAD **differs** from the latest tag’s file (a real bump). **Require:** workflow `version` input equals HEAD `versionName`.

If the user wants a release but the file was not bumped, bump `versionName` / `versionCode` in `build.gradle.kts` only when the user explicitly asked for that version — otherwise stop and ask.

### 4. Release tag on the right commit

Let `TAG="v${versionName}"` (same version as preflight §3).

- **Require:** annotated or lightweight tag `TAG` exists locally or on `origin`.
- **Require:** `git rev-parse "$TAG"` equals the commit you are releasing (typically `HEAD` after commit; after push, `origin/<branch>` must contain that commit).

If **`TAG` exists but points at a different commit:** stop. Use the **AskQuestion** tool (or ask in chat if unavailable): *“Can I delete the tag and recreate it on the current commit?”* — options e.g. Yes / No. Only if approved: delete locally (`git tag -d "$TAG"`), delete on remote if pushed (`git push origin ":refs/tags/$TAG"`), then `git tag "$TAG"` on the intended commit and push the tag. Never force-move a tag without explicit approval.

If **`TAG` is missing** but version and commit are otherwise ready: create `git tag "$TAG"` on the intended commit and push the tag before dispatch (unless the user’s process says otherwise — then ask).

Do **not** dispatch `release.yml` until §1–§4 pass.

## Order (after preflight)

1. **`git status`** — if there are uncommitted changes the user wants in the release, commit them first (no secrets in the commit).

2. **`git push -u origin HEAD`** (and push the release tag if you created or moved it).

3. **`gh`** — install if missing. If `gh auth status` fails, run `gh auth login`.

4. **Dispatch** — prefer the default branch for cache reuse; if dispatching from another branch, warn that Actions caches are scoped to that ref only (see workflow comments).

   ```bash
   REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
   gh workflow run release.yml -R "$REPO" --ref "<branch>" \
     -f version="<version>" \
     -f skip_publish="false" \
     -f force_update_release="false" \
     -f rebuild="false"
   ```

   Inputs match `release.yml`: `version` (required), `skip_publish`, `force_update_release`, `rebuild`.

5. **Report** the workflow run URL to the user. CI creates/updates the GitHub Release for `TAG` during publish; do not rely on dispatch alone if preflight required a local tag on this commit.

## When CI fails

1. **`gh run list -R "$REPO" --workflow=release.yml --limit 3`** — get run id.
2. While the run is **in_progress**, failed job logs may be unavailable (`--log-failed` empty). Use **`gh run view <run-id> --json jobs -q '.jobs[] | select(.conclusion=="failure") | .name'`** then, when the job is **completed**, **`gh run view <run-id> --job <job-database-id> --log`**.
3. **Build JAR** / `proguardReleaseJars` → fix per §2, re-run `exportReleaseProguardForCi` locally.
4. Fix on the release branch, **commit**, **move `v{version}` tag** to the fix commit (with user approval), push branch + tag.
5. Redispatch. If **resolve-version** skipped rebuild because inputs unchanged, dispatch with **`-f rebuild="true"`** (and/or **`force_update_release="true"`** if publish must be retried).