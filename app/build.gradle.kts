import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing is supplied by an untracked keystore.properties at the
// repository root (see RELEASE_CHECKLIST.md). Without it the release variant
// still builds, but its APK/AAB stay unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.musicplayer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.musicplayer"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Keep only the English locale to strip unused alternative resources.
        resConfigs("en")
    }

    signingConfigs {
        if (keystorePropertiesFile.isFile) {
            create("release") {
                storeFile = rootProject.file(
                    requireNotNull(keystoreProperties.getProperty("storeFile")) {
                        "keystore.properties must define storeFile"
                    },
                )
                storePassword = requireNotNull(keystoreProperties.getProperty("storePassword")) {
                    "keystore.properties must define storePassword"
                }
                keyAlias = requireNotNull(keystoreProperties.getProperty("keyAlias")) {
                    "keystore.properties must define keyAlias"
                }
                keyPassword = requireNotNull(keystoreProperties.getProperty("keyPassword")) {
                    "keystore.properties must define keyPassword"
                }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        // MigrationTestHelper reads the exported Room schemas from test assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

ksp {
    // Export the schema so future versions can ship explicit Room migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core and lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (versions are provided by the Compose BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Icon set for tabs, transport controls and menus. Only the icons that are
    // actually referenced survive R8 in release builds.
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coroutines and Flow
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3 playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Artwork loading
    implementation(libs.coil.compose)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Preferences and Storage Access Framework
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
