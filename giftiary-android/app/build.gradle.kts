import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// The upload key's details come from the git-ignored keystore.properties next to settings.gradle.kts.
// Read once here; a problem only fails the build when release output is actually produced (below).
val releaseSigning = loadReleaseSigning()

android {
    namespace = "app.giftiary"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.giftiary"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.0-beta.2"

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

    signingConfigs {
        releaseSigning.config?.let { signing ->
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        release {
            // Never debug-signed: without keystore.properties the release tasks fail instead.
            signingConfig = signingConfigs.findByName("release")
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

// Every task that turns the release variant into an APK or AAB, whatever was asked for on the command
// line (assembleRelease, bundleRelease, build, installRelease…), waits on this check. Debug builds, unit
// tests, lint and the debug-signed minifiedSmoke never reach it.
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    description = "Fails unless keystore.properties names a usable upload key."
    val problem = releaseSigning.problem
    doLast {
        if (problem != null) {
            throw GradleException(
                "Release signing is not configured: $problem. Put storeFile, storePassword, keyAlias and " +
                    "keyPassword in giftiary-android/keystore.properties (git-ignored; a relative storeFile " +
                    "is resolved from giftiary-android/). See docs/release-checklist.md, Signing.",
            )
        }
    }
}
// Same boundary for the backend a release talks to: a debug build may run without one (it warns), but a
// release that cannot reach Supabase, or would reach it over plain HTTP, is never produced.
val checkReleaseBackend = tasks.register("checkReleaseBackend") {
    description = "Fails unless supabase.url is an https URL and supabase.publishableKey is set."
    val problems = releaseBackendProblems(supabaseSetting("supabase.url"), supabaseSetting("supabase.publishableKey"))
    doLast {
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Release backend is not configured: ${problems.joinToString("; ")}. Set supabase.url and " +
                    "supabase.publishableKey for the hosted project in giftiary-android/local.properties (git-ignored) " +
                    "or as -P properties. See docs/release-checklist.md, Build.",
            )
        }
    }
}
val releaseOutputTasks = setOf("minifyReleaseWithR8", "packageRelease", "packageReleaseBundle", "signReleaseBundle")
tasks.configureEach {
    if (name in releaseOutputTasks) dependsOn(checkReleaseSigning, checkReleaseBackend)
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
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.ktor.client.mock)
    // Compose layout tests under Robolectric; the manifest gives them an activity to run in.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

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

/** What is wrong with the release backend settings; never their values, so nothing configured is echoed. */
fun releaseBackendProblems(url: String, publishableKey: String): List<String> = buildList {
    if (url.isBlank()) {
        add("supabase.url is not set")
    } else {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        val valid = uri != null && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null
        if (!valid) add("supabase.url is not an https:// URL with a host")
    }
    if (publishableKey.isBlank()) add("supabase.publishableKey is not set")
}

/** Same sources as [supabaseSetting]; only the exact value "true" enables a provider. */
fun providerEnabled(name: String): Boolean {
    val localProperties = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
    }
    return (localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull)?.trim() == "true"
}

class SigningValues(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

/** Either the values to sign with, or what is wrong with them; never the secrets themselves in [problem]. */
class ReleaseSigning(val config: SigningValues?, val problem: String?)

fun loadReleaseSigning(): ReleaseSigning {
    val text = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?: return ReleaseSigning(null, "giftiary-android/keystore.properties does not exist")
    val properties = Properties().apply { load(text.reader()) }
    val fields = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val blank = fields.filter { properties.getProperty(it).isNullOrBlank() }
    if (blank.isNotEmpty()) return ReleaseSigning(null, "keystore.properties has no value for ${blank.joinToString()}")
    val storeFile = rootProject.file(properties.getProperty("storeFile").trim())
    if (!storeFile.isFile) return ReleaseSigning(null, "the keystore named by storeFile does not exist ($storeFile)")
    return ReleaseSigning(
        SigningValues(
            storeFile = storeFile,
            storePassword = properties.getProperty("storePassword"),
            keyAlias = properties.getProperty("keyAlias").trim(),
            keyPassword = properties.getProperty("keyPassword"),
        ),
        problem = null,
    )
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
