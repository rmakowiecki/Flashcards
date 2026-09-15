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
include(":app")
include(":core:common")
include(":core:domain")
include(":core:ui")
include(":core:data")
include(":core:voice")
include(":feature:settings")
include(":feature:home")
include(":feature:onboarding")
include(":feature:browse")
include(":feature:study")
include(":feature:auth")
include(":feature:debug")
include(":konsist")
