import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Shared static-analysis convention: Spotless (ktlint formatting) + detekt (smells) +
// Android Lint baseline wiring. Applied from the language/module convention plugins so
// every module gets the same tasks. Plugins applied in the body via pluginManager.apply
// to avoid the precompiled-script-plugin accessor probe (same reason as AGP plugins).
pluginManager.apply("com.diffplug.spotless")
pluginManager.apply("io.gitlab.arturbosch.detekt")

// ktlint rule overrides applied on top of the repo-root .editorconfig. `backing-property-naming`
// clashes with the ViewModel `_prefix` idiom for private MutableStateFlow with no public mirror.
// Class and function signature wrapping is intentionally author-controlled. In particular, the
// `class-signature` rule would otherwise collapse multiline data-class constructors.
val ktlintOverrides = mapOf(
    "ktlint_standard_backing-property-naming" to "disabled",
    "ktlint_standard_class-signature" to "disabled",
    "ktlint_standard_function-signature" to "disabled",
    "ktlint_standard_parameter-list-wrapping" to "disabled",
    "ktlint_standard_argument-list-wrapping" to "disabled",
    "ktlint_standard_parameter-wrapping" to "disabled",
    "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
    "ktlint_standard_trailing-comma-on-call-site" to "disabled",
)

extensions.configure<SpotlessExtension> {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(ktlintOverrides)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint().editorConfigOverride(ktlintOverrides)
    }
}

extensions.configure<DetektExtension> {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Per-module baseline; created by `./gradlew detektBaseline`. Missing file is ignored.
    baseline = file("detekt-baseline.xml")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "11"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

// The baseline task is a separate type, so it needs the jvmTarget set independently —
// otherwise detekt derives it from the running JDK (23), which detekt 1.23.x rejects.
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "11"
}

// Android Lint: freeze existing findings via a per-module baseline. No rule re-tuning here.
pluginManager.withPlugin("com.android.library") {
    extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
        lint { baseline = file("lint-baseline.xml") }
    }
}
pluginManager.withPlugin("com.android.application") {
    extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
        lint { baseline = file("lint-baseline.xml") }
    }
}

// Per-module entry point, e.g. `./gradlew :feature:study:staticAnalysis` — faster than the root
// aggregate when only one module changed. Konsist stays whole-repo (its cross-module layer rules
// can't be scoped to one module), and it reruns whenever any module's Kotlin sources change; the
// speedup comes from skipping every other module's Spotless/detekt/Lint.
val moduleStaticAnalysis = tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs Spotless, detekt and (Android modules) Lint for this module, plus Konsist."
    dependsOn("spotlessCheck", "detekt", ":konsist:test")
}
// Lint only exists on Android modules — pure-Kotlin modules (android-core-kotlin) have no such task.
pluginManager.withPlugin("com.android.library") { moduleStaticAnalysis.configure { dependsOn("lint") } }
pluginManager.withPlugin("com.android.application") { moduleStaticAnalysis.configure { dependsOn("lint") } }
