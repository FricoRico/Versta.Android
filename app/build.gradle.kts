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
        versionCode = 12
        versionName = "1.3.1"

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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
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
    register("getRemoteData") {
        doLast {
            // src/main/res/raw holds only generated, gitignored JSON, so a
            // clean checkout has no raw/ directory. Create it before writing;
            // without this every CI build fails at preBuild with
            // FileNotFoundException.
            layout.projectDirectory
                .dir("src/main/res/raw")
                .asFile
                .mkdirs()

            URI("https://models.versta.app/data/data.json")
                .toURL()
                .openStream()
                .use { input ->
                    layout.projectDirectory
                        .file("src/main/res/raw/versta_data.json")
                        .asFile
                        .outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }

            URI("https://models.versta.app/translation/models.json")
                .toURL()
                .openStream()
                .use { input ->
                    layout.projectDirectory
                        .file("src/main/res/raw/versta_translation_models.json")
                        .asFile
                        .outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }

            URI("https://models.versta.app/speech-recognition/models.json")
                .toURL()
                .openStream()
                .use { input ->
                    layout.projectDirectory
                        .file("src/main/res/raw/versta_speech_recognition_models.json")
                        .asFile
                        .outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }

            URI("https://models.versta.app/text-to-speech/models.json")
                .toURL()
                .openStream()
                .use { input ->
                    layout.projectDirectory
                        .file("src/main/res/raw/versta_text_to_speech_models.json")
                        .asFile
                        .outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }

            URI("https://models.versta.app/object-character-recognition/models.json")
                .toURL()
                .openStream()
                .use { input ->
                    layout.projectDirectory
                        .file("src/main/res/raw/versta_object_character_recognition_models.json")
                        .asFile
                        .outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }
        }
    }

    preBuild {
        dependsOn("getRemoteData")
    }
}

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
}
