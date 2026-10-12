import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// App-agnostic Kotlin Multiplatform libraries under `lib/` (Android + desktop JVM). They never depend
// on a Flashcards module; Konsist enforces it. Each module sets its own `android { namespace }` and
// its own dependencies; everything shared lives here.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

pluginManager.apply("android-compose")
pluginManager.apply("android-quality")
// The multiplatform Android target gets Lint only through the standalone Lint plugin.
pluginManager.apply("com.android.lint")

private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinMultiplatformExtension> {
    // Every public declaration states its visibility and type, so what other modules can depend on is
    // always a decision rather than a default.
    explicitApi()

    (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    sourceSets.getByName("commonTest").dependencies {
        implementation(kotlin("test"))
        implementation(libs.findLibrary("kotest-assertions-core").get())
    }
}

// detekt's default sources are the Android/JVM `src/main` layout; point it at the multiplatform one.
extensions.configure<DetektExtension> {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/androidMain/kotlin",
        "src/jvmMain/kotlin",
    )
}
