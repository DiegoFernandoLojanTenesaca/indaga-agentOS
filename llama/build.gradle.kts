// Módulo de inferencia local: llama.cpp compilado con el NDK.
//
// Copiado de llama.cpp/examples/llama.android/lib (upstream de ARM) y adaptado:
//
//   minSdk 33 → 26   El original deja fuera a Android 10, que es justo el
//                    parque objetivo de AgentOS (Huawei post-2019). Es un
//                    valor por defecto de ARM, no un límite técnico de llama.cpp.
//   ndkVersion       a la 27 LTS, que es la instalada.
//   compileSdk 36→35 el del resto del proyecto.
//   cmake 3.31.6→3.31.0  la última que ofrece el SDK manager.
//   x86_64 fuera     solo arm64-v8a: es lo que corre en un teléfono real y
//                    compilar llama.cpp dos veces dobla el tiempo de build.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.arm.aichat"
    compileSdk = 35

    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_APP=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DLLAMA_CURL=OFF"

                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.0"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
