plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.paparazzi)
}

// Every build is signed with one stable key so a new APK installs over the old one.
// CI passes the key in through environment variables (from GitHub secrets); locally
// you can export the same variables. Without them the build falls back to the
// per-machine debug key, which is fine for trying things out but can't update an
// installed copy signed with the real key.
val keystoreFile = System.getenv("YESCHEF_KEYSTORE")?.let(::file)?.takeIf { it.exists() }

// Each CI run gets a higher version code, which Android needs to accept an update.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0

android {
    namespace = "app.yeschef"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.yeschef"
        // Pixel phones and the Pixel Tablet all run recent Android.
        minSdk = 29
        targetSdk = 35
        versionCode = 100 + buildNumber
        versionName = "0.2.$buildNumber"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("yeschef") {
                storeFile = keystoreFile
                storePassword = System.getenv("YESCHEF_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("YESCHEF_KEY_ALIAS")
                keyPassword = System.getenv("YESCHEF_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("yeschef") ?: signingConfigs.getByName("debug")
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
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navsuite)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}

// Debug and release KSP both write Room's schema JSON to the same folder.
// Running them at the same time can leave an empty file and fail the build.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
    mustRunAfter("kspDebugKotlin")
}
