/*
 * build.gradle.kts (Project-level / Root)
 * ----------------------------------------
 * This is the TOP-LEVEL build file. It declares the Gradle plugins used
 * across the entire project but does NOT apply them here — each module
 * (like :app) applies the ones it needs.
 *
 * "alias(...)" references come from the version catalog (libs.versions.toml)
 * but since we're keeping things simple, we declare plugins directly.
 */

plugins {
    // 'id' loads a plugin; 'version' pins it; 'apply false' means
    // "make it available but don't activate it in THIS file"
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false
}
