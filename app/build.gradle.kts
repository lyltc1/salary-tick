plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProps: Map<String, String> = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.readLines()
    ?.filter { !it.trim().startsWith("#") && it.contains("=") }
    ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    ?: emptyMap()

android {
    namespace = "com.example.salarytick"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.salarytick"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps["KEYSTORE_FILE"] ?: "salarytick.jks")
            storePassword = keystoreProps["KEYSTORE_PASSWORD"] ?: "salarytick2026"
            keyAlias = keystoreProps["KEY_ALIAS"] ?: "salarytick"
            keyPassword = keystoreProps["KEY_PASSWORD"] ?: "salarytick2026"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}