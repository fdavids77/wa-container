plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Resolve signing credentials once at config time.
    // Resolution order:
    //   1. Env vars (CI):   KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
    //   2. Gradle props (local ~/.gradle/gradle.properties):
    //        WA_KEYSTORE_PATH, WA_KEYSTORE_PASSWORD, WA_KEY_ALIAS, WA_KEY_PASSWORD
    //   3. Fallback: AGP default debug key — APK will not install over a release-signed build.
    //
    // The keystore is gitignored (*.jks). CI decodes KEYSTORE_B64 secret to $RUNNER_TEMP/release.jks.
    // Local: add the four WA_* properties to ~/.gradle/gradle.properties.
    val ksPath = System.getenv("KEYSTORE_PATH")
        ?: (findProperty("WA_KEYSTORE_PATH") as String?)
    val ksPassword = System.getenv("KEYSTORE_PASSWORD")
        ?: (findProperty("WA_KEYSTORE_PASSWORD") as String?)
    val ksAlias = System.getenv("KEY_ALIAS")
        ?: (findProperty("WA_KEY_ALIAS") as String?)
    val ksKeyPassword = System.getenv("KEY_PASSWORD")
        ?: (findProperty("WA_KEY_PASSWORD") as String?)
    val ksFile = ksPath?.let { file(it) }
    val hasReleaseSigning = ksFile?.exists() == true &&
        !ksPassword.isNullOrEmpty() && !ksAlias.isNullOrEmpty() && !ksKeyPassword.isNullOrEmpty()

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
        if (hasReleaseSigning) {
            create("release") {
                storeFile = ksFile
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                println("⚠ wa-container: release keystore not configured — APK will be debug-signed and cannot be installed over a release-signed build")
                signingConfigs.getByName("debug")
            }
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
