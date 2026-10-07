plugins {
    id("com.android.application")
    // NOTE: with AGP 9 the Kotlin compiler is built in; do NOT apply
    // org.jetbrains.kotlin.android (it fails with a fatal diagnostic).
}

android {
    namespace = "eu.kanade.tachiyomi.extension.en.ehentai"

    compileSdk = 36

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.en.ehentai"
        // minSdk 21: Tachimanga's JVM-based extension runtime cannot verify
        // bytecode that relies on `invokedynamic` (string concat / lambdas
        // stay native when minSdk >= 26). With 21, D8 desugars those to
        // classic bytecode, avoiding java.lang.VerifyError there.
        minSdk = 21
        targetSdk = 36
        // versionCode grows with every release; the version name starts with
        // the lib version (1.4) so legacy apps parse it correctly.
        versionCode = 6
        versionName = "1.4.6"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            // Debug-signed so the APK can be installed directly ("local install" in Mihon).
            // Keep rules are minimal because the source class is discovered by dex scanning.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    signingConfigs {
        // Project-local debug key (AGP's default would write to the user home
        // directory, which is not writable in every build environment).
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

// Kotlin jvmTarget is derived automatically from compileOptions (AGP 9 built-in Kotlin).

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("tachiyomi-en.ehentai-v1.4.6.apk")
        }
    }
}

dependencies {
    // extensions-lib 1.4 (tachiyomiorg, classic Observable API): the exact
    // API bundled by Tachimanga and other legacy clients. Compiling against
    // the modern suspend API (keiyoushi, lib 1.6) produces APKs that fail
    // with VerifyError in those apps.
    compileOnly("com.github.tachiyomiorg:extensions-lib:1.4.4") {
        // The original injekt repo (inorichi) is dead and unresolvable via
        // JitPack; the maintained fork below (same uy.kohesive.injekt
        // packages) is used instead.
        exclude(group = "com.github.inorichi.injekt", module = "injekt-core")
    }
    compileOnly("io.reactivex:rxjava:1.3.8")
    compileOnly("org.jsoup:jsoup:1.22.2")
    compileOnly("com.squareup.okhttp3:okhttp:5.4.0")
    compileOnly("com.github.null2264.injekt:injekt-core:4135455a2a")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    compileOnly("org.jspecify:jspecify:1.0.0")

    // Unit tests run on the JVM against the real classes.
    testImplementation("com.github.tachiyomiorg:extensions-lib:1.4.4") {
        exclude(group = "com.github.inorichi.injekt", module = "injekt-core")
    }
    testImplementation("io.reactivex:rxjava:1.3.8")
    testImplementation("org.jsoup:jsoup:1.22.2")
    testImplementation("com.squareup.okhttp3:okhttp:5.4.0")
    testImplementation("junit:junit:4.13.2")
}
