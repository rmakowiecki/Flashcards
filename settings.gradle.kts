pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    // PREFER_SETTINGS, not FAIL_ON_PROJECT_REPOS: the Kotlin wasmJs toolchain (:web) registers its
    // own repositories for Node.js, Yarn and Binaryen on the root project. They are ignored here and
    // served by the matching ivy repositories below instead, so every repository still lives in one
    // place.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen distributions"
            patternLayout { artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "Flashcards"

// Morpheus (the animated graph background and its tuner) lives in its own repository, checked out here
// as the `morpheus` git submodule and built from source as part of this build. Swap this block for a
// repository dependency once Morpheus is published; the `com.rossomak:*` coordinates stay the same.
includeBuild("morpheus") {
    dependencySubstitution {
        substitute(module("com.rossomak:morpheus")).using(project(":morpheus"))
        substitute(module("com.rossomak:morpheus-tuner")).using(project(":morpheus-tuner"))
    }
}

include(":app")
include(":core:common")
include(":core:domain")
include(":core:ui")
include(":core:designsystem")
include(":core:data")
include(":core:voice")
include(":feature:settings")
include(":feature:account")
include(":feature:home")
include(":feature:onboarding")
include(":feature:browse")
include(":feature:study")
include(":feature:auth")
include(":debug-tools")
include(":web")
include(":konsist")
