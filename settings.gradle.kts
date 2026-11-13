pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "darts-game"
include(":app")
include(":core")
include(":vision")

// BullTrack is vendored as a pinned git submodule (AGPL-3.0-or-later). Only its
// standalone Android library module is used; the React Native app is not.
include(":bulltrack-core")
project(":bulltrack-core").projectDir = file("third_party/BullTrack/bulltrack-core")
