plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firebase config comes from google-services.json (downloaded from the Firebase console).
// Apply the plugin only when the file exists, so the project still builds before Firebase is set up.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.focusnest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.focusnest"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity:1.9.2")

    // Google sign-in, Firebase Auth, and the Firestore points database
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
