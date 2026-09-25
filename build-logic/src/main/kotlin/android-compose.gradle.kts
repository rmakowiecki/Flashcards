import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

// Shared Compose compiler setup for every Compose-dependent module (app, core-android, feature).
//
// Stability: `config/compose/stability-config.conf` marks :core:domain models and read-only
// collections as stable, so composables taking them can skip.
//
// Reports and metrics are opt-in, so normal builds are unaffected:
//   ./gradlew :feature:browse:compileReleaseKotlin --rerun -PcomposeCompilerReports=true
// writes `<module>-classes.txt` and `<module>-composables.txt` to `<module>/build/compose_compiler/`
// and `<module>-module.json` metrics to a per-variant subdirectory. Read them from a release-like
// variant; debug builds compile with live-literal instrumentation and report differently. Keep
// `--rerun` (or clean first): incremental compilation only reports the files it recompiled.
pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

val composeCompilerReportsEnabled = providers.gradleProperty("composeCompilerReports")
    .map { it.toBoolean() }
    .getOrElse(false)

extensions.configure<ComposeCompilerGradlePluginExtension> {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability-config.conf"))
    if (composeCompilerReportsEnabled) {
        reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    }
}
