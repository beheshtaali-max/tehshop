plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}


fun sanitizeApkFileNamePart(value: String): String {
    return value
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
        .replace(Regex("\\s+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-', '.', '_')
        .ifBlank { "app" }
}

fun decodeXmlStringValue(value: String): String {
    return value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}

fun readAppNameFromStringsXml(): String {
    val candidateFiles = listOf(
        file("src/main/res/values/strings.xml"),
        file("src/main/res/values/String.xml")
    )

    val stringsFile = candidateFiles.firstOrNull { it.exists() } ?: return "app"
    val match = Regex(
        pattern = """<string\s+name=[\"']app_name[\"'][^>]*>(.*?)</string>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    ).find(stringsFile.readText())

    return match
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(Regex("<[^>]+>"), "")
        ?.let(::decodeXmlStringValue)
        ?.let(::sanitizeApkFileNamePart)
        ?: "app"
}

android {
    namespace = "pw.fullvpn.android"
    compileSdk {
        version = release(36)
    }
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    defaultConfig {
        applicationId = "pw.tehvpnn.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.010"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-analytics")

    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    implementation(libs.androidx.navigation.compose)

    //Preferences in compose
    implementation(libs.datastore.preferences)

    //Blur
    implementation(libs.compose.cloudy)
    implementation(libs.chrisbanes.haze)
    implementation(libs.haze.materials)
    //implementation("dev.chrisbanes.haze:haze-blur:1.7.2")
    //implementation(libs.chrisbanes.haze.blur)

    //Flag
    implementation(libs.worldcountrydata)
    //implementation("com.github.jsramraj:flags:v1.0")

    //Di
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.android)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.encoding)

    // JSON
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    //
    implementation(libs.accompanist.drawablepainter)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)


    implementation(libs.localbroadcastmanager)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)


    // Module
    implementation(project(":v2ray"))
}