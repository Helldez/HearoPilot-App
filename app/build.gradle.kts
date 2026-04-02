plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.plugin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.hearopilot.app.ui"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hearopilot.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 20260327
        versionName = "1.25.00"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Restrict the AAB/APK to arm64-v8a only.
        // This applies to all native libraries including prebuilt jniLibs in lib-sherpa-onnx.
        // Without this filter, jniLibs/armeabi-v7a (and x86/x86_64) are bundled in the AAB,
        // causing the Play Store to serve the app to 32-bit-only devices (Android Go, budget phones).
        // Those devices cannot load libai-chat.so (arm64-v8a only) → UnsatisfiedLinkError crash.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Force extraction of native libraries to nativeLibraryDir.
        // Required because lib-llama-android and lib-sherpa-onnx use
        // dlopen() on backend .so files at runtime via nativeLibraryDir path.
        // Without this, minSdk >= 23 defaults to extractNativeLibs=false,
        // keeping .so files inside the APK where dlopen() cannot find them.
        jniLibs {
            useLegacyPackaging = true
            // lib-llama-android bundles libdatastore_shared_counter.so; Firebase pulls DataStore
            // as a transitive dependency which also ships the same .so — pick one copy.
            pickFirsts += "lib/arm64-v8a/libdatastore_shared_counter.so"
        }
    }
}

dependencies {
    // New architecture modules
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":presentation"))
    implementation(project(":feature-stt"))
    implementation(project(":feature-llm"))
    implementation(project(":lib-sherpa-onnx"))
    implementation(project(":lib-llama-android"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.compose.material:material-icons-extended")

    // UI Enhancements
    implementation("com.materialkolor:material-kolor:2.0.0")  // M3 palette generation
    implementation("com.halilibo.compose-richtext:richtext-ui-material3:1.0.0-alpha01")  // RichText UI
    implementation("com.halilibo.compose-richtext:richtext-commonmark:1.0.0-alpha01")  // Markdown parsing
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.1")  // Skeleton loading

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
