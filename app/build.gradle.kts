plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.jbhugh.songtabfinderfree"  // Required for AGP 7.0+
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jbhugh.songtabfinderfree"
        minSdk = 21
        targetSdk = 34
        versionCode = 1  // New version for free app
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Billing kept in case original has it, but unused in code
    implementation("com.android.billingclient:billing:6.0.1")
}