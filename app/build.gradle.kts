// Sokoul PC: Compose Desktop app (mouse and keyboard), playback through libVLC (vlcj).
import java.time.Duration
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/* - Copie.kt", "**/* - copy.kt")
}

// API keys (sokoul.key.*) come from this checkout's local.properties or an explicit SOKOUL_KEYS_FILE.
// The generated class stays in the build folder: never committed or printed.
val keysDir = layout.buildDirectory.dir("generated/keys")
val generateDesktopKeys by tasks.registering {
    // Build inputs come from this checkout or an explicitly configured external key file.
    val candidates = listOfNotNull(
        System.getenv("SOKOUL_KEYS_FILE")?.let { File(it) },
        rootDir.resolve("local.properties"),
    )
    val props = candidates.firstOrNull { it.isFile } ?: candidates.last()
    inputs.files(listOf(props).filter { it.exists() })
    outputs.dir(keysDir)
    doLast {
        val p = Properties()
        if (props.isFile) props.inputStream().use { p.load(it) }
        val keys = p.stringPropertyNames().filter { it.startsWith("sokoul.key.") }.sorted()
            .associate { it.removePrefix("sokoul.key.") to p.getProperty(it).trim() }
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        val body = keys.entries.joinToString(",\n") { (k, v) -> "        \"${esc(k)}\" to \"${esc(v)}\"" }
        val out = keysDir.get().file("com/sokoul/desktop/data/DesktopKeys.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            "package com.sokoul.desktop.data\n\n" +
                "/** Generated at build time: never commit, never log. */\n" +
                "internal object DesktopKeys {\n    val all: Map<String, String> = mapOf(\n$body\n    )\n}\n",
        )
    }
}
sourceSets["main"].kotlin.srcDir(keysDir)
tasks.named("compileKotlin") { dependsOn(generateDesktopKeys) }

dependencies {
    implementation(project(":extractor"))
    implementation(project(":core"))
    implementation(project(":sources"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.okhttp)
    implementation("com.google.zxing:core:3.5.3")
    // Streaming sites read by the app itself (Coflix…), as core already does.
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.vlcj)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kxml2)
    // Watch party: the Socket.IO server of API/watchpartyAPI.
    implementation(libs.socketio.client)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

// libVLC is supplied explicitly by the build environment and bundled into the distribution.
// Windows: tools/fetch-vlc.ps1 fills app-resources/windows/vlc. macOS: tools/fetch-vlc.sh fills app-resources/macos/vlc.
val isMacHost = System.getProperty("os.name").lowercase().startsWith("mac")
val vlcDir = System.getenv("SOKOUL_VLC_DIR")?.let(::File)
    ?: projectDir.resolve(if (isMacHost) "app-resources/macos/vlc" else "app-resources/windows/vlc")
tasks.withType<JavaExec>().configureEach {
    if (vlcDir.isDirectory) environment("SOKOUL_VLC_DIR", vlcDir.path)
}

// libVLC loads every plugin at startup, so plugins that film playback never uses only slow the launch down.
val prunedVlc = layout.buildDirectory.dir("vlc-pruned")
// macOS: libVLC enters the .app through Compose's app resources (<root>/macos/vlc), before the bundle is signed;
// files added to a finished .app would break its signature.
val macVlcResources = layout.buildDirectory.dir("vlc-app-resources")
val pruneVlc by tasks.registering(Sync::class) {
    from(vlcDir) {
        exclude(
            "plugins/gui/**", "plugins/visualization/**", "plugins/video_splitter/**", "plugins/services_discovery/**",
            "plugins/keystore/**", "plugins/stream_out/**", "plugins/mux/**", "plugins/access_output/**", "plugins/lua/**",
            "plugins/meta_engine/**", "plugins/control/**", "plugins/logger/**",
        )
    }
    into(if (isMacHost) macVlcResources.map { it.dir("macos/vlc") } else prunedVlc.map { it.dir("vlc") })
}
val bundleVlcResources by tasks.registering(Sync::class) {
    dependsOn(pruneVlc)
    from(prunedVlc.map { it.dir("vlc") })
    into(layout.buildDirectory.dir("compose/binaries/main/app/Sokoul PC/app/resources/vlc"))
}
if (isMacHost) {
    tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(pruneVlc) }
} else {
    tasks.matching { it.name == "createDistributable" }.configureEach { finalizedBy(bundleVlcResources) }
}

