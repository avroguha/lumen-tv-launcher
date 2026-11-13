import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: a keystore.properties OUTSIDE the repo (LUMEN_KEYSTORE_PROPS, default
// ~/.tvos/keystore.properties) with storeFile, storePassword, keyAlias, keyPassword. Without
// one the release build is signed with the debug key, which is fine for your own TV.
// Keep your key: Android refuses to upgrade an app signed with a different one.
val keystoreProps = Properties().apply {
    val path = System.getenv("LUMEN_KEYSTORE_PROPS") ?: File(System.getProperty("user.home"), ".tvos/keystore.properties").path
    val f = File(path)
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "org.idktech.lumen"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.idktech.lumen"
        minSdk = 28
        targetSdk = 35
        versionCode = 12
        versionName = "0.6.3"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = File(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // lintVitalReportRelease dies on this Windows setup with an IOException on a path it computes;
    // it only writes a report, so the release build does not depend on it.
    lint { checkReleaseBuilds = false }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    // real backdrop blur for the glass surfaces (RenderEffect on Android 12+)
    implementation("dev.chrisbanes.haze:haze:1.5.4")
    // poster / backdrop loading with a bounded memory cache (the TV has 2 GB)
    implementation("io.coil-kt:coil-compose:2.7.0")
}
