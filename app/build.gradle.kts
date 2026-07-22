plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.focusgate.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusgate.launcher"
        minSdk = 26        // Gemini Nano only actually runs on much newer devices;
                           // availability is gated at runtime via checkStatus().
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Keep minification off: kotlinx-serialization + the MediaPipe native
            // libs are easier to keep unshrunk. Rules are stubbed in proguard-rules.pro.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign the release with the local debug key so the APK is installable
            // for sideloading / sharing. Swap in a real keystore for Play Store.
            signingConfig = signingConfigs.getByName("debug")
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
    }
    androidResources {
        // The bundled Gemma model is already 4-bit quantized; don't let aapt try to
        // recompress ~550 MB. Stored uncompressed so it copies out fast on first run.
        noCompress += "task"
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // On-device LLM (Gemma) via Google AI Edge — MediaPipe LLM Inference.
    // Unlike Gemini Nano/AICore this runs a model file we supply ourselves, so
    // it does NOT depend on AICore provisioning a feature. See FocusAgent.kt.
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
}
