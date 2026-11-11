# CI/CD via Bitrise, with a develop/main/release-branch flow

> **Superseded in part by [ADR-0077](0077-release-branches-tags-and-versions.md):** the roles of `main` and `release/*`, the `deploy-internal` workflow and the `versionName` scheme below are replaced there. Bitrise as the provider, the `pr-check` gate, release signing and the commit count as `versionCode` still stand as decided here.

## Decision

Bitrise is the CI/CD provider, connected to `rmakowiecki/Flashcards` via the Bitrise GitHub App. Scope was first the Android app only (`app` + `core:*` + `feature:*`); a later `backend-check` workflow tests `functions/` (Firebase Cloud Functions, Node) and the Firestore rules, which still deploy separately.

Branch roles:

- **`feature/*` → PR → `develop`**: gated by a required GitHub status check running `./gradlew staticAnalysis && ./gradlew test`. No emulator/instrumented-test step — none exist yet (`androidTest` source sets are empty across all modules). Branch protection requires the PR branch to be up to date with `develop` before merge, and admin bypass is disabled — so the exact code validated by this check is always what lands on `develop`.
- **`main` and `release/*`**: their roles, and how a release moves between them, are decided in [ADR-0077](0077-release-branches-tags-and-versions.md). Pull requests into them pass the same required status check and protection as above.

Release signing uses a newly generated keystore (the project had no release `signingConfig` before this), stored in Bitrise's encrypted file storage — never committed to the repo. Firebase App Distribution auth uses a dedicated Firebase service account scoped to App Distribution only, separate from the broader service account `functions/` uses.

`versionCode` = `git rev-list --count HEAD` (monotonic, always increases, needs no tags). `versionName` is decided in [ADR-0077](0077-release-branches-tags-and-versions.md).

## Context

The project had no CI at all. `build.gradle.kts` already anticipated this: a `staticAnalysis` aggregate task exists with the comment "CI later just calls `./gradlew staticAnalysis`," deliberately kept out of the default `check` lifecycle to keep local test runs fast. Day-to-day work merges into `develop`; `main` is a staging branch for internal-tester-quality builds; a dedicated release branch for Play Store submission doesn't exist yet.

The app already depends on Firebase (`firebase-auth`, Firestore) and has a `firebase-service-account.json` used by `functions/` — but that credential is scoped for Cloud Functions deploys and is deliberately not reused for App Distribution, to keep CI's Firebase permissions minimal.

## Alternatives considered

**Distribute to Firebase App Distribution from every `develop` merge, not just `main`** — rejected. Would give testers earlier visibility but at the cost of Bitrise minutes and Firebase Distribution noise on every integration merge; `develop` is treated as a staging/integration branch, not a tester-facing one.

**`versionName` derived from `git describe` against release tags** — rejected for now. No git tags exist in the repo's history, and adopting tag discipline was judged unnecessary complexity while the project is still pre-launch. Revisit once `release/*` branches and Play Store are in play, where semantic versioning matters more to end users.

**Reuse an existing keystore** — moot; none existed. A fresh keystore was generated instead of retrofitting one.

**Distribute straight to Play Store instead of Firebase App Distribution** — rejected for now. No Play Console listing exists yet; Firebase App Distribution has no review wait and fits the current internal-tester phase. Play Store is staged in as a `release/*`-triggered addition later without reworking the `main` → Firebase path.

## Consequences

- `app/build.gradle.kts` needs a net-new `signingConfigs.release` block reading store/key credentials from Gradle properties (`-P` flags backed by Bitrise secrets), plus the `versionCode`/`versionName` computation described above.
- The release keystore's SHA-1 must be registered in the Firebase console (Project settings → Your apps) — otherwise Google Sign-In (`feature:auth`) breaks on release-signed builds even though debug builds keep working, since debug and release keystores have different fingerprints.
- Google Play signs the published app with its own app-signing key (Play App Signing), so the release keystore is the upload key: if it is lost, Play support can reset it to a new one. Back it up outside Bitrise (password manager) anyway, since a reset takes days and blocks every upload meanwhile.
- GitHub branch protection on `develop` and `main`, and later `release/*` (ADR-0077), requires Bitrise's status check to pass before merge is allowed.
- Operational detail — exact workflow names, trigger config, secrets list, and how-to steps for cutting a release or rotating the keystore — lives in [docs/ci-cd.md](../ci-cd.md), kept separate from this ADR since it will change as the Play Store step is added, whereas this decision record won't.
- A local, dev-only distribution path (`scripts/distribute-wip.sh`) was added later to ship an uncommitted working tree straight to a device for progress checks, bypassing Bitrise entirely — deliberately outside this ADR's scope (no git ref, no PR, no static analysis). It reuses the debug signing/app id decided here but uploads to its own Firebase App Distribution tester groups (`wip-debug`, `wip-profiling`). See [docs/ci-cd.md](../ci-cd.md) for details.
