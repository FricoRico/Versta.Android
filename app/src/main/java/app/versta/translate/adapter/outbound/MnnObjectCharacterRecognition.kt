package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.inference.OcrEngine
import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionModule
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleWithFiles
import app.versta.translate.core.entity.OcrAnalysisResult
import java.nio.ByteBuffer
import kotlin.io.path.pathString

/**
 * OCR inference over the native MNN pipeline ([OcrEngine]). All modules of the
 * installed bundle are registered once; recognizers load lazily per script.
 *
 * [close] destroys the native engine (its reloc worker thread and any loaded
 * models); [load] recreates it, so bundle swaps never leave native state
 * behind.
 */
class MnnObjectCharacterRecognition : ObjectCharacterRecognitionInference, AutoCloseable {
    private var _engine: OcrEngine? = OcrEngine()

    private var _bundleId: String? = null

    @Volatile
    private var _loaded = false

    override fun load(bundle: ObjectCharacterRecognitionBundleWithFiles, threads: Int) {
        if (_bundleId == bundle.id && _loaded) {
            return
        }

        close()
        val engine = OcrEngine()
        _engine = engine

        val detector = bundle.module(ObjectCharacterRecognitionModule.Detector)
            ?: throw IllegalStateException("OCR bundle ${bundle.id} has no detector module")

        // The half-resolution det head is exact and ~2x faster; the full head
        // is only worth it on still captures at extreme resolutions.
        val detectorFile = detector.files.firstOrNull { it.inference.contains("_half_") }
            ?: detector.files.firstOrNull()
        detectorFile ?: throw IllegalStateException("OCR detector module has no model file")

        engine.setDetector(
            detector.inferencePath(detectorFile).pathString,
            threads
        )

        bundle.module(ObjectCharacterRecognitionModule.ScriptClassifier)?.let { module ->
            module.preferredFile()?.let {
                engine.setScriptClassifier(module.inferencePath(it).pathString, scriptRoutes(bundle))
            }
        }

        bundle.module(ObjectCharacterRecognitionModule.GlyphMatte)?.let { module ->
            module.preferredFile()?.let { engine.setGlyphMatte(module.inferencePath(it).pathString) }
        }

        bundle.module(ObjectCharacterRecognitionModule.Aligner)?.let { module ->
            module.preferredFile()?.let { engine.setAligner(module.inferencePath(it).pathString) }
        }

        bundle.recognizers().forEach { module ->
            module.preferredFile()?.let { file ->
                val vocab = module.vocabPath(file)
                    ?: throw IllegalStateException("OCR recognizer ${module.id} has no vocab file")
                engine.addRecognizer(
                    module.path.fileName.toString(),
                    module.inferencePath(file).pathString,
                    vocab.pathString
                )
            }
        }

        _bundleId = bundle.id
        _loaded = true
    }

    override fun analyzeLive(input: ByteBuffer, width: Int, height: Int, forcedRecognizer: String?): OcrAnalysisResult {
        if (!_loaded) {
            throw IllegalStateException("OCR engine is not loaded")
        }

        val engine = _engine ?: throw IllegalStateException("OCR engine is closed")
        val lines = engine.analyze(
            input,
            width,
            height,
            /* rotationDegrees */ 0,
            /* profile */ PROFILE_LIVE,
            forcedRecognizer
        ) ?: emptyArray()

        return OcrAnalysisResult(
            lines = lines.map { it.toLineResult() },
            width = width,
            height = height,
        )
    }

    override fun tickLive(input: ByteBuffer, width: Int, height: Int): ObjectCharacterRecognitionInference.LiveTick? {
        if (!_loaded) return null

        val engine = _engine ?: return null
        val packed = engine.tick(input, width, height) ?: return null
        return ObjectCharacterRecognitionInference.LiveTick(
            homography = packed.copyOfRange(0, 9),
            anchorEpoch = packed[9].toInt(),
            contentVersion = packed[10].toInt(),
        )
    }

    override fun pullLiveContent(width: Int, height: Int): OcrAnalysisResult? {
        if (!_loaded) return null

        val lines = (_engine ?: return null).liveContent() ?: return null
        return OcrAnalysisResult(
            lines = lines.map { it.toLineResult() },
            width = width,
            height = height,
        )
    }

    override fun analyzeStill(input: ByteBuffer, width: Int, height: Int, rotationDegrees: Int, forcedRecognizer: String?): OcrAnalysisResult {
        if (!_loaded) {
            throw IllegalStateException("OCR engine is not loaded")
        }

        val engine = _engine ?: throw IllegalStateException("OCR engine is closed")
        val lines = engine.analyze(
            input,
            width,
            height,
            rotationDegrees,
            /* profile */ PROFILE_STILL,
            forcedRecognizer
        ) ?: emptyArray()

        val (frameWidth, frameHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
            height to width
        } else {
            width to height
        }

        return OcrAnalysisResult(
            lines = lines.map { it.toLineResult() },
            width = frameWidth,
            height = frameHeight
        )
    }

    override fun probeLive(input: ByteBuffer, width: Int, height: Int) {
        if (!_loaded) return
        _engine?.probeLive(input, width, height)
    }

    override fun cancel() = Unit

    override fun close() {
        _loaded = false
        _bundleId = null
        _engine?.close()
        _engine = null
    }

    companion object {
        private const val PROFILE_LIVE = 1
        private const val PROFILE_STILL = 0

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}

private fun ObjectCharacterRecognitionModuleWithFiles.preferredFile() =
    files.maxByOrNull { it.priority }

/**
 * Maps each PULC script class (in the model's fixed output order) to the
 * recognizer module directory that handles it — PP-OCRv6 generations
 * preferred, v5 per-script recognizers as fallback. Empty = no recognizer
 * installed for that class.
 */
private fun scriptRoutes(bundle: ObjectCharacterRecognitionBundleWithFiles): Array<String> {
    val dirs = bundle.recognizers().map { it.path.fileName.toString() }
    fun find(predicate: (String) -> Boolean) = dirs.firstOrNull(predicate) ?: ""

    return arrayOf(
        /* arabic     */ find { it.startsWith("arabic_") },
        /* chinese    */ dirs.firstOrNull { it.contains("small_rec") } ?: find { it == "PP-OCRv5_mobile_rec" },
        /* cyrillic   */ find { it.startsWith("cyrillic_") }.ifEmpty { find { it.startsWith("eslav_") } },
        /* devanagari */ find { it.startsWith("devanagari_") },
        /* japanese   */ dirs.firstOrNull { it.contains("small_rec") } ?: find { it == "PP-OCRv5_mobile_rec" },
        /* kannada    */ find { it.startsWith("indic") },
        /* korean     */ find { it.startsWith("korean_") },
        /* tamil      */ find { it.startsWith("ta_") },
        /* telugu     */ find { it.startsWith("te_") },
        /* latin      */ dirs.firstOrNull { it.contains("tiny_rec") } ?: find { it.startsWith("latin_") }
    )
}
