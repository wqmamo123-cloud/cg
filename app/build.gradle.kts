plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.chess"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.chess"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ──────────────────────────────────────────────
    // Jetpack Compose BOM — manages all Compose versions
    // ──────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose Core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    // ──────────────────────────────────────────────
    // Navigation Compose
    // ──────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ──────────────────────────────────────────────
    // Lifecycle & ViewModel Compose
    // ──────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // ──────────────────────────────────────────────
    // Kotlin Coroutines
    // ──────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // ──────────────────────────────────────────────
    // Coil — Image loading with SVG decoder
    // (SVG decoder required for Wikimedia chess piece vectors)
    // ──────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // ──────────────────────────────────────────────
    // OkHttp — required by Coil's SVG ImageLoader
    // (User-Agent interceptor for Wikimedia 403 bypass)
    // ──────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ──────────────────────────────────────────────
    // AndroidX Core
    // ──────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.15.0")

    // ──────────────────────────────────────────────
    // Debug tooling
    // ──────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
