plugins {
    id("com.android.application")
    // NOTE: with AGP 9 the Kotlin compiler is built in; do NOT apply
    // org.jetbrains.kotlin.android (it fails with a fatal diagnostic).
}

android {
    namespace = "eu.kanade.tachiyomi.extension.en.ehentai.v16"

    compileSdk = 36

    defaultConfig {
        // Its own package name, so this build installs next to the 1.4 one
        // (eu.kanade.tachiyomi.extension.en.ehentai) instead of replacing it.
        applicationId = "eu.kanade.tachiyomi.extension.en.ehentai.v16"
        minSdk = 21
        targetSdk = 36
        // versionName must start with the lib version (1.6): apps that predate the
        // tachiyomix.extensionLib metadata read the API version from it.
        versionCode = 1
        versionName = "1.6.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The site logic is shared with the 1.4 build; only the API glue (this module's
    // Ehentai.kt) differs. The 1.4 main class is excluded because it targets the
    // Observable API and would otherwise end up as dead code in the APK.
    sourceSets["main"].kotlin.srcDir("../ehentai/src/main/kotlin")
    sourceSets["main"].kotlin.exclude("**/ehentai/Ehentai.kt")

    buildTypes {
        release {
            isMinifyEnabled = false
            // Same debug key as the 1.4 build: the signing key fingerprint published in
            // repo.json / index.v2.json covers both APKs, so Mihon trusts them.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "kotlin-tooling-metadata.json"
    }

    dependenciesInfo {
        includeInApk = false
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("tachiyomi-en.ehentai-v1.6.0.apk")
        }
    }
}

dependencies {
    // extensions-lib 1.6 (keiyoushi, suspend API) — the API Mihon 0.20+ ships.
    // compileOnly: the app provides these classes at runtime.
    compileOnly("com.github.keiyoushi:extensions-lib:6e0c96cea8")
    compileOnly("org.jsoup:jsoup:1.22.2")
    compileOnly("com.squareup.okhttp3:okhttp:5.4.0")
    compileOnly("com.github.null2264.injekt:injekt-core:4135455a2a")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
