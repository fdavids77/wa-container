plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fdavids77.wacontainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fdavids77.wacontainer"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            // Pin to the specific keystore used to sign the build already installed on
            // the device. The app is installed into Private Space (user 10) and holds
            // INTERACT_ACROSS_USERS_FULL; a signature mismatch on update requires a full
            // uninstall/reinstall. The exists() guard lets CI fall back to its own
            // generated debug key without failing the build.
            val pinned = file("/home/kali/.android/debug.keystore")
            if (pinned.exists()) {
                storeFile = pinned
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}
