import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.services)
}

// Per-developer overrides (e.g. today's dev-tunnel URL, which rotates) live in
// local.properties, which is gitignored -- never committed. CI/release builds pass their
// value instead via -P<propertyKey>=... on the command line. Priority: -P property >
// local.properties > the hardcoded fallback below.
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

fun resolveBackendBaseUrl(propertyKey: String, fallback: String): String {
    return (project.findProperty(propertyKey) as String?)
        ?: localProperties.getProperty(propertyKey)
        ?: fallback
}

android {
    namespace = "com.example"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aistudio.soulai.chat"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // The project's own debug key (not the machine-global ~/.android/debug.keystore
        // AGP falls back to otherwise). Its SHA-1 is what's registered as the Android OAuth
        // client in Firebase for Google Sign-In -- using the wrong debug key here is why
        // Google Sign-In fails while email/password auth still works fine.
        getByName("debug") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
            // Local dev default: the port-forwarded (dev tunnel) URL. Override per-developer
            // via `LOCAL_BACKEND_URL=...` in local.properties, or per-build via
            // `-PLOCAL_BACKEND_URL=...`, without editing this file.
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"${resolveBackendBaseUrl("LOCAL_BACKEND_URL", "https://zvmwmtrx-8787.inc1.devtunnels.ms")}\""
            )
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release builds get no hardcoded default -- the URL must be supplied as a build
            // input, e.g. `-PRELEASE_BACKEND_URL=https://api.example.com` from CI/a release
            // workflow. Falls back to blank (Settings' manual entry still works as an override)
            // rather than failing the build, since there's no release pipeline wired up yet.
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"${resolveBackendBaseUrl("RELEASE_BACKEND_URL", "")}\""
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.0"
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
