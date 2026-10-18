plugins {
    `kotlin-dsl`
}

dependencies {
    // AGP referenced via the plugins{} block of the convention scripts.
    compileOnly(libs.android.gradlePlugin)
    // Applied programmatically via pluginManager.apply(...) in the convention
    // script bodies, so they must be on the consuming project's runtime classpath.
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    // Compose Multiplatform: Compose resources and the wasmJs browser distribution (kmp-* plugins).
    implementation(libs.compose.multiplatform.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.google.services.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    // Static-analysis plugins applied via pluginManager.apply(...) in android-quality.
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
}
