/*
 * settings.gradle.kts
 * -------------------
 * This is the SETTINGS file for the Gradle build system.
 * It tells Gradle:
 *   1. WHERE to find plugins (pluginManagement)
 *   2. WHERE to find libraries (dependencyResolutionManagement)
 *   3. WHICH modules make up this project (include)
 *
 * Kotlin DSL (.kts) means we write Gradle config in Kotlin instead of Groovy.
 */

pluginManagement {
    // "repositories" = places Gradle looks to download build plugins
    repositories {
        google()          // Google's Maven repo (Android Gradle Plugin, etc.)
        mavenCentral()    // The main public Maven repository
        gradlePluginPortal() // Gradle's own plugin marketplace
    }
}

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS means: if any module tries to declare its own
    // repositories, the build will FAIL. This enforces a single source of truth here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// The name shown in the IDE and build output
rootProject.name = "SmishGuard"

// Tell Gradle that the "app" folder is a sub-module of this project
include(":app")
