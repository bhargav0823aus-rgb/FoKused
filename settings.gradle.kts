pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()        // ML Kit GenAI artifacts live here
        mavenCentral()
    }
}

rootProject.name = "FocusGate"
include(":app")
include(":app")
 