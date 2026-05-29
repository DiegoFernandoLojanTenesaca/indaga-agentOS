plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.indagalab.agentos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.indagalab.agentos"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase0"

        // Chaquopy: limit to the ABIs we ship. P40 Lite is arm64-v8a.
        // Add "armeabi-v7a" later for very old phones (bigger APK).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// --- Python runtime (Chaquopy) ---
chaquopy {
    defaultConfig {
        version = "3.13"
        // Host interpreter Chaquopy uses to compile bytecode + run pip.
        // Must match major.minor of `version`. /usr/bin/python3.13 exists here.
        buildPython("/usr/bin/python3.13")
        pip {
            // Validates the pip pipeline; jarvis.py (Phase 0) uses only stdlib,
            // but Phase 1 will lean on requests.
            install("requests")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    debugImplementation(libs.androidx.ui.tooling)
}
