import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The desktop host of the network-graph playground: the only module that applies the Compose
// Multiplatform Gradle plugin, which the lib modules don't need. Run it with
// `./gradlew :lib:networkgraph-playground:run`.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.jetbrains.compose)
    id("android-quality")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":lib:networkgraph-tuner"))
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
}

compose.desktop {
    application {
        mainClass = "com.rossomak.networkgraph.playground.MainKt"
    }
}
