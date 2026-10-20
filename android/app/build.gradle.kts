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
        applicationId = "com.mathis.mandala"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProperties.isFile) {
            create("upload") {
                storeFile = file(homePath(keystore.getProperty("storeFile") ?: "~/.keystore/mandala-upload.jks"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias") ?: "upload"
                keyPassword = keystore.getProperty("keyPassword") ?: keystore.getProperty("storePassword")
            }
        }
    }

    buildTypes {
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
    // One-time "Pro" purchase (ADR-0007, approved 20 October 2026).
    implementation("com.android.billingclient:billing:8.3.0")

    testImplementation("junit:junit:4.13.2")
}
