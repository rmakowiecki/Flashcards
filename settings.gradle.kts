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
