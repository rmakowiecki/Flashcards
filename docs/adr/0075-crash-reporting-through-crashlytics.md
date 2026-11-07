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
native symbols, so a native frame shows its function name rather than a bare address. The app
builds no native code of its own, but ONNX Runtime's JNI library (`libonnxruntime4j_jni.so`) ships
unstripped, and the JNI layer is where a Java-side mismatch such as an R8-renamed class surfaces.
`libonnxruntime.so` itself ships stripped, and system libraries carry no uploaded symbols, so their
frames stay unnamed. Debug builds upload nothing: Crashlytics matches symbols by a library's build ID,
and the prebuilt ONNX Runtime libraries are byte-identical across variants, so a release or
profiling upload already names debug frames, while a debug upload would rerun on every Run from
Android Studio. If the app ever builds native code of its own, each variant gets its own build ID
and debug must upload too, or App Distribution testers' native crashes stay unnamed. The debug
hub's Native crash row raises `SIGABRT` to check the path end to end.

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
- **Java-only reporting.** Misses every crash in ONNX Runtime and other native libraries.
