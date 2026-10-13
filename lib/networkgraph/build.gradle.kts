plugins {
    id("kmp-library")
}

kotlin {
    android {
        namespace = "com.rossomak.networkgraph"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.ui.util)
            implementation(libs.jetbrains.compose.animation.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
