plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.androobsidian.wear"
    compileSdk = 34

    defaultConfig {
        // Must match phone app for Data Layer API to work
        applicationId = "com.androobsidian.mobile"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=com.google.android.horologist.annotations.ExperimentalHorologistApi")
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }
}

dependencies {
    implementation(project(":shared"))
    
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Wear OS
    implementation("androidx.wear:wear:1.3.0")
    
    // Compose for Wear OS
    implementation("androidx.wear.compose:compose-material:1.2.1")
    implementation("androidx.wear.compose:compose-foundation:1.2.1")
    implementation("androidx.wear.compose:compose-navigation:1.2.1")
    
    // Tiles
    implementation("androidx.wear.tiles:tiles:1.2.0")
    implementation("androidx.wear.protolayout:protolayout:1.0.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.0.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.0.0")
    
    // Horologist for rotary input
    implementation("com.google.android.horologist:horologist-compose-layout:0.5.28")
    implementation("com.google.android.horologist:horologist-compose-tools:0.5.28")
    
    // Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
    
    // Guava for ListenableFuture
    implementation("com.google.guava:guava:32.1.3-android")
}
