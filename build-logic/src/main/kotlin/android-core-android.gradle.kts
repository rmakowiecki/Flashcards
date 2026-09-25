plugins {
    id("com.android.library")
}

pluginManager.apply("android-compose")
pluginManager.apply("com.google.devtools.ksp")
pluginManager.apply("com.google.dagger.hilt.android")
pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
pluginManager.apply("android-quality")

android {
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}
