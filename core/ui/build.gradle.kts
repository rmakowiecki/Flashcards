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
    // api: the theme tokens (spacing, brandColors, typography, …) are part of every component's
    // contract, so feature modules see them through :core:ui without declaring the module.
    api(project(":core:designsystem"))
    api(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    // api: SharedTransitionScope leaks through the shared-element CompositionLocals below.
    api(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.syntaxmp)
    implementation(libs.androidsvg)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.compose.shimmer)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    // Morpheus, included from the `morpheus` submodule as a composite build (see settings.gradle.kts).
    // api: MorpheusGraphSpec and MorpheusGraphMorphState are in BrandGradientBackdrop's public signature.
    api("com.rossomak:morpheus")
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
