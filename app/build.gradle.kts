plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.irobot.english"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.irobot.english"
        minSdk = 26          // Bluetooth LE advertising sets need Android 8.0+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // A fixed key kept in the repo so every new build installs over the old one.
        // It is a personal sideload key, not a Play Store key.
        create("sideload") {
            storeFile = rootProject.file("keystore/sideload.jks")
            storePassword = "irobot-sideload"
            keyAlias = "irobot"
            keyPassword = "irobot-sideload"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
}
