import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// A Compose Multiplatform browser app compiled to WebAssembly (wasmJs). Builds the static site
// with `wasmJsBrowserDistribution` and serves it locally with `wasmJsBrowserDevelopmentRun`.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

pluginManager.apply("org.jetbrains.compose")
pluginManager.apply("android-compose")
pluginManager.apply("android-quality")

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Unit tests run on Node (below), so CI needs no headless browser.
            testTask { enabled = false }
        }
        nodejs()
        binaries.executable()
    }
}

// detekt's default source set is src/main; KMP sources live under src/<sourceSet>Main.
extensions.configure<DetektExtension> {
    source.setFrom(files("src"))
}
