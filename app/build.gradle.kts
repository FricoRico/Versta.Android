import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.net.URI
import java.util.Properties

// keystore.properties is gitignored (developer-local signing secrets). Absent
// the file, release signing is skipped so CI and external contributors can
// still run every Gradle task; debug builds sign with the debug keystore.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

plugins {
    alias(libs.plugins.about.libraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.gradle.crypto.checksum)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.triplet.play)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("app.versta.translate.database")
            srcDirs.setFrom("src/main")
        }
    }
}

aboutLibraries {
    offlineMode = true

    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.GROUP
    }
}

android {
    namespace = "app.versta.translate"
    compileSdk = 36
    ndkVersion = "28.1.13356709"
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.versta.translate"
        minSdk = 28
        targetSdk = 36
        versionCode = 13
        versionName = "1.3.2-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        externalNativeBuild {
            cmake {
                targets("app_versta_translate_bridge")
                arguments += "-DUSE_MBROLA=OFF"
                arguments += "-DUSE_ASYNC=OFF"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file("versta.keystore")
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = "versta"
                keyPassword = keystoreProperties["keyPassword"].toString()
            }
        }
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                // Embed native symbol tables in the AAB so Play Android Vitals
                // can symbolicate C++ (LeanMT/Whisper/espeak) crashes for the
                // beta. SYMBOL_TABLE only: FULL debug info would bloat the
                // bundle past Play's upload-size comfort zone.
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    lint {
        lintConfig = rootProject.file("lint.xml")
        // Pre-existing findings are pinned in the baseline; new findings fail
        // the build. Regenerate deliberately: `./gradlew :app:updateLintBaseline`.
        baseline = rootProject.file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.md"
        }
    }
    androidResources {
        noCompress += "ort"
        noCompress += "json"
        noCompress += "spm"
        noCompress += "bin"
    }
    externalNativeBuild {
        cmake {
            path = file("native/jni/CMakeLists.txt")
            version = "3.31.6"
        }
    }
    flavorDimensions += "abi"

    productFlavors {
        // Scope every flavour to its own ABI. Without per-flavour abiFilters,
        // AGP cannot tell that the flavour names are ABIs and builds the CMake
        // target for all four ABIs into every variant (~4x native compile work
        // per assemble and all four .so sets packaged in every APK).
        create("arm64-v8a") {
            dimension = "abi"
            ndk {
                abiFilters += "arm64-v8a"
            }
            externalNativeBuild {
                cmake {
                    // Architecture-specific SIMD/optimisation flags are set in
                    // third_party/CMakeLists.txt, where ANDROID_ABI is reliable.
                    // Gradle flavour arguments are subject to a shared-mutable-
                    // list bug in AGP 9.0.1 that leaks them between flavours.
                    cppFlags += "-O3"
                }
            }
        }
        create("armeabi-v7a") {
            dimension = "abi"
            ndk {
                abiFilters += "armeabi-v7a"
            }
        }
        create("x86") {
            dimension = "abi"
            ndk {
                abiFilters += "x86"
            }
        }
        create("x86_64") {
            dimension = "abi"
            ndk {
                abiFilters += "x86_64"
            }
        }
        // Release lane only: one AAB carrying every ABI for Play upload (Play
        // accepts a single bundle per release). Per-ABI flavours stay the fast
        // path for local/debug work; nothing debuggable should use this.
        create("universal") {
            dimension = "abi"
        }
    }
//
//    splits {
//        abi {
//            isEnable = true
//            reset()
//            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
//            isUniversalApk = false
//        }
//    }
}

ktlint {
    // Pinned deliberately; bump via libs.versions.toml after reading the
    // ktlint release notes, since new rules can fail existing code.
    version.set(libs.versions.ktlint.get())
    android.set(true)
    outputToConsole.set(true)
    filter {
        // Vendored submodules and Gradle build output are never linted.
        exclude("**/build/**")
        exclude("app/native/**")
    }
}

// The plugin's incremental check tasks treat deleted files as up-to-date and
// keep reporting stale violations (known plugin limitation). A gate must never
// be red on ghosts, so always re-run; a full check takes only a few seconds.
tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    outputs.upToDateWhen { false }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("detekt.yml"))
    // Pre-existing findings live in detekt-baseline.xml so CI only fails on
    // new debt. Regenerate deliberately with `./gradlew :app:detektBaseline`.
    baseline = rootProject.file("detekt-baseline.xml")
}

// Play release automation (see .github/workflows/release-beta.yml and
// RELEASE.md). Auth is keyless: CI mints an OIDC token, exchanges it via
// Workload Identity Federation, and GPP picks it up as Application Default
// Credentials (google-github-actions/auth writes GOOGLE_APPLICATION_CREDENTIALS).
// No service-account JSON key exists anywhere — the GCP org policy
// iam.disableServiceAccountKeyCreation blocks them. Publish tasks only run
// where ADC exists; local/debug builds are unaffected because publishing is
// disabled for every variant except universalRelease below.
play {
    useApplicationDefaultCredentials = true
    track.set("internal")
    defaultToAppBundles.set(true)
    // Repeated beta uploads reuse the same configured versionCode; AUTO bumps
    // past whatever Play already has instead of failing the run.
    resolutionStrategy.set(com.github.triplet.gradle.androidpublisher.ResolutionStrategy.AUTO)
    enabled.set(false)
}

