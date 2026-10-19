import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("kmp-web-app")
}

// The hosted website (Firebase Hosting): home, privacy policy and account deletion pages, drawn
// with Compose Multiplatform over the shared :core:designsystem tokens. Build the deployable site
// with `./gradlew :web:wasmJsBrowserDistribution`; preview it with `wasmJsBrowserDevelopmentRun`.
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // Names the emitted script `web.js`, which index.html loads.
        outputModuleName.set("web")
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core:designsystem"))
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.kotlinx.browser)
        }
        wasmJsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            // Downloads its own Chromium, so the tests need no browser installed on the machine.
            implementation(devNpm("puppeteer", libs.versions.puppeteer.get()))
        }
    }
}

compose.resources {
    // The default ("auto") generates `Res` only when commonMain depends on the resources library;
    // this module's code and resources all live in wasmJsMain, its only target.
    generateResClass = always
    packageOfResClass = "com.rossomak.flashcards.web.resources"
}
