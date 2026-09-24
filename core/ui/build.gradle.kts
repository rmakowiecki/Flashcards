plugins {
    id("android-core-android")
}

android {
    namespace = "com.rossomak.flashcards.core.ui"
}

ksp {
    arg("skipPrivatePreviews", "true")
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    api(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    // api: SharedTransitionScope leaks through the shared-element CompositionLocals below.
    api(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.syntaxmp)
    implementation(libs.androidsvg)
    // Annotation only on the main compile classpath so components can carry
    // @ShowkaseComposable; SOURCE-retained, so no release-runtime cost. The processor and
    // browser UI stay debug-only.
    compileOnly(libs.showkase.annotation)
    debugImplementation(libs.showkase)
    kspDebug(libs.showkase.processor)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
