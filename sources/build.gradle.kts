// Sokoul's own web-source scrapers (Coflix, French-Stream, PurStream, VoirDrama, Anime-Sama) with a typed result model,
// shared by the apps and the future Kotlin server. Plain JVM, no Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/* - Copie.kt", "**/* - copy.kt")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // AudioLanguage, ReleaseName, TmdbClient.NamesAndImages and SingleFlight are part of this module's API.
    api(project(":core"))
    // PublicNetwork: the catalogue DNS refuses private answers with the extractor's own rule.
    implementation(project(":extractor"))
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
