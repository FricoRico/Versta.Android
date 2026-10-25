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
        versionCode = 14
        versionName = "1.3.2-beta.2"

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

// ---------------------------------------------------------------------------
// Baked model catalog gate
//
// `getRemoteData` writes whatever https://models.versta.app serves at build
// time straight into res/raw and everything downstream trusts it: each
// External*FileRepository parses its catalog inside a fire-and-forget
// CoroutineScope(Dispatchers.IO).launch at startup. An empty, truncated, or
// mid-publish registry response therefore ships as a broken beta (the
// 1.3.2-beta.1 incident: languages gone / zero-valid groups -> uncaught
// exception in that coroutine). `validateBakedCatalogs` runs right after the
// downloads and fails the build when a catalog cannot be decoded the way the
// app would decode it, or when it is structurally valid JSON but holds no
// usable definition.
//
// The checks deliberately mirror the app's own decode semantics: the
// ListSerializer roots in adapter/outbound/External*FileRepository.kt, the
// required (non-defaulted) constructor fields on the External*Definition
// classes, the isValid() rules in core/entity/External*Model.kt, and
// case-insensitive enum names (decodeEnumsCaseInsensitive = true). Divergence
// here must stay intentional.
// ---------------------------------------------------------------------------
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

    register("validateBakedCatalogs") {
        dependsOn("getRemoteData")

        doLast {
            fun fail(
                file: String,
                violations: List<String>,
            ): Nothing =
                throw GradleException(
                    buildString {
                        appendLine("Baked catalog $file failed validation (${violations.size} violation(s)):")
                        violations.forEach { appendLine("  - $it") }
                        appendLine()
                        append(
                            "Refusing to bake a broken catalog into the app. models.versta.app may be " +
                                "mid-publish or degraded; retry when the registry is healthy, or " +
                                "investigate the publish before rebuilding.",
                        )
                    },
                )

            fun decode(file: String): Any? {
                val target = layout.projectDirectory.file("src/main/res/raw/$file").asFile
                if (!target.isFile) fail(file, listOf("expected file is missing: ${target.path}"))
                if (target.length() == 0L) fail(file, listOf("file is empty"))
                return try {
                    groovy.json.JsonSlurper().parse(target)
                } catch (e: Exception) {
                    fail(file, listOf("not parseable as JSON: ${e.message}"))
                }
            }

            // Field decoders mirroring kotlinx.serialization constructor binding:
            // a required field that is absent or mistyped fails the app's decode.
            fun obj(value: Any?): Map<*, *>? = value as? Map<*, *>

            fun nonBlank(value: Any?): Boolean = (value as? String)?.isNotBlank() == true

            fun positiveNumber(value: Any?): Boolean = (value as? Number)?.toLong()?.let { it > 0 } == true

            fun isNumber(value: Any?) = value is Number

            fun isBool(value: Any?) = value is Boolean

            fun stringList(value: Any?): Boolean = value is List<*> && value.all { it is String && it.isNotBlank() }

            // decodeEnumsCaseInsensitive = true on the app side: enum entries
            // match by name, ignoring case (no @SerialName on the entries).
            fun enumList(
                value: Any?,
                allowed: Set<String>,
            ): Boolean = value is List<*> && value.all { it is String && allowed.any { a -> a.equals(it, ignoreCase = true) } }

            fun parseableUri(value: Any?): Boolean = (value as? String)?.let { runCatching { URI(it) }.isSuccess } == true

            fun checkDefinition(
                file: String,
                where: String,
                def: Any?,
                requiredNonBlank: List<String>,
                out: MutableList<String>,
            ): Map<*, *>? {
                val fields = obj(def)
                if (fields == null) {
                    out += "$where: definition is not a JSON object"
                    return null
                }
                requiredNonBlank.forEach {
                    if (!nonBlank(fields[it])) out += "$where: required field `$it` missing or blank"
                }
                if (!nonBlank(fields["bundle"]) || !parseableUri(fields["bundle"])) out += "$where: `bundle` missing, blank, or not a parseable URI"
                if (!nonBlank(fields["checksum"]) || !parseableUri(fields["checksum"])) out += "$where: `checksum` missing, blank, or not a parseable URI"
                if (!positiveNumber(fields["size"])) out += "$where: `size` missing or not a positive number"
                return fields
            }

            fun checkNonEmptyRoot(file: String): List<*>? {
                val root = decode(file)
                if (root !is List<*>) {
                    fail(file, listOf("root element must be a JSON array (app decodes a ListSerializer root)"))
                }
                if (root.isEmpty()) {
                    fail(file, listOf("root array is empty: the app treats catalogs as non-empty at startup"))
                }
                return root
            }

            fun finalize(
                file: String,
                violations: List<String>,
            ) {
                if (violations.isNotEmpty()) fail(file, violations)
            }

            // data.json -> List<ExternalDataDefinition>
            run {
                val file = "versta_data.json"
                val violations = mutableListOf<String>()
                checkNonEmptyRoot(file)?.forEachIndexed { i, def ->
                    val where = "data[$i]"
                    val fields =
                        checkDefinition(
                            file,
                            where,
                            def,
                            listOf("id", "type", "version"),
                            violations,
                        ) ?: return@forEachIndexed
                    // DataType entry names, matched case-insensitively by the app.
                    if (!setOf("TTS").any { it.equals(fields["type"] as? String, ignoreCase = true) }) {
                        violations += "$where: `type` is not a known DataType entry"
                    }
                }
                finalize(file, violations)
            }

            // translation/models.json -> List<List<ExternalLanguageModelDefinition>>
            run {
                val file = "versta_translation_models.json"
                val violations = mutableListOf<String>()
                checkNonEmptyRoot(file)?.forEachIndexed { g, group ->
                    val where = "translation[$g]"
                    if (group !is List<*>) {
                        violations += "$where: language pair group is not a JSON array"
                        return@forEachIndexed
                    }
                    if (group.isEmpty()) {
                        // An empty group passes Definitions.isValid() but crashes
                        // mapLanguageModelDefinitionToDownloadableLanguageModel on
                        // models.first().
                        violations += "$where: language pair group is empty"
                        return@forEachIndexed
                    }
                    val decoded = mutableListOf<Map<*, *>>()
                    group.forEachIndexed { i, def ->
                        val fields =
                            checkDefinition(
                                file,
                                "$where[$i]",
                                def,
                                listOf("base_model", "source_language", "target_language", "version"),
                                violations,
                            ) ?: return@forEachIndexed
                        if (!isBool(fields["bidirectional"])) violations += "$where[$i]: required field `bidirectional` missing or not a boolean"
                        if (!isNumber(fields["score"])) violations += "$where[$i]: required field `score` missing or not a number"
                        if (!enumList(fields["architectures"], setOf("MarianMTModel", "BergamotTinyModel"))) {
                            violations += "$where[$i]: `architectures` missing or contains unknown LanguageModelArchitecture entries"
                        }
                        decoded += fields
                    }
                    // Pair-consistency rule (ExternalLanguageModelDefinitions.isValid):
                    // only when every definition in the group is bidirectional.
                    if (decoded.size != group.size || decoded.any { it["bidirectional"] != true }) return@forEachIndexed
                    if (decoded.size != 2) {
                        violations += "$where: all-bidirectional pair group must hold exactly 2 mirrored definitions, found ${decoded.size}"
                        return@forEachIndexed
                    }
                    val a = decoded[0]
                    val b = decoded[1]
                    if (a["source_language"] != b["target_language"] ||
                        a["target_language"] != b["source_language"] ||
                        a["size"] != b["size"] ||
                        a["version"] != b["version"] ||
                        a["bundle"] != b["bundle"] ||
                        a["checksum"] != b["checksum"]
                    ) {
                        violations += "$where: bidirectional definitions are not a mirrored pair (ExternalLanguageModelDefinitions.isValid rule)"
                    }
                }
                finalize(file, violations)
            }

            // speech-recognition/models.json -> List<ExternalSpeechRecognitionModelDefinition>
            run {
                val file = "versta_speech_recognition_models.json"
                val violations = mutableListOf<String>()
                checkNonEmptyRoot(file)?.forEachIndexed { i, def ->
                    val where = "speechRecognition[$i]"
                    val fields =
                        checkDefinition(
                            file,
                            where,
                            def,
                            listOf("id", "name", "base_model", "version"),
                            violations,
                        ) ?: return@forEachIndexed
                    if (!enumList(fields["architectures"], setOf("Whisper"))) {
                        violations += "$where: `architectures` missing or contains unknown SpeechRecognitionArchitecture entries"
                    }
                    if ((fields["architectures"] as? List<*>).isNullOrEmpty()) {
                        violations += "$where: `architectures` must be non-empty (isValid rule)"
                    }
                    if (!stringList(fields["languages"]) || (fields["languages"] as? List<*>).isNullOrEmpty()) {
                        violations += "$where: `languages` missing or empty"
                    }
                }
                finalize(file, violations)
            }

            // text-to-speech/models.json -> List<ExternalVoiceModelDefinition>
            run {
                val file = "versta_text_to_speech_models.json"
                val violations = mutableListOf<String>()
                checkNonEmptyRoot(file)?.forEachIndexed { i, def ->
                    val where = "voice[$i]"
                    val fields =
                        checkDefinition(
                            file,
                            where,
                            def,
                            listOf("id", "name", "base_model", "version"),
                            violations,
                        ) ?: return@forEachIndexed
                    // `voices` and `architectures` are defaulted ctor parameters:
                    // optional keys, but entries must still decode.
                    fields["architectures"]?.let {
                        if (!enumList(it, setOf("Kokoro", "StyleTTS2"))) {
                            violations += "$where: `architectures` contains unknown VoiceModelArchitecture entries"
                        }
                    }
                    fields["voices"]?.let { voices ->
                        if (voices !is List<*>) {
                            violations += "$where: `voices` is not a JSON array"
                            return@let
                        }
                        voices.forEachIndexed { v, voice ->
                            val vf = obj(voice)
                            if (vf == null) {
                                violations += "$where.voices[$v]: not a JSON object"
                                return@forEachIndexed
                            }
                            if (!setOf("Male", "Female").any { it.equals(vf["gender"] as? String, ignoreCase = true) }) {
                                violations += "$where.voices[$v]: `gender` is not a known VoiceGender entry"
                            }
                            if (!nonBlank(vf["language"])) violations += "$where.voices[$v]: `language` missing or blank"
                        }
                    }
                }
                finalize(file, violations)
            }

            // object-character-recognition/models.json ->
            // List<ExternalObjectCharacterRecognitionModelDefinition>
            run {
                val file = "versta_object_character_recognition_models.json"
                val violations = mutableListOf<String>()
                checkNonEmptyRoot(file)?.forEachIndexed { i, def ->
                    val where = "ocr[$i]"
                    val fields =
                        checkDefinition(
                            file,
                            where,
                            def,
                            listOf("id", "name", "base_model", "version"),
                            violations,
                        ) ?: return@forEachIndexed
                    if (!enumList(fields["architectures"], setOf("PaddleOCR"))) {
                        violations += "$where: `architectures` missing or contains unknown ObjectCharacterRecognitionArchitecture entries"
                    }
                    if ((fields["architectures"] as? List<*>).isNullOrEmpty()) {
                        violations += "$where: `architectures` must be non-empty (isValid rule)"
                    }
                    if (!stringList(fields["languages"]) || (fields["languages"] as? List<*>).isNullOrEmpty()) {
                        violations += "$where: `languages` missing or empty"
                    }
                }
                finalize(file, violations)
            }
        }
    }

    preBuild {
        dependsOn("validateBakedCatalogs")
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
