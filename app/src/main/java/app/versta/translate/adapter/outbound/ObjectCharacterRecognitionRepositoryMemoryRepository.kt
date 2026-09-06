package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionModule
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleFile
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleWithFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class ObjectCharacterRecognitionRepositoryMemoryRepository : ObjectCharacterRecognitionRepository {
    private val _mockPath = Path("")

    private val _modules = mutableMapOf(
        "paddle-ocr:PP-OCRv6_tiny_det" to ObjectCharacterRecognitionModuleWithFiles(
            id = "paddle-ocr:PP-OCRv6_tiny_det",
            bundleId = "paddle-ocr",
            path = _mockPath,
            module = ObjectCharacterRecognitionModule.Detector,
            languages = listOf("*"),
            version = "v2.0.0",
            size = 1234567,
            files = listOf(ObjectCharacterRecognitionModuleFile(inference = "PP-OCRv6_tiny_det_int8.mnn"))
        ),
        "paddle-ocr:PP-OCRv6_tiny_rec" to ObjectCharacterRecognitionModuleWithFiles(
            id = "paddle-ocr:PP-OCRv6_tiny_rec",
            bundleId = "paddle-ocr",
            path = _mockPath,
            module = ObjectCharacterRecognitionModule.Recognizer,
            languages = listOf("en", "nl"),
            version = "v2.0.0",
            size = 1234567,
            files = listOf(
                ObjectCharacterRecognitionModuleFile(
                    inference = "PP-OCRv6_tiny_rec_int8.mnn",
                    vocab = "PP-OCRv6_tiny_vocab.txt"
                )
            )
        )
    )

    override fun getModule(id: String) = _modules[id]

    override fun getModules(): Flow<List<ObjectCharacterRecognitionModuleWithFiles>> =
        flowOf(_modules.values.toList())

    override fun getCompleteBundle(): ObjectCharacterRecognitionBundleWithFiles =
        ObjectCharacterRecognitionBundleWithFiles(
            id = "paddle-ocr",
            path = _mockPath,
            version = "v2.0.0",
            languages = listOf("en", "nl"),
            modules = _modules.values.toList()
        )

    override fun getRecognizerForLanguage(language: Language) =
        _modules["paddle-ocr:PP-OCRv6_tiny_rec"]

    override fun upsertModule(metadata: ObjectCharacterRecognitionModuleModel) {
        return
    }

    override fun deleteBundle(id: String) {
        _modules.entries.removeAll { it.value.bundleId == id }
    }
}
