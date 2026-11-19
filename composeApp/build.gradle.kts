import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Supabase-gegevens: uit omgevingsvariabelen (CI) of anders uit local.properties (lokaal).
// Leeg = de app werkt alleen lokaal, zonder inloggen.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun supabaseSetting(envName: String, propertyName: String): Provider<String> =
    providers.environmentVariable(envName)
        .orElse(providers.provider { localProperties.getProperty(propertyName) ?: "" })

abstract class GenerateSupabaseConfig : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val publishableKey: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        fun lit(value: String) = "\"" + value.trim().replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        val file = outputDir.get().file("nl/theeuwesit/stapje/sync/SupabaseConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package nl.theeuwesit.stapje.sync
            |
            |// Gegenereerd door composeApp/build.gradle.kts, niet met de hand wijzigen.
            |object SupabaseConfig {
            |    const val URL = ${lit(url.get())}
            |    const val PUBLISHABLE_KEY = ${lit(publishableKey.get())}
            |}
            |""".trimMargin()
        )
    }
}

val generateSupabaseConfig by tasks.registering(GenerateSupabaseConfig::class) {
    url = supabaseSetting("SUPABASE_URL", "supabase.url")
    publishableKey = supabaseSetting("SUPABASE_PUBLISHABLE_KEY", "supabase.publishableKey")
    outputDir = layout.buildDirectory.dir("generated/supabase/kotlin")
}

kotlin {
    android {
        namespace = "nl.theeuwesit.stapje.shared"
        compileSdk = 37
        minSdk = 26
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSupabaseConfig)
        }
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            api(libs.supabase.postgrest)
            api(libs.supabase.auth)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}
