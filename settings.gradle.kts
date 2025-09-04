pluginManagement {
    includeBuild("build-convention")
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
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.objectbox") {
                useModule("io.objectbox:objectbox-gradle-plugin:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://androidx.dev/snapshots/builds/13617490/artifacts/repository")
        mavenLocal()
        maven("https://maven.universablockchain.com/")
    }
}

rootProject.name = "rikkahub"
include(":composeApp")
include(":highlight")
include(":ai")
include(":search")
include(":tts")
include(":common")
include(":app:baselineprofile")