android {
    playConfigs {
        register("universalRelease") {
            // Publishing needs Play credentials at task time (AUTO version-code
            // resolution calls the Play API). Only hook GPP into the variant
            // when a publish is actually requested (-Pversta.play.publish=true,
            // the release lane) — plain release compiles/bundles must stay
            // credential-free for CI and local checks.
            enabled.set(
                providers
                    .gradleProperty("versta.play.publish")
                    .map(String::toBoolean)
                    .orElse(false),
            )
        }
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    autoCorrect = false
}

// Only until compileSdk can move to 37: androidx.compose.ui 1.12.0-rc01 AARs
// declare compileSdk 37 / AGP 9.1+ while the platform SDK channel tops out at
// android-36. MeshGradientPainter requires 1.12.0-rc01 but uses no API > 28.
tasks.configureEach {
    if (name.startsWith("check") && name.endsWith("AarMetadata")) {
        enabled = false
    }
}

tasks.apply {
    // The metadata JSONs are gitignored build inputs (app/src/main/res/.gitignore),
    // so every checkout fetches them once. Declared inputs/outputs make the task
    // up-to-date-checkable, and onlyIf skips it whenever the outputs exist — after
    // the first successful fetch, builds are incremental and work fully offline.
    register("getRemoteData") {
        description = "Fetch model metadata JSON into res/raw (first build per checkout)."

        val metadata =
            mapOf(
                "versta_data.json" to "https://models.versta.app/data/data.json",
                "versta_translation_models.json" to "https://models.versta.app/translation/models.json",
                "versta_speech_recognition_models.json" to "https://models.versta.app/speech-recognition/models.json",
                "versta_text_to_speech_models.json" to "https://models.versta.app/text-to-speech/models.json",
                "versta_object_character_recognition_models.json" to "https://models.versta.app/object-character-recognition/models.json",
            )
        val metadataFiles = metadata.keys.map { layout.projectDirectory.file("src/main/res/raw/$it").asFile }

        metadata.forEach { (file, url) -> inputs.property("url.$file", url) }
        outputs.files(metadataFiles)
        onlyIf { metadataFiles.any { !it.exists() } }

        doLast {
            metadata.forEach { (file, url) ->
                URI(url)
                    .toURL()
                    .openStream()
                    .use { input ->
                        layout.projectDirectory
                            .file("src/main/res/raw/$file")
                            .asFile
                            .also { it.parentFile?.mkdirs() }
                            .outputStream()
                            .use { output ->
                                input.copyTo(output)
                            }
                    }
            }
        }
    }

    preBuild {
        dependsOn("getRemoteData")
    }
}

// ---------------------------------------------------------------------------
// Release lane: universal flavour + ONNX Runtime
//
// The vendored AARs under libs/ are the same upstream ONNX Runtime artifact
// split per ABI (classes.jar byte-identical across the four; only the
// jni/<abi>/ payload differs). The `universal` flavour compiles one Kotlin
// source set for all four ABIs and its AAB must carry every ABI's native
// libs, so the four vendored splits are merged back into one all-ABI AAR at
// build time. The acquisition path stays vendored + offline; the shipped
// natives stay byte-identical to the per-flavour builds.
// ---------------------------------------------------------------------------
val universalOnnxRuntimeAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

val stageUniversalOnnxRuntime by tasks.registering(Copy::class) {
    into(layout.buildDirectory.dir("onnxruntime-universal/stage"))
    // Everything except the per-ABI jni payload is byte-identical across the
    // four splits (same classes.jar hash) — copy it once.
    from(zipTree("$projectDir/libs/arm64-v8a/onnxruntime-1.24.1.aar")) {
        exclude("jni/**")
    }
    universalOnnxRuntimeAbis.forEach { abi ->
        from(zipTree("$projectDir/libs/$abi/onnxruntime-1.24.1.aar")) {
            include("jni/$abi/**")
        }
    }
}

val assembleUniversalOnnxRuntimeAar by tasks.registering(Zip::class) {
    dependsOn(stageUniversalOnnxRuntime)
    from(layout.buildDirectory.dir("onnxruntime-universal/stage"))
    destinationDirectory.set(layout.buildDirectory.dir("onnxruntime-universal"))
    archiveFileName.set("onnxruntime-1.24.1-universal.aar")
}

val universalOnnxRuntimeAarFile =
    layout.buildDirectory.file("onnxruntime-universal/onnxruntime-1.24.1-universal.aar")

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.compose.ui.text)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.about.libraries.core)
    implementation(libs.about.libraries.compose.core)
    implementation(libs.about.libraries.compose.m3)
    implementation(libs.accomanist.permissions)
    implementation(libs.android.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.effects)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.splash.screen)
    implementation(libs.appache.commons.compress)
    implementation(libs.atilika.kuromoji.ipadic)
    implementation(libs.jakewharton.timber)
    implementation(libs.jetbrains.bio.npy)
    implementation(libs.kotlinx.serialization)
    implementation(libs.squareup.okhttp)
    implementation(libs.sqldelight.android)
    implementation(libs.sqldelight.coroutines)

    add("arm64-v8aImplementation", files("$projectDir/libs/arm64-v8a/onnxruntime-1.24.1.aar"))
    add("armeabi-v7aImplementation", files("$projectDir/libs/armeabi-v7a/onnxruntime-1.24.1.aar"))
    add("x86Implementation", files("$projectDir/libs/x86/onnxruntime-1.24.1.aar"))
    add("x86_64Implementation", files("$projectDir/libs/x86_64/onnxruntime-1.24.1.aar"))
    val universalOnnxRuntime = files(universalOnnxRuntimeAarFile)
    universalOnnxRuntime.builtBy(assembleUniversalOnnxRuntimeAar)
    add("universalImplementation", universalOnnxRuntime)
}
