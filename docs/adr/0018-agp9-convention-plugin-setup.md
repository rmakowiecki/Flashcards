# AGP 9 convention plugins: built-in Kotlin, body-applied AGP-dependent plugins, shared classloader

## Decision

The `build-logic/` convention plugins (introduced by ADR-0011) are implemented for **AGP 9.2.1 + Kotlin 2.3.0 + KSP 2.3.9 + Hilt 2.60** with three non-obvious constraints:

1. **Plugins applied in the convention-script body** via `pluginManager.apply("…")`, with only the AGP plugin in the `plugins {}` block.
2. **KSP declared `apply false` in the root `build.gradle.kts`** so Hilt and KSP load from one classloader.
3. **`compileOnly` vs `implementation`** split in `build-logic/build.gradle.kts` for plugin markers.

AGP 9 built-in Kotlin is active (default). No `android.builtInKotlin` or `android.newDsl` overrides are needed.

## Context

ADR-0011 specified `build-logic/` precompiled script plugins but predates AGP 9.2.1 and several dependency version upgrades. The initial Phase 1 implementation worked around KSP 1.x and Hilt 2.58 limitations with `android.builtInKotlin=false` and `android.newDsl=false`. Those constraints were eliminated by upgrading:

- **KSP 2.3.0 → 2.3.9**: KSP 2.3.1 added AGP 9 + built-in Kotlin support, removing the `builtInKotlin=false` requirement.
- **Hilt 2.58 → 2.60**: Hilt 2.59 added AGP 9 new DSL support, removing the `newDsl=false` requirement.

## Rationale — why each constraint remains

**Body-applied plugins.** Gradle's `generatePrecompiledScriptPluginAccessors` task evaluates only the `plugins {}` block of each precompiled script against a throwaway synthetic project, to discover type-safe accessors. AGP-dependent plugins (Hilt, KSP, compose compiler, serialization, google-services) fail in that probe — e.g. Hilt: *"Could not find the Android Gradle Plugin (AGP) base extension."* Applying them in the script **body** with `pluginManager.apply(...)` defers application to real project configuration time, after AGP's extension exists. Only the AGP plugin itself (`com.android.application` / `com.android.library`) stays in `plugins {}`, because the `android {}` type-safe accessor must be generated.

**Root `apply false` for the shared classloader.** With KSP applied only inside a sub-project's convention plugin while Hilt resolved elsewhere, the two plugins land in different classloaders and Gradle fails: *"The KSP plugin was detected to be applied but its task class could not be found … Hilt Gradle Plugin is using a different class loader"* (dagger #3965). Declaring every plugin — KSP included — `apply false` at the root `build.gradle.kts` loads them all from the root buildscript classloader, which sub-projects then reuse.

**`compileOnly` vs `implementation` in `build-logic/build.gradle.kts`.** The AGP marker is `compileOnly` (referenced from the `plugins {}` block at build-logic compile time). Every marker applied programmatically in a script body — compose, serialization, hilt, ksp, google-services — must be `implementation`, so it reaches the **consuming** project's runtime classpath; otherwise `pluginManager.apply("…")` fails with "Plugin not found". The `kotlin-gradlePlugin` marker is `implementation` because it provides the compose and serialization plugin classes.

**`build-logic` catalog + properties wiring.** `build-logic/settings.gradle.kts` must declare its own repositories and re-create the version catalog (`from(files("../gradle/libs.versions.toml"))`), or `libs.*` is unresolved inside build-logic. `build-logic/gradle.properties` only needs `android.useAndroidX=true`.

**Font cert resource must live in `:core:ui`.** *(Obsolete: the app no longer uses downloadable fonts and both `font_certs.xml` files are gone, see [ADR-0063](0063-typography-is-the-stock-m3-scale-over-bundled-roboto.md).)* `Type.kt` references `R.array.com_google_android_gms_fonts_certs`. Library module R classes only include their own resources (not transitive deps). The cert array is declared in `app/src/main/res/values/font_certs.xml` (not bundled in any AAR), so it must be copied to `core/ui/src/main/res/values/font_certs.xml`.

**Hilt plugin requires `hilt-android` dep even with no annotations.** Modules using `android-core-android` must declare `implementation(libs.hilt.android)` + `ksp(libs.hilt.android.compiler)` or Gradle fails at configuration time.

## Alternatives considered

- **All plugins in the `plugins {}` block** — rejected. Fails `generatePrecompiledScriptPluginAccessors` under AGP 9.
- **Binary `Plugin<Project>` convention plugins instead of precompiled `*.gradle.kts`** — viable and sidesteps accessor generation entirely, but a larger rewrite. The body-applied precompiled-script approach keeps ADR-0011's file shape with minimal divergence.
- **Keep KSP 2.3.0 + Hilt 2.58 with `builtInKotlin=false` + `newDsl=false`** — was the Phase 1 starting point, eliminated by version upgrades.

## Consequences

- Convention plugins are clean: `plugins {}` has only the AGP plugin; Hilt/KSP/compose/serialization apply in the body.
- No `android.builtInKotlin` or `android.newDsl` flags in `gradle.properties`.
- Adding a new annotation-processor-based plugin: add its marker as `implementation` in `build-logic/build.gradle.kts`, `apply false` at the root, and `pluginManager.apply` it in the relevant convention script body.
- Builds against ADR-0011's module architecture.
