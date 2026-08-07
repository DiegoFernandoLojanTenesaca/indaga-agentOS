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
        versionCode = 2
        versionName = "0.2.0"

        // Chaquopy: limit to the ABIs we ship. P40 Lite is arm64-v8a.
        // Add "armeabi-v7a" later for very old phones (bigger APK).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            // Sufijo para que la build de desarrollo CONVIVA con la de producción
            // (firmada con otra llave) en el mismo dispositivo, sin desinstalarla.
            // applicationId final = com.indagalab.agentos.dev
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            // Beta: firmamos el release con la clave debug para que sea instalable
            // por sideload (descarga desde GitHub). Para Play Store/AppGallery habría
            // que generar y usar una clave de firma propia.
            signingConfig = signingConfigs.getByName("debug")
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

    // ggml carga sus backends por ruta con dlopen (GGML_BACKEND_DL=ON). Desde
    // AGP 3.6 las .so viajan comprimidas dentro del APK y lib/arm64 queda
    // VACÍO, así que llama.cpp arrancaba con "no backends are loaded".
    // Esto las extrae al disco al instalar. Cuesta ~20 MB de espacio en el
    // dispositivo; es el precio de tener backends dinámicos por CPU.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

// --- Python runtime (Chaquopy) ---
chaquopy {
    defaultConfig {
        version = "3.13"
        // Host interpreter que Chaquopy usa para compilar bytecode + correr pip.
        // Debe coincidir con major.minor de `version`.
        //   Windows: launcher `py -3.13`   ·   Linux/Mac: binario python3.13
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("win")) buildPython("py", "-3.13")
        else buildPython("/usr/bin/python3.13")
        pip {
            install("requests")
            install("Pillow")   // jarvis_core.shrink() — comprime imágenes para la vision API
            install("pypdf")    // jarvis_core.handle_document() — lectura de PDFs
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(project(":llama"))   // inferencia local (llama.cpp)
    implementation(libs.coil.compose)   // miniaturas de PalsHub
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.composables.lucide)   // iconos Lucide para Compose
    implementation("org.nanohttpd:nanohttpd:2.3.1")  // AndroidBridge: HTTP localhost para el agente
    implementation(libs.androidx.security.crypto)     // ConfigStore cifrado (Android Keystore)

    debugImplementation(libs.androidx.ui.tooling)
}