compose.desktop {
    application {
        mainClass = "com.sokoul.pc.MainKt"
        jvmArgs += listOf("-Xmx1g", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            packageName = "Sokoul PC"
            packageVersion = "1.0.0"
            vendor = "Sokoul"
            // libVLC is bundled into the distribution by bundleVlcResources (Windows) or the app resources (macOS).
            modules("java.instrument", "java.sql", "java.naming", "jdk.crypto.ec", "jdk.unsupported", "jdk.localedata", "jdk.httpserver")
            if (isMacHost) appResourcesRootDir.set(macVlcResources)
            windows {
                iconFile.set(project.layout.projectDirectory.file("icon/sokoul.ico"))
            }
            macOS {
                iconFile.set(project.layout.projectDirectory.file("icon/sokoul.icns"))
                bundleID = "com.sokoul.pc"
                dockName = "Sokoul"
            }
        }
    }
}

// Development tools (screen renders, audits, trials, checks) live in their own source set: they never ship in the app,
// and the TV build never sees them. They can use the main code, internals included.
val dev: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets["main"].runtimeClasspath
}
kotlin.target.compilations.getByName("dev").associateWith(kotlin.target.compilations.getByName("main"))

tasks.register<JavaExec>("sagaHarvest") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.SagaHarvestKt")
    args(rootDir.resolve("app/src/main/resources/all_sagas.json").path, "250")
}

tasks.register<JavaExec>("sagaCategorize") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.SagaCategorizeKt")
    args(rootDir.resolve("app/src/main/resources/all_sagas.json").path)
}

tasks.register<JavaExec>("rdTrial") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.RdTrialKt")
    args((project.findProperty("rdArgs") as String? ?: "").split(" "))
}

tasks.register<JavaExec>("sourceAudit") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.SourceAuditKt")
    args((project.findProperty("titles") as String? ?: "").split(" ", ",").filter { it.isNotBlank() })
}

tasks.register<JavaExec>("logoAudit") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.LogoAuditKt")
    args(rootDir.resolve("renders/audit/logos.html").path, rootDir.resolve("renders/audit/urls.txt").path)
}

tasks.register<JavaExec>("trailerQuality") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.TrailerQualityKt")
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.register<JavaExec>("trailerTrial") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.TrailerTrialKt")
    jvmArgs("-Dfile.encoding=UTF-8")
}


tasks.register<JavaExec>("renderScreens") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.RenderScreensKt")
    args(providers.gradleProperty("renderOutput").orElse(rootDir.resolve("renders").path).get())
    providers.gradleProperty("renderMode").orNull?.let { args(it) }
    // -PrenderLanguage=ar renders the home and title screens in that interface language.
    providers.gradleProperty("renderLanguage").orNull?.let { systemProperty("sokoul.renderLanguage", it) }
}

tasks.register<JavaExec>("setupCheck") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.SetupCheckKt")
    args(rootDir.path)
}

tasks.register<JavaExec>("validatePrivateKeys") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.PrivateKeyCheckKt")
    args(providers.gradleProperty("privateKeyInput").orElse("").get(),
        providers.gradleProperty("privateKeyOutput").orElse("").get(), rootDir.path)
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(25))
    // Tests get their own empty data folder (AppDirs reads sokoul.data), never the user's %APPDATA%\SokoulPC: playback
    // tests wrote their fake starts into the real start log, and Settings tests then hung on the longer page (2026-11-25).
    val testData = layout.buildDirectory.dir("test-data/$name/SokoulPC")
    systemProperty("sokoul.data", testData.get().asFile.absolutePath)
    doFirst { testData.get().asFile.let { it.deleteRecursively(); it.mkdirs() } }
    testLogging {
        events("started", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

tasks.register<JavaExec>("hostsCompare") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.HostsCompareKt")
    jvmArgs("-Dfile.encoding=UTF-8")
    providers.gradleProperty("failedEmbeds").orNull?.let { systemProperty("sokoul.failedEmbeds", it) }
}

// Task 7.2: a copy of a real database round-trips through a Sokoul server (see SyncRoundTrip.kt). Never the live file.
tasks.register<JavaExec>("syncRoundTrip") {
    classpath = dev.runtimeClasspath
    mainClass.set("com.sokoul.pc.dev.SyncRoundTripKt")
    jvmArgs("-Dfile.encoding=UTF-8")
    args((project.findProperty("roundTrip") as String? ?: "").split("|").filter { it.isNotEmpty() })
}
