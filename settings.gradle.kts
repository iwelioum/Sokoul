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
        google()
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") } }
    }
}

rootProject.name = "SokoulPC"

include(":parser")
include(":core")
include(":extractor")
include(":sources")
include(":app")
// The TV app needs the Android SDK: tools/build-tv.sh sets ANDROID_HOME. The Windows build never does.
if (System.getenv("ANDROID_HOME") != null) include(":tv")
