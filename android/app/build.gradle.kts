import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Upload key for Google Play, read from the git-ignored android/keystore.properties (see
// docs/store/STORE-1-play-listing.md). Without that file, release builds are signed with the local
// debug key, so they still build and install for testing; Google Play rejects such a bundle.
val keystoreProperties = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreProperties.isFile) keystoreProperties.inputStream().use(::load) }

fun homePath(path: String): String = if (path.startsWith("~/")) System.getProperty("user.home") + path.substring(1) else path

android {
    namespace = "com.mathis.mandala.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "one.tearoom.mandala"
        minSdk = 29
        targetSdk = 36
        versionCode = 15
        versionName = "1.8.0"
    }

    signingConfigs {
        if (keystoreProperties.isFile) {
            create("upload") {
                storeFile = file(homePath(keystore.getProperty("storeFile") ?: "~/.keystore/mandalagenerator-upload.jks"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias") ?: "upload"
                keyPassword = keystore.getProperty("keyPassword") ?: keystore.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        // Own package id and name, so a debug build sits next to the store or release app on a phone
        // (Android only replaces an app with one of the same signature).
        debug {
            applicationIdSuffix = ".debug"
        }
        // A debuggable app runs mostly in the interpreter and renders many times slower; this one runs
        // at release speed for testing on a phone, next to the store and debug apps.
        create("fast") {
            initWith(getByName("debug"))
            isDebuggable = false
            applicationIdSuffix = ".fast"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "debug"
        }
        // The fast build without Pro, to try the free app (ADR-0016) on a phone that owns Pro.
        create("free") {
            initWith(getByName("fast"))
            applicationIdSuffix = ".free"
            matchingFallbacks += "debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    // One-time "Pro" purchase (ADR-0007) through the shared tearoom-android-kit (ADR-0022), which brings
    // Google Play Billing 9.1.0.
    implementation("one.tearoom:unlock:1.0.0")
    constraints {
        // Billing's Play Services pull in fragment 1.1.0, which Play Console flags as outdated.
        implementation("androidx.fragment:fragment:1.9.1")
    }

    testImplementation("junit:junit:4.13.2")
}
