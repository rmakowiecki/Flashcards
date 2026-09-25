import org.gradle.api.artifacts.VersionCatalogsExtension

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
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("mockk").get())
    add("testImplementation", libs.findLibrary("turbine").get())
    add("testImplementation", libs.findLibrary("kotest-assertions-core").get())
    add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    add("testImplementation", testFixtures(project(":core:domain")))
}
