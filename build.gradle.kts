import com.diffplug.gradle.spotless.SpotlessExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.spotless)
}

// The root project itself isn't a subproject, so the per-module `android-quality` convention
// plugin never touches its own build.gradle.kts/settings.gradle.kts — cover those here directly.
extensions.configure<SpotlessExtension> {
    kotlinGradle {
        target("build.gradle.kts", "settings.gradle.kts")
        ktlint()
    }
}

// Aggregate entry points for static analysis. Not wired into `check` (kept fast for tests).
// CI later just calls `./gradlew staticAnalysis`.
val staticAnalysis = tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs Spotless, detekt, Konsist and Android Lint across all modules."
}
val formatCode = tasks.register("formatCode") {
    group = "formatting"
    description = "Applies Spotless (ktlint) formatting across all modules."
}

gradle.projectsEvaluated {
    staticAnalysis.configure {
        dependsOn(tasks.matching { task -> task.name == "spotlessCheck" })
        dependsOn(subprojects.flatMap { it.tasks.matching { task -> task.name == "spotlessCheck" } })
        dependsOn(subprojects.flatMap { it.tasks.matching { task -> task.name == "detekt" } })
        dependsOn(subprojects.flatMap { it.tasks.matching { task -> task.name == "lint" } })
        dependsOn(":konsist:test")
    }
    formatCode.configure {
        dependsOn(tasks.matching { task -> task.name == "spotlessApply" })
        dependsOn(subprojects.flatMap { it.tasks.matching { task -> task.name == "spotlessApply" } })
    }
}
