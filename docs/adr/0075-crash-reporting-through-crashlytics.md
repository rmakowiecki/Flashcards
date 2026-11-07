# Crash reporting through Crashlytics

> Status: accepted

## Context

Release builds are obfuscated by R8 and planted no Timber tree, so a crash or a caught error on a
tester's device left no trace. Play Console vitals only see crashes from users who share
diagnostics with Google, with no logs attached.

## Decision

**Firebase Crashlytics, without Google Analytics.** It reuses the existing Firebase project, uploads
R8 mapping files on every obfuscated build, and adds no `AD_ID` permission.

**Native crashes too, through the Crashlytics NDK.** ONNX Runtime runs the voice models in native
code, and a fault there, or a JNI abort it triggers, ends in a signal such as `SIGABRT` that kills the
process before any Java exception handler runs. `firebase-crashlytics-ndk` installs a native signal
handler that writes a minidump, uploaded on the next launch. Release and profiling builds upload
native symbols after packaging. ONNX Runtime's JNI library (`libonnxruntime4j_jni.so`) ships
unstripped, so its frames get names; `libonnxruntime.so` and system libraries stay unnamed.

**Debug builds upload no native symbols.** Crashlytics matches symbols by build ID, and the
prebuilt ONNX Runtime libraries are identical in every variant, so a release or profiling upload
already names debug frames. If the app ever builds native code of its own, debug must upload too.

**Collected in every build type.** Debug and profiling builds reach real testers through App
Distribution, so their crashes matter too. The version name suffix and the `git_sha` custom key tell
builds apart on the dashboard.

**`AppLog` feeds it.** `CrashlyticsTree` is planted in every build. Info, warning and error lines
become breadcrumbs; an error with a throwable is also recorded as a non-fatal. Debug and verbose
lines are dropped.

**No user identifier.** Account Deletion cannot delete Crashlytics data, so reports never carry the
uid. Crashlytics keeps reports for 90 days before removing them.

**No opt-out.** Reports carry only diagnostics, disclosed in the privacy policy.

**Only in `:app`.** The plugin is applied by the `android-app` convention plugin, and `CrashReporter`,
`DefaultCrashReporter` and `CrashlyticsTree` live in `:app`, the one place that plants Timber trees.
Other modules keep logging through `AppLog` only.

## Alternatives considered

- **Play Console vitals only.** Opt-in coverage, delayed, no breadcrumbs or non-fatals.
- **Setting the uid on reports.** Would outlive the User after Account Deletion.
- **Collection off in debug builds.** Would lose crashes from App Distribution testers.
- **Java-only reporting.** Misses every crash in ONNX Runtime and other native libraries.

## Consequences

- Release and profiling builds need network: each uploads its R8 mapping file and native symbols
  after packaging, and a failed upload fails the build.
- A Gradle property to skip both uploads is the fix once offline builds or CI need it.
