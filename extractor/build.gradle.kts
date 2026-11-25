plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
val quickjsJvmNatives = configurations.create("quickjsJvmNatives")
val quickjsAndroidNatives = configurations.create("quickjsAndroidNatives")
dependencies {
    quickjsJvmNatives("io.github.dokar3:quickjs-kt-jvm:${libs.versions.quickjs.get()}@jar") { isTransitive = false }
    quickjsAndroidNatives("io.github.dokar3:quickjs-kt-android:${libs.versions.quickjs.get()}@aar") { isTransitive = false }
}
sourceSets.main { kotlin.srcDir("src/quickjs/kotlin") }
val extractJvmNatives = tasks.register<Sync>("extractJvmNatives") {
    from({ quickjsJvmNatives.map { zipTree(it) } }) { include("jni/**") }
    into(layout.buildDirectory.dir("generated/quickjsJvm"))
}
val extractAndroidNatives = tasks.register<Sync>("extractAndroidNatives") {
    from({ quickjsAndroidNatives.map { zipTree(it) } }) { include("jni/**"); eachFile { path = path.removePrefix("jni/") }; includeEmptyDirs = false }
    into(layout.buildDirectory.dir("generated/quickjsAndroid"))
}
sourceSets.main { resources.srcDir(extractJvmNatives) }
val generatedCore = layout.buildDirectory.dir("generated/core")
val generateCore = tasks.register<Exec>("generateCore") {
    inputs.files("../extraction/runtime/extractors-core.js", "../extraction/runtime/fsvid-vidzy-quickjs-entry.js", "scripts/build-native-core.mjs", "src/main/resources/sokoul-extract/adapter.js")
    outputs.dir(generatedCore)
    commandLine("node", "scripts/build-native-core.mjs", generatedCore.get().asFile.absolutePath)
}
sourceSets.main { resources.srcDir(generatedCore) }
tasks.processResources { dependsOn(generateCore) }
val generatedFixtures = layout.buildDirectory.dir("generated/fixtures")
val generateFixtures = tasks.register<Exec>("generateFixtures") {
    inputs.files("../extraction/tests/fixtures.mjs", "scripts/build-test-fixtures.mjs")
    outputs.dir(generatedFixtures)
    commandLine("node", "scripts/build-test-fixtures.mjs", generatedFixtures.get().asFile.absolutePath)
}
sourceSets.test { resources.srcDir(generatedFixtures) }
tasks.processTestResources { dependsOn(generateFixtures) }
