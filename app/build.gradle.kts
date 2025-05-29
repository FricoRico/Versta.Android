import org.gradle.crypto.checksum.Checksum
import java.io.FileInputStream
import java.net.URI
import java.util.Properties

val keystoreProperties = Properties().apply {
    load(FileInputStream(rootProject.file("keystore.properties")))
}

plugins {
    alias(libs.plugins.about.libraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.gradle.crypto.checksum)
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
    }
}

android {
    namespace = "app.versta.translate"
    compileSdk = 36
    ndkVersion = "28.1.13356709"
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.versta.translate"
        minSdk = 27
        targetSdk = 36
        versionCode = 10
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        externalNativeBuild {
            cmake {
                targets("app_versta_translate_data", "app_versta_translate_bridge")
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("versta.keystore")
            storePassword = keystoreProperties["storePassword"].toString()
            keyAlias = "versta"
            keyPassword = keystoreProperties["keyPassword"].toString()
        }
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
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
    }
    externalNativeBuild {
        cmake {
            path = file("native/jni/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

tasks.apply {
    val dataArchiveName = "versta_data.tgz"

    register("checkData") {
        doFirst {
            assert(layout.buildDirectory.file("generated/espeak-ng-data/en_dict").isPresent)
            assert(layout.buildDirectory.file("generated/espeak-ng-data/intonations").isPresent)
            assert(layout.buildDirectory.file("generated/espeak-ng-data/phondata").isPresent)
            assert(layout.buildDirectory.file("generated/espeak-ng-data/phondata-manifest").isPresent)
            assert(layout.buildDirectory.file("generated/espeak-ng-data/phonindex").isPresent)
            assert(layout.buildDirectory.file("generated/espeak-ng-data/phontab").isPresent)
        }
    }

    register("createDataArchive", Tar::class) {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        compression = Compression.GZIP

        archiveFileName.set(dataArchiveName)
        destinationDirectory.set(layout.projectDirectory.dir("src/main/res/raw"))

        from(layout.buildDirectory.dir("generated/espeak-ng-data")) {
            into("espeak-ng-data")
        }
    }

    register("createDataHash", Checksum::class) {
        dependsOn("createDataArchive")

        checksumAlgorithm.set(Checksum.Algorithm.SHA256)
        inputFiles.setFrom(layout.projectDirectory.file("src/main/res/raw/$dataArchiveName"))
        outputDirectory.set(layout.buildDirectory.dir("intermediates/datahash"))
    }

    register("createDataVersion", Copy::class) {
        dependsOn("createDataHash")
        rename { "versta_data_hash.sha256" }

        from(layout.buildDirectory.file("intermediates/datahash/$dataArchiveName.sha256"))
        into(layout.projectDirectory.dir("src/main/res/raw"))
    }

    register("getLanguageModels") {
        doLast {
            URI("https://models.versta.app/translation/models.json").toURL().openStream()
                .use { input ->
                    layout.projectDirectory.file("src/main/res/raw/versta_translation_models.json").asFile.outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }

            URI("https://models.versta.app/text-to-speech/models.json").toURL().openStream()
                .use { input ->
                    layout.projectDirectory.file("src/main/res/raw/versta_text_to_speech_models.json").asFile.outputStream()
                        .use { output ->
                            input.copyTo(output)
                        }
                }
        }
    }

    preBuild {
        dependsOn("getLanguageModels")
    }
}

project.apply {
    afterEvaluate {
        tasks.named("checkData") {
            dependsOn("externalNativeBuildDebug")
        }
        tasks.named("createDataArchive") {
            dependsOn("externalNativeBuildDebug")
        }

        listOf(
            "mapDebugSourceSetPaths",
            "mergeDebugResources",
            "packageDebugResources",
            "generateDebugResources",
            "generateDebugDatabaseInterface",
            "mapReleaseSourceSetPaths",
            "mergeReleaseResources",
            "packageReleaseResources",
            "generateReleaseResources",
            "generateReleaseDatabaseInterface"
        ).forEach {
            tasks.named(it) {
                dependsOn("createDataVersion", "createDataArchive")
            }
        }
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
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
    implementation(libs.androidx.splash.screen)
    implementation(libs.appache.commons.compress)
    implementation(libs.atilika.kuromoji.ipadic)
    implementation(libs.jakewharton.timber)
    implementation(libs.jetbrains.bio.npy)
    implementation(libs.kotlinx.serialization)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.material.icons)
    implementation(libs.material.icons.extended)
    implementation(libs.navigation.compose)
    implementation(libs.squareup.okhttp)
    implementation(libs.sqldelight.android)
    implementation(libs.sqldelight.coroutines)

    implementation(libs.onnxruntime)
//    implementation(libs.onnxruntime.extensions)
}
