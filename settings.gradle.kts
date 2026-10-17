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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Flashcards"

// Morpheus is built from source from the `morpheus` git submodule. Once it is published to Maven Central,
// replace this block with versioned `com.rossomak:*` dependencies and drop the submodule.
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
include(":konsist")
