plugins {
    id("android-core-kotlin")
}

// The XP scoring cases and default configuration shared with the Cloud Functions test suite. Carried
// by the test fixtures, so any module using them can also load the files.
sourceSets.named("testFixtures") {
    resources.srcDir(rootProject.layout.projectDirectory.dir("testdata"))
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javax.inject)
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.kotlinx.serialization.json)
    // Feature modules get these from the android-feature convention plugin; core:domain is a plain
    // JVM module, so its own unit tests declare them here.
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
