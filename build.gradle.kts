plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}

// Optional: LUMEN_BUILD_DIR moves all build output out of the source tree (useful when the
// source sits in a synced folder that locks files mid-build). Unset = the usual build/ dirs.
System.getenv("LUMEN_BUILD_DIR")?.takeIf { it.isNotBlank() }?.let { root ->
    allprojects {
        layout.buildDirectory.set(File(root, if (project == rootProject) "root" else project.name))
    }
}
