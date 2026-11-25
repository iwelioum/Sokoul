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
// The private Sokoul server (Ktor), replacing API/ lot by lot: openspec/changes/sokoul-server.
include(":server")
// The TV app needs the Android SDK: tools/build-tv.sh sets ANDROID_HOME. The Windows build never does.
if (System.getenv("ANDROID_HOME") != null) include(":tv")
