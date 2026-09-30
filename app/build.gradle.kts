plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.glyphalarm"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.glyphalarm"
        // The Nothing Glyph Developer Kit needs Android 14+
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the APK installs straight away; swap for a real key to publish.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildFeatures {
      compose = true
      buildConfig = true
    }
}

dependencies {
    // Nothing Glyph Developer Kit (Ketchum SDK) — drop the official .jar/.aar into app/libs
    implementation(fileTree("libs") { include("*.jar", "*.aar") })

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation("junit:junit:4.13.2")
}
