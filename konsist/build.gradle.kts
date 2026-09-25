plugins {
    id("android-core-kotlin")
}

// Architecture-rule tests. Konsist scans the whole project via its scope API, so this
// module has no production code — only tests asserting cross-module conventions.
dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnit()
    // Konsist reads every module's Kotlin sources at test time, which Gradle can't see on its own:
    // without these inputs an edit elsewhere leaves this task UP-TO-DATE (or restored from the
    // build cache) and the architecture rules silently pass on stale results.
    inputs
        .files(
            fileTree(rootDir) {
                include("**/*.kt", "**/*.kts")
                exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
            },
        ).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("projectKotlinSources")
}
