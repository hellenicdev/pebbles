plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.sentry.android.gradle")
}

val defaultSentryDsn =
    "https://f6586d71fba55531896a3ef3414811ab@o4511908991729664.ingest.de.sentry.io/4512469049081936"

fun javaStringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(ch)
        }
    }
    append('"')
}

// SENTRY_DSN wins over -PsentryDsn. Both are optional; the project DSN is the default.
val sentryDsn: String = listOf(
    System.getenv("SENTRY_DSN"),
    findProperty("sentryDsn") as? String,
).firstNotNullOfOrNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
    ?: defaultSentryDsn

android {
    namespace = "eu.hellenicdev.pebbles"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.hellenicdev.pebbles"
        minSdk = 26
        targetSdk = 35
        val requestedVersion = (findProperty("versionName") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val appVersion = requestedVersion ?: "0.5.0"
        versionName = appVersion
        // major*10000 + minor*100 + patch, so a newer release can install over this one.
        versionCode = semverCode(appVersion)
        buildConfigField("String", "SENTRY_DSN", javaStringLiteral(sentryDsn))
    }

    val storePath = System.getenv("PEBBLES_STORE_FILE")
    val storePassword = System.getenv("PEBBLES_STORE_PASSWORD")
    val keyAlias = System.getenv("PEBBLES_KEY_ALIAS")
    val keyPassword = System.getenv("PEBBLES_KEY_PASSWORD")
    if (!storePath.isNullOrBlank() && !storePassword.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(storePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
        buildConfig = true
    }
}

sentry {
    org.set("hellenicdev")
    projectName.set("pebbles-android")
    url.set("https://de.sentry.io")

    // Mapping and source upload need a Sentry auth token. Keep them off so
    // assembleDebug and assembleRelease succeed with no extra secrets.
    includeProguardMapping.set(false)
    autoUploadProguardMapping.set(false)
    uploadNativeSymbols.set(false)
    autoUploadNativeSymbols.set(false)
    includeNativeSources.set(false)
    includeSourceContext.set(false)
    autoUploadSourceContext.set(false)
    telemetry.set(false)

    tracingInstrumentation {
        enabled.set(false)
        logcat {
            enabled.set(false)
        }
    }

    autoInstallation {
        enabled.set(true)
        sentryVersion.set("8.65.0")
    }
}

fun semverCode(name: String): Int {
    val parts = name.removePrefix("v").substringBefore("-").split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10_000 + minor * 100 + patch
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.sentry:sentry-android:8.65.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
