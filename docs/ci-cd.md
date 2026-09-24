# CI/CD (Bitrise)

Operational reference for the Bitrise pipeline. Rationale and alternatives considered: [ADR-0032](adr/0032-bitrise-cicd-gitflow.md). This doc changes as the pipeline evolves (e.g. the Play Store step in Phase 6 below) — the ADR does not.

## Branch flow

```
feature/*  --PR-->  develop  --PR-->  main  --(future PR)-->  release/*
              [gate]           [gate]
```

| Event | Workflow | What runs |
|---|---|---|
| PR into `develop` or `main` | `pr-check` | `./gradlew staticAnalysis` + `./gradlew test`. Required GitHub status check — merge is blocked on failure. Branch protection requires the PR to be up to date with base and disallows admin bypass, so this is the *only* Bitrise build per PR — no separate post-merge check. |
| Push to `main` | `deploy-internal` | Builds debug + release APKs, signs release, pushes debug to Firebase App Distribution's `internal-debug` group and release to `internal-release`. |
| Push to `release/*` | *(not yet implemented — see Phase 6)* | Will build an AAB and upload to Play Store. |

No instrumented (`androidTest`) tests exist yet, so no emulator step is configured. Add one to `pr-check` if/when instrumented tests land.

## Secrets / Generic File Storage (Bitrise)

| Name | Type | Purpose |
|---|---|---|
| `google-services.json` | Generic File Storage | Firebase config for the Android app. Contains both the release app (`com.rossomak.flashcards`) and debug app (`com.rossomak.flashcards.debug`) entries. Gitignored, per-dev/CI. |
| `release.keystore` | Generic File Storage | Release signing key. **Back up outside Bitrise too** (password manager) — unrecoverable if lost, no Play App Signing fallback configured. |
| `RELEASE_STORE_FILE` | Secret env var / Gradle `-P` | Path to `release.keystore` once placed on the CI runner. |
| `RELEASE_STORE_PASSWORD` | Secret env var | Release keystore store password. |
| `RELEASE_KEY_ALIAS` | Secret env var | Release key alias (`flashcards-release`). |
| `RELEASE_KEY_PASSWORD` | Secret env var | Release key password. Same value as `RELEASE_STORE_PASSWORD` — keystore is PKCS12, which requires store and key password to match. |
| `GOOGLE_WEB_CLIENT_ID` | Secret env var | Currently only in local `local.properties`; needed in CI for the same `buildConfigField`. |
| Firebase App Distribution service account JSON | Generic File Storage | Dedicated service account, scoped to App Distribution admin only — **not** the same credential `functions/` uses for its own deploys. |

## Versioning

