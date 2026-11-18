plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Release-ondertekening en versienummer komen in CI uit omgevingsvariabelen (zie README).
// Zonder deze variabelen bouwt een release-build gewoon, maar dan niet ondertekend.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

android {
    namespace = "nl.theeuwesit.stapje"
    compileSdk = 37

    defaultConfig {
        applicationId = "nl.theeuwesit.stapje"
        minSdk = 26
        targetSdk = 37
        versionCode = env("VERSION_CODE")?.toInt() ?: 1
        versionName = env("VERSION_NAME") ?: "1.0"
    }
    signingConfigs {
        create("release") {
            env("STAPJE_KEYSTORE_FILE")?.let { storeFile = file(it) }
            storePassword = env("STAPJE_KEYSTORE_PASSWORD")
            keyAlias = env("STAPJE_KEY_ALIAS")
            keyPassword = env("STAPJE_KEY_PASSWORD")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (env("STAPJE_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
