pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

rootProject.name = "ehentai-extension"

// 1.4 build: every app that understands extensions-lib 1.4 (Mihon, Tachimanga, older forks).
include(":ehentai")
// 1.6 build: suspend API, for Mihon 0.20+ only. Separate package name, so both install side by side.
include(":ehentai-v16")
