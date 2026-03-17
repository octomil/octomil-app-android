plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// KLEIDIAI ARM compatibility: disable KleidiAI kernels for budget ARM SoCs.
// Re-enable with: ./gradlew assembleDebug -Poctomil.disableKleidiai=false
val disableKleidiai = providers.gradleProperty("octomil.disableKleidiai")
    .orElse("true")
    .get()

android {
    namespace = "ai.octomil.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.octomil.app"
        minSdk = 33  // llama.cpp native lib requires API 33+
        targetSdk = 36
        versionCode = 14
        versionName = "1.3.4"

        externalNativeBuild {
            cmake {
                if (disableKleidiai.toBoolean()) {
                    val overlay = file("../octomil-android/build-config/arm-compatibility.cmake")
                    arguments("-C", overlay.absolutePath)
                }
            }
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "keystore.jks"
            val ksFile = file(keystorePath)
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        // Optimised debug: R8 shrinking with debug signing — fast for demos
        create("benchmark") {
            initWith(getByName("debug"))
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release", "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("ai.octomil:octomil-client")
    implementation("ai.octomil:octomil-ui")

    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.core:core-ktx:1.16.0")

    // Google Code Scanner — handles camera internally via Play Services,
    // bypasses CameraX (which crashes on some Samsung devices).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Coil for async image loading in Compose
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.json:json:20231013")
}
