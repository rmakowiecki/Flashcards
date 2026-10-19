plugins {
    id("kmp-library")
}

// Design tokens shared by the Android app (through :core:ui) and the website (:web): the palette,
// color schemes, brand gradients, semantic colors, spacing, sizes, corner radii, motion and the
// Roboto type scale. Tokens only — components stay in :core:ui.
kotlin {
    android {
        namespace = "com.rossomak.flashcards.core.designsystem"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.ui)
            api(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.animation)
            implementation(libs.jetbrains.compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.rossomak.flashcards.core.designsystem.resources"
}
