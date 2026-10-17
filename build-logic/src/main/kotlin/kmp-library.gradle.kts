import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Kotlin Multiplatform library shared by the Android app and the wasmJs website (:web). Compose
// comes from Compose Multiplatform, whose artifacts resolve to the androidx ones on Android, so a
// module built with this plugin drops into the Android graph like any other library.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// Compose resources (fonts, drawables) shared across targets as `Res`.
pluginManager.apply("org.jetbrains.compose")
pluginManager.apply("android-compose")
pluginManager.apply("android-quality")

kotlin {
    android {
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        // Packs the module's composeResources into the AAR so Android resolves `Res` too.
        androidResources { enable = true }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
}

// detekt's default source set is src/main; KMP sources live under src/<sourceSet>Main.
extensions.configure<DetektExtension> {
    source.setFrom(files("src"))
}
