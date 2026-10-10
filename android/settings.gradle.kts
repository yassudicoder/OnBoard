// settings.gradle.kts: the first file Gradle reads. It says WHERE to download things from
// and WHICH modules belong to this project. (Build logic for the app lives in app/build.gradle.kts.)

pluginManagement {
    // Where Gradle looks for *plugins* (for example the Android Gradle Plugin, "AGP").
    repositories {
        google {
            // Only ask Google's repository for Google/Android artifacts. Every other plugin
            // comes from Maven Central or the Gradle Plugin Portal. This keeps builds faster.
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

dependencyResolutionManagement {
    // Where Gradle looks for *libraries* (AppCompat, Material, Navigation, JUnit, ...).
    // FAIL_ON_PROJECT_REPOS = repositories may ONLY be declared here, never inside a module,
    // so every module downloads from the same, known places.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SafarSaathi"

// One module for now: the Android app in the app/ folder.
include(":app")
