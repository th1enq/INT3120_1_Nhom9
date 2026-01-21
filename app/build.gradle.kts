import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.coupleapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.coupleapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Load Maps API Key from local.properties
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
        manifestPlaceholders["MAPS_API_KEY"] = properties.getProperty("MAPS_API_KEY", "")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.accompanist.systemuicontroller)
    
    // Lifecycle runtime compose for LocalLifecycleOwner
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Lifecycle process for app lifecycle tracking
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    
    // Lottie for animations
    implementation("com.airbnb.android:lottie-compose:6.1.0")
    
    // CameraX for Locket feature
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    
    // Accompanist permissions for camera permission handling
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // Coil for image loading (Firebase Storage URLs)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Google Maps for Distance feature
    implementation("com.google.maps.android:maps-compose:4.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // Core library desugaring for Java 8 Time API support on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Health Connect for Sleep Tracking
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")
    
    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    
    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    
    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth-ktx")
    
    // Firestore Database
    implementation("com.google.firebase:firebase-firestore-ktx")
    
    // Firebase Realtime Database
    implementation("com.google.firebase:firebase-database-ktx")
    
    // Firebase Storage
    implementation("com.google.firebase:firebase-storage-ktx")
    
    // Firebase Analytics
    implementation("com.google.firebase:firebase-analytics-ktx")
    
    // Firebase Cloud Messaging (for notifications)
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // WorkManager for background photo sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Room Database (Single Source of Truth for widget data)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Hilt for Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    
    // ExifInterface for reading photo GPS data
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}