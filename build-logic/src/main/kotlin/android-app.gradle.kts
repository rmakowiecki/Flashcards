plugins {
    id("com.android.application")
}

// AGP-dependent plugins applied in the body so generatePrecompiledScriptPluginAccessors
// does not probe them against a synthetic project without AGP's extension.
// AGP 9 built-in Kotlin handles kotlin.android — no explicit apply needed.
pluginManager.apply("android-compose")
pluginManager.apply("com.google.devtools.ksp")
pluginManager.apply("com.google.dagger.hilt.android")
pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
pluginManager.apply("com.google.gms.google-services")
pluginManager.apply("android-quality")

android {
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}
