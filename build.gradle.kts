plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
}

// The project lives in OneDrive: keep build output (which holds generated keys) out of sync.
val projectBuildRoot = System.getenv("SOKOUL_BUILD_ROOT")?.let(::File)
    ?: File(rootDir, "build-output")
allprojects {
    val name = if (path == ":") "root" else path.removePrefix(":").replace(':', '_')
    layout.buildDirectory.set(File(projectBuildRoot, name))
}
