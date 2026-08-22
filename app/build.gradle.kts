import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing credentials live in local.properties (gitignored). When they
// are absent — clean clones, CI, F-Droid's builders — the release build simply
// stays unsigned; F-Droid signs with its own key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { stream -> this.load(stream) }
}
val releaseStoreFile: String? = localProps.getProperty("RELEASE_STORE_FILE")

android {
    namespace = "com.defang.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.defang.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.1.16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // No embedded git revision: the released APK must be
            // byte-identical to F-Droid's build from the tag commit
            // (reproducible builds), and the revision necessarily differs
            // between a working-tree build and a clean tag checkout.
            vcsInfo {
                include = false
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
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
    }

    // Google-encrypted dependency block (Play Console telemetry) — F-Droid
    // rejects APKs containing it, and we don't publish to Play anyway.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Robolectric's includeAndroidResources reads the app's own merged
    // assets (not a test-only source set), so the exported schemas need to
    // live under main assets for MigrationTestHelper to find them here.
    sourceSets {
        getByName("main") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

// Exported schema JSONs (committed to git) let MigrationTestHelper build a
// real historical version and run each Migration against it, so a mismatch
// like issue #19 (hand-written migration SQL drifting from the entity's
// actual schema) fails a fast unit test instead of shipping.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Core
    implementation(libs.core.ktx)

    // QR / barcode unlock
    implementation(libs.zxing.embedded)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.navigation)
    implementation(libs.compose.viewmodel)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.android)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.android.junit)
    androidTestImplementation(libs.espresso)
}
