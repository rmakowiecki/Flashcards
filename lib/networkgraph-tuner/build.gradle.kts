plugins {
    id("kmp-library")
}

kotlin {
    android {
        namespace = "com.rossomak.networkgraph.tuner"
    }

    sourceSets {
        commonMain.dependencies {
            // One dependency for consumers: the tuner brings the engine it edits.
            api(project(":lib:networkgraph"))
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.core)
            implementation(libs.jetbrains.compose.material.icons.extended)
        }
    }
}
