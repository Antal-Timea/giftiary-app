import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "app.giftiary"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.giftiary"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Client-safe values only: the project URL and the publishable (anon) key. Access is
        // enforced by row level security, never by keeping this key secret.
        buildConfigField("String", "SUPABASE_URL", supabaseSetting("supabase.url").asBuildConfigString())
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            supabaseSetting("supabase.publishableKey").asBuildConfigString(),
        )
        // A provider is offered only by builds that enable it on purpose, once its console and
        // the Supabase provider are set up; anything but "true" leaves its button out.
        buildConfigField("boolean", "AUTH_GOOGLE_ENABLED", providerEnabled("auth.google.enabled").toString())
        buildConfigField("boolean", "AUTH_FACEBOOK_ENABLED", providerEnabled("auth.facebook.enabled").toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release, minified the same way but debug-signed, to run the decoding smoke on a device
        // against R8's output. It adds only src/minifiedSmoke, an instrumentation of the app itself,
        // and no keep rule. Never shipped.
        create("minifiedSmoke") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".minifiedsmoke"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        // Robolectric reads the debug variant's merged assets, so the migration tests find the
        // exported schema history there. Release builds do not include it.
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's Android 17 runtime reaches into JDK internals.
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED",
            )
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.ktor.client.mock)

    // The minified decoding smoke: its entry point serves PostgREST rows from a mock engine.
    add("minifiedSmokeImplementation", libs.ktor.client.mock)
}

/**
 * Supabase settings come from the git-ignored local.properties, or from a Gradle property
 * (e.g. -Psupabase.url=... on CI). A missing value builds an app that cannot reach Supabase.
 */
fun supabaseSetting(name: String): String {
    val localProperties = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
    }
    val value = localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull
    if (value.isNullOrBlank()) logger.warn("Giftiary: $name is not set in local.properties; Supabase is unavailable.")
    return value.orEmpty()
}

/** Same sources as [supabaseSetting]; only the exact value "true" enables a provider. */
fun providerEnabled(name: String): Boolean {
    val localProperties = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
    }
    return (localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull)?.trim() == "true"
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
