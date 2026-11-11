# Branch and release flow

How code moves from a feature branch to users, which branch holds what, and how a release and a hotfix are cut. Pipeline details (workflows, secrets, signing): [ci-cd.md](ci-cd.md).

## Branches

| Branch | Holds | Lifetime |
|---|---|---|
| `main` | Exactly what users run: the last release shipped to production. | Permanent |
| `develop` | Everything merged and accepted, released or not. The base for new work. | Permanent |
| `feature/*`, `fix/*`, `docs/*`, … | One change, branched off `develop` (or off a release branch, for a release fix). | Until its PR merges |
| `release/x.y` | One release being stabilized: cut from `develop`, then only fixes. | From the cut until the release ships |
| `release/x.y.z` | One hotfix: cut from the tag of the release it fixes. | Until the hotfix ships |

Every change reaches `develop`, `main` and `release/*` through a pull request that passes the `pr-check` workflow, up to date with its base. Nothing is pushed to them directly; the one push is creating a release branch. Release tags (`v*`) cannot be deleted or moved once pushed. Every pull request merges with a merge commit, never squash or rebase: the commit count is the `versionCode` (see [Versions](#versions)), and rewriting history would change it.

`main` and `develop` never merge into each other directly. Changes reach `main` only from a release branch, and reach `develop` back from a release branch.

## The flow

```
feature/x ──PR──┐   feature/y ──PR──┐   fix/z (from release) ──PR──┐
                ▼                   ▼                               │
develop  ──●────●───────────●───────●──────────────●────────────────┼───●──── (1.1 work…)
                            │ cut                  ▲ merge-back     │   ▲
                            ▼                      │                ▼   │
release/1.0                 ●──────────────────────●────────────────●───┤
                            │candidate 1           │                │candidate 2 ✅
                            ▼                      │                │  tag v1.0.0
                         Play internal → closed    │                │  promote → production
                                                                    │
                                                                    └──PR──┐
                                                                           ▼
main     ──●───────────────────────────────────────────────────────────────●── (= v1.0.0 in prod)
                                                                           │
                                                               hotfix: release/1.0.1
                                                               from tag v1.0.0, same cycle
```

### 1. Everyday work

Each change gets its own branch off `develop` and a pull request back into `develop`. Builds from `develop` never go to Google Play; check work in progress on a device or through Firebase App Distribution instead.

### 2. Cut a release

When `develop` holds what the next release should contain, create `release/x.y` from it and push the new branch. `develop` already carries the version `x.y.0`, so the release branch needs no version change. Then bump `develop` to the following version (for example `x.(y+1).0`) by pull request. `develop` stays open: new features keep merging into it, and the release branch takes only fixes from now on.

### 3. Stabilize

- Every merge into `release/x.y` produces a **candidate**: a signed release AAB built from the branch head and uploaded to the Play internal track. A candidate that looks good is promoted to the closed track for testers.
- A bug found in testing is fixed on a branch off `release/x.y`, with a pull request into `release/x.y`. Fixup commits stay on the fix branch while its pull request is in review, so only reviewed, accepted changes ever produce a candidate. The merge produces the next candidate.
- After each fix, `release/x.y` is merged into `develop` by pull request, so the fix is never lost from the next release.

Only release branches upload to Google Play. A build from `develop` on a Play track would usually carry a higher `versionCode` than the candidate, and Play gives a tester in both tracks the highest one, so the candidate would never reach them.

### 4. Ship

1. Check the candidate before promoting it:
   - It was built from the release branch's current head. If a fix merged after it, the newer head's candidate must pass closed testing instead. From here on nothing merges into the branch until its pull request into `main` is merged, so `main` receives exactly what shipped.
   - It contains the latest production release: `git merge-base --is-ancestor v<latest> <candidate commit>` succeeds. A candidate built before a hotfix shipped fails this and is never promoted.
2. In Play Console, promote the candidate that passed closed testing to production. It is the same AAB the testers ran, never a rebuild.
3. Tag the commit that candidate was built from with an annotated `vx.y.z` tag, and push the tag. Before opening the release pull request, `git rev-parse release/x.y` must equal `git rev-parse 'vx.y.z^{commit}'`.
4. Open a pull request from `release/x.y` into `main` and merge it. `main` now holds what users run.
5. Delete `release/x.y`.

The tag, not `main`'s tip, marks the shipped commit: the merge into `main` adds a merge commit whose content is identical but whose commit count, and so `versionCode`, is one higher. Rebuild or inspect a release only from its tag.

Builds that only reach the internal or closed track get no tag: their `versionCode` already identifies them.

### 5. Hotfix

A bug in production that cannot wait for the next release is fixed on `release/x.y.z`, branched from the tag of the release it fixes (for example `release/1.0.1` from `v1.0.0`), with the version set to `x.y.z`. It then follows steps 3 and 4: fix pull requests into it, candidates on the tracks, promotion, the `vx.y.z` tag, the pull request into `main`, and merging it back into `develop`.

Once the hotfix ships, it is also merged by pull request into every release branch still stabilizing (for example `release/1.0.1` into `release/1.1`), so the next release cannot bring the bug back. Each such merge produces a new candidate, and only candidates built after it pass the check in step 4.

## What merges where

| From | Into | When |
|---|---|---|
| `feature/*` and other change branches | `develop` | Any time, by pull request |
| `develop` | `release/x.y` | Once, when the release branch is created from it |
| A fix branch | `release/x.y` or `release/x.y.z` | During stabilization, by pull request; each merge is a candidate |
| `release/*` | `develop` | After each fix, by pull request |
| A shipped hotfix `release/x.y.z` | Every release branch still stabilizing | Once the hotfix ships, by pull request; each merge is a candidate |
| `release/*` | `main` | Once, after its candidate is promoted to production and tagged |

## Versions

- **`versionName`** is `MAJOR.MINOR.PATCH`, three hand-edited constants in `app/build.gradle.kts`. `develop` always carries the version of the next release: it is bumped right after each release branch is cut, so a release branch starts at `x.y.0` with no change of its own. A hotfix branch bumps its own patch. Debug and profiling builds append their build type and the commit count (for example `1.0.0-debug.2450`), so installs of the same version can be told apart. The app stays on `0.x` through closed testing and becomes `1.0.0` with the first release to production.
- **`versionCode`** is the commit count of the built commit (`git rev-list --count HEAD`). It climbs with every merge, and a hotfix still outnumbers the release it replaces. A commit always has a higher count than its ancestors, so every promoted candidate, which must contain the latest production release (step 4), outnumbers every build already in production: an upgrade always sees a higher code.
- **A duplicate `versionCode`** is possible but rare: two release branches open at once (a hotfix while the next release is stabilizing) could reach the same commit count. Play rejects the upload with a duplicate version code error; add a commit to the branch and rebuild.

## Not automated yet

Candidates are built locally from the release branch head and uploaded to Play by hand. A Bitrise workflow on every push to `release/*` that builds the AAB and uploads it to the internal track is planned before the first hotfix or the release after 1.0.0 ([ADR-0077](adr/0077-release-branches-tags-and-versions.md)). Promotion and tagging stay manual.
