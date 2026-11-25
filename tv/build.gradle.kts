// Sokoul on Google TV: the screens of :app, with Media3 instead of libVLC. Installs beside the old app, which it never touches.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

val shared = layout.buildDirectory.dir("sharedSrc")
val syncShared = tasks.register<Sync>("syncShared") {
    from("../app/src/main/kotlin")
    into(shared)
    exclude(
        "**/* - Copie.kt", "**/* - copy.kt",
        "com/sokoul/pc/Main.kt", "com/sokoul/pc/Platform.kt", "com/sokoul/pc/ui/VideoFill.kt", "com/sokoul/pc/ui/NativeWindow.kt",
        // Android has no com.sun.net.httpserver: the MCP server is for the PC.
        "com/sokoul/pc/control/McpServer.kt",
        "com/sokoul/desktop/player/DesktopPlayer.kt",
        "com/sokoul/desktop/player/LocalMediaRelay.kt",
        // The PC's debug driver uses AWT. Development tools are in app/src/dev and never reach this build.
        "com/sokoul/pc/dev/DebugDriver.kt",
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/* - Copie.kt", "**/* - copy.kt")
}

// SQLite's native library for Android, taken from the sqlite-jdbc jar and shipped as a native library of the APK. Left as
// a Java resource, sqlite-jdbc unpacks it into the app's data at run time and loads it from there, which Android forbids
// to apps targeting API 29 and later.
val sqliteNatives = layout.buildDirectory.dir("sqliteNatives")
val extractSqliteNatives = tasks.register<Copy>("extractSqliteNatives") {
    val jar = configurations.detachedConfiguration(dependencies.create(libs.sqlite.jdbc.get())).apply { isTransitive = false }
    from({ zipTree(jar.singleFile) }) {
        include("org/sqlite/native/Linux-Android/arm/libsqlitejdbc.so", "org/sqlite/native/Linux-Android/aarch64/libsqlitejdbc.so")
        eachFile { path = (if (path.contains("/aarch64/")) "arm64-v8a/" else "armeabi-v7a/") + name }
        includeEmptyDirs = false
    }
    into(sqliteNatives)
}

// The browser transport some players need (fsvid, Vidzy): curl-impersonate's pinned Android binaries, checked by their
// SHA-256 (tools/prepare-curl-impersonate.py), and libsokoulcurl built against them. Without it those hosts answered
// nothing on the TV ("curl_native_unavailable", 2026-11-18).
val curlRoot = layout.buildDirectory.dir("curlImpersonate")
val curlAbis = listOf("armeabi-v7a", "arm64-v8a")
val prepareCurlImpersonate = tasks.register<Exec>("prepareCurlImpersonate") {
    val root = curlRoot.get().asFile
    outputs.dir(root)
    onlyIf { curlAbis.any { !File(root, "$it/libcurl-impersonate.so").isFile } }
    // macOS and Linux only ship "python3"; Windows' installer names it "python".
    val python = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    commandLine(python, rootProject.file("tools/prepare-curl-impersonate.py").absolutePath, root.absolutePath)
}
// Only the shared library goes into the APK, beside libsokoulcurl; the headers stay for CMake.
val curlNatives = layout.buildDirectory.dir("curlNatives")
val copyCurlNatives = tasks.register<Copy>("copyCurlNatives") {
    dependsOn(prepareCurlImpersonate)
    from(curlRoot) { include("*/libcurl-impersonate.so") }
    into(curlNatives)
}

android {
    namespace = "com.sokoul.tv"
    ndkVersion = "27.1.12297006"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sokoul.tv"
        minSdk = 26
        // SQLite's native library ships in the APK and loads from the app's native folder (TvApp), so a recent target is
        // possible. At 28, Android's compatibility checks for an old target held a lock the app waited on 1,343 times in a
        // few seconds of scrolling (trace on the TV, 2026-11-08).
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        // c++_shared: libcurl-impersonate needs libc++_shared.so, packaged only when the native build asks for it.
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared"; arguments += "-DCURL_IMPERSONATE_ROOT=${curlRoot.get().asFile.absolutePath.replace('\\', '/')}" } }
    }
    // CMake's working files go with the other outputs (build-output/tv), not beside the sources.
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1"; buildStagingDirectory = layout.buildDirectory.dir("cxx").get().asFile } }
    System.getenv("SOKOUL_DEBUG_KEYSTORE")?.let { path ->
        signingConfigs.getByName("debug").storeFile = file(path)
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
            // Installed by hand on the user's own TV.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    sourceSets["main"].apply {
        java.srcDir(shared)
        java.exclude("**/* - Copie.kt", "**/* - copy.kt")
        // The keys class generated from the local configuration, never in the repo.
        java.srcDir(layout.buildDirectory.dir("../app/generated/keys"))
        resources.srcDir("../app/src/main/resources")
        assets.srcDir("../app/src/main/resources")
        res.exclude("**/* - Copie.*", "**/* - copy.*")
        resources.exclude("**/* - Copie.*", "**/* - copy.*")
        assets.exclude("**/* - Copie.*", "**/* - copy.*")
        jniLibs.srcDir(sqliteNatives)
        jniLibs.srcDir(curlNatives)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipe's extractor uses java.time and java.nio beyond what Android 8 has.
        isCoreLibraryDesugaringEnabled = true
    }
    packaging {
        resources {
            excludes += setOf("jni/**", "META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/versions/**", "org/sqlite/native/Windows/**", "org/sqlite/native/Mac/**", "org/sqlite/native/FreeBSD/**", "org/sqlite/native/Linux/**", "org/sqlite/native/Linux-Musl/**", "org/sqlite/native/Linux-Android/**")
        }
        // Unpacked at install into the app's native folder, where sqlite-jdbc loads it by its full path (TvApp).
        jniLibs { useLegacyPackaging = true }
    }
    lint { checkReleaseBuilds = false }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Compose compiler reports, on demand only (-PcomposeReports): which composables can be skipped, which classes are stable.
if (project.hasProperty("composeReports")) {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_reports")
    }
}

tasks.named("preBuild") { dependsOn(syncShared, ":app:generateDesktopKeys", extractSqliteNatives, copyCurlNatives) }
tasks.named("preBuild") { dependsOn(":extractor:extractAndroidNatives") }
android.sourceSets["main"].jniLibs.srcDir(project(":extractor").layout.buildDirectory.dir("generated/quickjsAndroid"))

dependencies {
    implementation(project(":extractor"))
    testImplementation(libs.junit)
    implementation(project(":core")) { exclude(group = "xmlpull") }
    implementation(project(":sources")) { exclude(group = "xmlpull") }
    implementation(platform(libs.androidx.compose.bom))
    // Installs the baseline profiles that Compose ships, also when the APK is installed with adb: without it the app ran
    // interpreted (dexopt "verify") and the Chromecast HD dropped 41 % of frames while browsing (2026-11-22).
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    // Streaming sites read by the app itself (Coflix…), as core already does.
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sqlite.jdbc)
    implementation(libs.tvprovider)
    implementation(libs.kxml2) { exclude(group = "xmlpull") }
    // Android ships org.json itself.
    implementation(libs.socketio.client) { exclude(group = "org.json", module = "json") }
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.ffmpeg)
    implementation(libs.newpipe.extractor)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}
