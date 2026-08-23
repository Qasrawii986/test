import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing. The keystore lives outside git (only its GPG-encrypted twin
 * is committed); credentials come from signing/keystore.properties locally or
 * from environment variables in CI. When neither is present the release build
 * falls back to debug signing so the project still builds for anyone.
 *
 * The key must stay the same forever — Android refuses to update an installed
 * app with a differently-signed APK.
 */
val keystorePropertiesFile = rootProject.file("signing/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val releaseStoreFile: File? = when {
    keystoreProperties.containsKey("storeFile") ->
        rootProject.file(keystoreProperties.getProperty("storeFile"))
    System.getenv("KEYSTORE_PATH") != null -> file(System.getenv("KEYSTORE_PATH"))
    else -> null
}?.takeIf { it.exists() }
val releaseStorePassword: String? =
    keystoreProperties.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias: String =
    keystoreProperties.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS") ?: "smsexpense"
val releaseKeyPassword: String? =
    keystoreProperties.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null

android {
    namespace = "com.smsexpense.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smsexpense.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.12.0"

        // Where the in-app updater looks for new releases (public repo: no auth needed).
        buildConfigField("String", "UPDATE_OWNER", "\"Qasrawii986\"")
        buildConfigField("String", "UPDATE_REPO", "\"test\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword ?: releaseStorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // Debug builds allow verbose parser logging; release strips it (see AppLog).
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.arch.core.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
