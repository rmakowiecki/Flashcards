# Crash reporting through Crashlytics

> Status: accepted

## Context

Release builds are obfuscated by R8 and planted no Timber tree, so a crash or a caught error on a
tester's device left no trace. Play Console vitals only see crashes from users who share
diagnostics with Google, with no logs attached.

## Decision

**Firebase Crashlytics, without Google Analytics.** It reuses the existing Firebase project, uploads
R8 mapping files on every obfuscated build, and adds no `AD_ID` permission.

**Collected in every build type.** Debug and profiling builds reach real testers through App
Distribution, so their crashes matter too. The version name suffix and the `git_sha` custom key tell
builds apart on the dashboard.

**`AppLog` feeds it.** `CrashlyticsTree` is planted in every build. Info, warning and error lines
become breadcrumbs; an error with a throwable is also recorded as a non-fatal. Debug and verbose
lines are dropped.

**No user identifier.** Account Deletion cannot delete Crashlytics data, so reports never carry the
uid. Crashlytics deletes reports after 90 days.

**No opt-out.** Reports carry only diagnostics, disclosed in the privacy policy.

**Only in `:app`.** The plugin is applied by the `android-app` convention plugin, and `CrashReporter`,
`DefaultCrashReporter` and `CrashlyticsTree` live in `:app`, the one place that plants Timber trees.
Other modules keep logging through `AppLog` only.

## Rejected alternatives

- **Play Console vitals only.** Opt-in coverage, delayed, no breadcrumbs or non-fatals.
- **Setting the uid on reports.** Would outlive the User after Account Deletion.
- **Collection off in debug builds.** Would lose crashes from App Distribution testers.
