plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.ghostuser.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ghostuser.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.4"

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            // Read from env vars; deliberately no hardcoded fallback so a
            // forgotten export fails loudly instead of signing with a weak key.
            storeFile = file("${rootProject.projectDir}/ghostuser-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    gradle.taskGraph.whenReady {
        if (allTasks.any { it.name.contains("assembleRelease") || it.name.contains("bundleRelease") }) {
            val sc = signingConfigs.getByName("release")
            require(!sc.storePassword.isNullOrBlank() && !sc.keyAlias.isNullOrBlank() && !sc.keyPassword.isNullOrBlank()) {
                "Release build requires KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD env vars."
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DataStore (settings)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Kotlin serialization (macro persistence)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