- `versionCode` = `git rev-list --count HEAD` — total commit count, monotonic, no tagging required.
- `versionName` = `MAJOR.MINOR.<commit count>` — `MAJOR`/`MINOR` are hand-edited constants in `app/build.gradle.kts`; the patch segment is the same commit count as `versionCode` and climbs across the whole repo history (doesn't reset per release).
- To cut a new `MAJOR`/`MINOR`: edit the constants directly in `app/build.gradle.kts`.
- Non-release build types append a `versionNameSuffix`: `-debug` or `-profiling` (e.g. `0.1.1234-profiling`); release has none. The Debug tab's hub shows version name/code and the short commit SHA (`BuildConfig.GIT_SHORT_SHA`) under its title while the app bar is expanded.

## Signing

- `app/build.gradle.kts` `signingConfigs.release` reads `storeFile`/`storePassword`/`keyAlias`/`keyPassword` via a `releaseSigningProperty()` helper: Gradle `-P` flags first (what Bitrise passes), falling back to `local.properties` (what local dev uses, same pattern as `GOOGLE_WEB_CLIENT_ID`). No repo-committed defaults either way — `assembleRelease` without either source set will fail by design.
- Debug builds use the default debug keystore (unchanged, no setup needed).
- **If the release keystore is ever regenerated**, its new SHA-1 must be re-registered in the Firebase console (Project settings → Your apps), or Google Sign-In (`feature:auth`) breaks on release-signed builds.

## Debug/release package split

- `buildTypes.debug` sets `applicationIdSuffix = ".debug"`, so debug installs as `com.rossomak.flashcards.debug` alongside a release install of `com.rossomak.flashcards` on the same device — same signing-cert mismatch that would otherwise block co-installing them is sidestepped by using distinct package names instead.
- This requires **two Firebase Android apps** under the one Firebase project: `com.rossomak.flashcards` (release, pre-existing) and `com.rossomak.flashcards.debug` (added for this). Each needs its own SHA-1 (and SHA-256 only if Dynamic Links/App Check are ever added) registered against it in the Firebase console.
- Both apps' config lives in the single `google-services.json` (keyed internally by `package_name`) — one file, no per-build-type file swapping needed.
- `app/src/debug/res/values/strings.xml` overrides `app_name` to "Flashcards Debug" so the two are visually distinguishable on-device too.

## Profiling build type

`buildTypes.profiling` exists to judge real-world performance without losing developer tools. It is initialized from release (R8 shrinking + optimization, not debuggable, so ART honors baseline profiles) with these differences:

- **Same package as debug** (`.debug` suffix) and **debug signing**, so it installs over a debug build and reuses the debug Firebase app entry and its registered SHA-1 — no extra Firebase app, no `google-services.json` change. Launcher label "Flashcards Profiling" (`app/src/profiling/res`).
- **No obfuscation** (`app/proguard-rules-profiling.pro`: `-dontobfuscate`) so stack traces and traces stay readable.
- **Profileable by shell** (`app/src/profiling/AndroidManifest.xml`) so Android Studio's profiler and Perfetto attach.
- **Logging on** (`BuildConfig.LOGGING_ENABLED`: true for debug and profiling, false for release).
- **Debug hub included**: `:feature:debug` via `profilingImplementation`, and the Debug-tab wiring in `app/src/debug/java` is added to the profiling source set. LeakCanary, Compose UI tooling and Showkase stay debug-only.
- Library modules have no profiling variant; `matchingFallbacks` resolves them to release.
- Not built by Bitrise; ship one with the WIP script below (`--variant profiling`).

Baseline profiles: `androidx.profileinstaller` is an explicit `:app` dependency, so the library-shipped profiles merged into `assets/dexopt/baseline.prof` are installed for sideloaded builds too (Firebase App Distribution, adb), not only Play installs. ART compiles them at the next background dexopt; to judge a fresh install immediately, force it:

```
adb shell cmd package compile -f -m speed-profile com.rossomak.flashcards.debug
```

## R8 keep rules

Release and profiling are minified. Firestore's reflection-based `toObject()` maps documents onto `:core:data`'s `core.data.model` classes, so `core/data/consumer-rules.pro` keeps that package's constructors and members — without it, reads silently yield default-valued objects. kotlinx-serialization and Hilt rely on their bundled rules.

## Local WIP distribution (dev-only, outside Bitrise)

Separate from everything above — no Bitrise workflow, no git push required.
Lets a dev build the *current, possibly-uncommitted* working tree and ship
it straight to a device for a mid-feature progress check. No PR, no
review, no static analysis; just "does it compile, ship it."

- **Script**: `scripts/distribute-wip.sh [--variant debug|profiling]`
  (default `debug`). Standalone-runnable (bare terminal, cron, or the skill
  below — same behavior). Preflight (firebase CLI + service-account JSON
  present) → `./gradlew compile<Variant>Kotlin` (fail fast before wasting
  time on a full build) → `./gradlew assemble<Variant>` →
  `firebase appdistribution:distribute` to the `wip-debug` or
  `wip-profiling` tester group. Release notes name the variant alongside the
  timestamp and short commit hash. All build/upload output goes to stdout
  (no separate logfile, no status file); the last line is always one
  plain-English outcome (`SUCCESS: ...` / `FAILED: <stage> — see output
  above`) that a caller reads off the tail of that same output.
- **Skill**: `.claude/skills/distribute-wip/SKILL.md` (untracked —
  see note below) — thin wrapper, no business logic: runs the script via
  `Bash(run_in_background: true)` so the calling session is never blocked
  on the multi-minute Gradle build, then tails the background output for
  the final `SUCCESS:`/`FAILED:` line and relays it verbatim via
  `PushNotification` + in-session.
- Both variants use the **same** debug app id / debug signing /
  `.debug`-suffixed package as `deploy-internal` above, and the same
  `firebase-app-distribution-service-account.json` — but their own tester
  groups (`wip-debug` / `wip-profiling`, distinct from `internal-debug`) so ad-hoc WIP noise
  never mixes with "latest clean push to main."
- WIP snapshots get no versioning of their own — the distinguishing marker
  between them is the Firebase release notes (timestamp + short commit
  hash); the build type shows in the `versionNameSuffix` and on the Debug
  tab's hub.
- Install path: Firebase's tester client, **Firebase App Tester** (not on
  Play Store — sideloaded via the tester-invite link itself). One-time
  per device; subsequent `wip-debug` uploads notify inside that same app.
- Uploads always run as the service account, whatever the local login
  state: the script hands the firebase CLI a throwaway config dir, so a
  personal `firebase login` (which the CLI would otherwise prefer) and a
  `FIREBASE_TOKEN` are both ignored for the upload.
- One-time setup: `firebase-tools` CLI; a personal `firebase login` is only
  needed for managing tester groups, not for uploading. Create the tester
  groups with
  `firebase appdistribution:groups:create "wip-debug" wip-debug` and
  `firebase appdistribution:groups:create "wip-profiling" wip-profiling`
  (`firebase-tools` ≥15.29).
- The skill file above (`.claude/skills/distribute-wip/SKILL.md`) is
  local-only by design — `.claude/` is gitignored repo-wide, so it never
  reaches git and won't exist for a fresh clone. The script itself is
  fully standalone and doesn't need it; a new dev who wants the skill too
  copies that one file by hand (e.g. from another dev, or recreates it
  from this doc).

## GitHub integration

- Bitrise GitHub App installed on `Haydart/Flashcards`.
- Branch protection on `develop` and `main`: `pr-check` status check required before merge.
