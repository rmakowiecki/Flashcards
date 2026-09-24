import java.util.Properties

plugins {
    id("android-app")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val googleWebClientId: String =
    localProperties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""

// Bitrise doesn't have local props file, so it will have to call assembleRelease/assembleDebug with the -P option and directly passed creds
fun signingProperty(name: String): String? =
    project.findProperty(name) as String? ?: localProperties.getProperty(name)

val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

val gitShortSha: String = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim()

val versionMajor = 0
val versionMinor = 1

android {
    namespace = "com.rossomak.flashcards"

    signingConfigs {
        getByName("debug") {
            storeFile = signingProperty("DEBUG_STORE_FILE")?.let { file(it) }
            storePassword = signingProperty("DEBUG_STORE_PASSWORD")
            keyAlias = signingProperty("DEBUG_KEY_ALIAS")
            keyPassword = signingProperty("DEBUG_KEY_PASSWORD")
        }
        create("release") {
            storeFile = signingProperty("RELEASE_STORE_FILE")?.let { file(it) }
            storePassword = signingProperty("RELEASE_STORE_PASSWORD")
            keyAlias = signingProperty("RELEASE_KEY_ALIAS")
            keyPassword = signingProperty("RELEASE_KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.rossomak.flashcards"
        versionCode = gitCommitCount
        versionName = "$versionMajor.$versionMinor.$gitCommitCount"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "GIT_SHORT_SHA", "\"$gitShortSha\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("Boolean", "LOGGING_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "LOGGING_ENABLED", "false")
        }
        // Release performance with debug conveniences: R8-optimized and not debuggable, so ART
        // honors baseline profiles, but unobfuscated, logging, carrying the debug hub, and
        // installed over the debug package so its Firebase app and Google Sign-In config apply.
        create("profiling") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-profiling"
            signingConfig = signingConfigs.getByName("debug")
            proguardFile("proguard-rules-profiling.pro")
            buildConfigField("Boolean", "LOGGING_ENABLED", "true")
            matchingFallbacks += "release"
        }
    }
    // The debug-hub wiring (Debug tab + its nav graph) is shared with profiling; release keeps its
    // own no-op stub in src/release.
    sourceSets {
        getByName("profiling") {
            kotlin.directories += "src/debug/java"
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:voice"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:study"))
    implementation(project(":feature:settings"))
    debugImplementation(project(":feature:debug"))
    "profilingImplementation"(project(":feature:debug"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    // work-runtime-ktx for Configuration.Provider, hilt-work for HiltWorkerFactory — :app wires
    // WorkManager to Hilt but defines no @HiltWorker class itself, so no ksp(hilt-compiler) here.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:domain")))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)
}
