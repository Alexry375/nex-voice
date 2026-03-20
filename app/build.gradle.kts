plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nex.voice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nex.voice"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Defaults loaded from local.properties or env vars
        buildConfigField("String", "DEFAULT_BOT_TOKEN", "\"${findProperty("nex.bot_token") ?: System.getenv("NEX_BOT_TOKEN") ?: ""}\"")
        buildConfigField("String", "DEFAULT_CHAT_ID", "\"${findProperty("nex.chat_id") ?: System.getenv("NEX_CHAT_ID") ?: ""}\"")
        buildConfigField("String", "DEFAULT_GROQ_KEY", "\"${findProperty("nex.groq_key") ?: System.getenv("NEX_GROQ_KEY") ?: ""}\"")
        buildConfigField("String", "DEFAULT_BRIDGE_URL", "\"${findProperty("nex.bridge_url") ?: System.getenv("NEX_BRIDGE_URL") ?: "http://100.96.206.81:3459"}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
