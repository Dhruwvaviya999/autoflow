plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.dhruw.autoflow"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dhruw.autoflow"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    sourceSets {
        // Exported Room schemas as test assets so MigrationTestHelper can
        // create old-version databases and validate migrations.
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// room-testing 2.8.x needs kotlinx-serialization 1.8+, but androidx.navigation
// strictly pins core to 1.7.3, causing AbstractMethodError when the migration
// test parses schema JSON. Force a consistent modern version for tests only.
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM org.json so converter unit tests run without the stubbed android.jar classes
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    // room-testing parses exported schema JSON with kotlinx-serialization;
    // pin a runtime matching its compiled-against API to avoid
    // AbstractMethodError from an older transitive core.
    androidTestImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}