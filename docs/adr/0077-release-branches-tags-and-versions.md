# Release branches, tags and versions

> Status: accepted

## Context

Until now `main` was a staging branch nobody merged into, `release/*` was reserved for a Play Store
path that was never built, and closed-testing builds were built from `develop` and uploaded to Google
Play by hand. `versionName` carried the commit count as its patch segment, so a hotfix had no
meaningful number and no tag marked what any user ran. The app is heading for its first production
release and should follow a release process close to a production product's: a stabilization phase
that does not freeze ongoing work, a reviewable record of each release, and a reliable starting point
for a hotfix.

The process for day-to-day use is [docs/release-flow.md](../release-flow.md). This record keeps the
decisions and the alternatives rejected.

## Decision

**`main` holds what users run.** Changes reach it only from a release branch, after that release is in
production. `develop` holds everything accepted, released or not.

**A release is stabilized on its own branch.** `release/x.y` is cut from `develop` and takes only fixes,
each by pull request; `develop` stays open for new work. Each fix is merged back into `develop` by pull
request. A hotfix is `release/x.y.z`, cut from the tag of the release it fixes, and follows the same
cycle.

**Every merge into a release branch is a candidate, and only release branches upload to Google Play.**
A candidate is a signed release AAB uploaded to the internal track and promoted to the closed track for
testers. Fixup commits stay on fix branches until their pull request is accepted, so only reviewed
changes become candidates. Builds from `develop` never go to Play.

**What ships is the tested candidate.** The candidate that passed closed testing is promoted to
production in Play Console, never rebuilt. Its commit gets an annotated `vx.y.z` tag; internal and
closed uploads get none.

**The release reaches `main` through a pull request with a merge commit.** The release branch's pull
request into `main` merges after promotion and tagging, and the branch is then deleted. `main`'s tip
therefore has the shipped content but is not the shipped commit; the tag is. `main` is never merged
back into `develop`.

**Merge commits only, everywhere.** Squash and rebase merges are disabled for the repository: the commit
count is the `versionCode`, and rewriting history would change it.

**`versionCode` stays the commit count; `versionName` is `MAJOR.MINOR.PATCH`.** The three numbers are
hand-edited constants. `develop` carries the next release's version from the moment the previous
release branch is cut, and a hotfix branch bumps its own patch. Debug and profiling builds append their
build type and the commit count (`0.2.0-debug.2450`). The app stays on `0.x` through closed testing and
becomes `1.0.0` with the first production release.

**Candidate builds will be automated on pushes to `release/*`.** A Bitrise workflow will build the AAB
and upload it to the internal track, before the first hotfix or the release after 1.0.0. Until then
candidates are built locally from the release branch head. Promotion and tagging stay manual.

**Protection.** `release/*` requires pull requests and the `pr-check` status check, up to date with its
base, with no force pushes and no admin bypass; creating and deleting the branch is allowed. `v*` tags
cannot be deleted or moved.

The Bitrise `deploy-internal` workflow, which built APKs for Firebase App Distribution on every push to
`main`, is removed: Play tracks carry release builds to testers, and a release APK outside Play fails
App Check (ADR-0076).

## Alternatives considered

- **Release straight from `develop`, with no release branch.** Freezing `develop` while a candidate is
  tested is simpler for one developer, but blocks ongoing work and is not the production process this
  project demonstrates.
- **Build the release only after the release branch merges into `main`.** Makes `main` the single build
  trigger, but testing would follow the merge: `main` would hold untested code, and a bug found in
  closed testing would already be on it.
- **Fast-forward `main` to the shipped commit.** Makes `main`'s tip the tag, but GitHub pull requests
  cannot fast-forward, so each release would be a local push needing a protection exception and would
  leave no release pull request.
- **An `-rc.N` suffix on candidates.** Shipping without the suffix would need a rebuild, so what users get
  would not be what testers ran.
- **A CI build number as `versionCode`.** Never collides across branches, but needs every Play upload to
  come from CI. Revisit if parallel release branches multiply.
- **Fix on `develop` and cherry-pick onto the release branch.** Duplicates commits and loses the ancestry
  that keeps a release branch's fixes provably in `develop`.

## Consequences

- Two release branches open at once (a hotfix while the next release stabilizes) can reach the same
  commit count. Play rejects the duplicate `versionCode` at upload; a new commit on the branch fixes it.
- A build from `develop` on a Play track would usually outnumber the candidate and hide it from testers
  in both tracks, which is why `develop` never uploads to Play.
- Rebuilding or inspecting a release starts from its tag, never from `main`'s tip, whose merge commit
  makes its `versionCode` one higher.
- Each release costs a branch cut, fix pull requests and their merge-backs, the release pull request
  and a version bump on `develop`.
