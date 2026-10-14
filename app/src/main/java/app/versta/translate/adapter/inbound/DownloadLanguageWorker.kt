package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication
import app.versta.translate.bridge.leanmt.LeanmtVersion
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.IncompatibleModelBundleException
import app.versta.translate.core.entity.LanguageBundleData
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.LanguageModelMetadata
import timber.log.Timber
import java.io.File
import java.util.UUID

const val DOWNLOAD_LANGUAGE_STATUS_INTENT = "DOWNLOAD_LANGUAGE_STATUS_UPDATE"

class DownloadLanguageWorker(
    context: Context,
    parameters: WorkerParameters,
) : DownloadWorker(context, parameters) {
    override val downloadStatusIntent = Intent(DOWNLOAD_LANGUAGE_STATUS_INTENT)

    private val _extractionDirectory = context.filesDir

    private val _languageExtractor = MainApplication.module.extractor
    private val _languageRepository = MainApplication.module.languageRepository

    /**
     * Extracts the downloaded language model.
     */
    override fun extractDownload(
        taskId: UUID,
        file: File,
    ) {
        var output: File? = null

        try {
            output =
                _languageExtractor.extract(
                    file = file,
                    outputDir = _extractionDirectory,
                )

            if (!file.delete()) {
                Timber.tag(TAG).e("Deleting file ${file.absolutePath}")
            }

            val metadata = readMetadata(output)
            _languageRepository.upsertLanguageModels(metadata)
        } catch (e: Exception) {
            output?.deleteRecursively()
            setStatus(taskId, DownloadStatus.Error(e))
            Timber.tag(TAG).e(e, "Extracting file ${file.absolutePath}")
        }
    }

    /**
     * Reads the metadata file from the extracted model.
     */
    private fun readMetadata(output: File?): LanguageBundleData {
        if (output == null) {
            throw Exception("Output file is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val languageBundleMetadata =
            _serializer.decodeFromString<LanguageBundleMetadata>(bundleMetadataFile.readText())

        if (!languageBundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        preFlightBinaryVersion(languageBundleMetadata)

        val languageModelMetadata =
            languageBundleMetadata.metadata.map {
                val languageMetadataFile = File(output.resolve(it.directory), "metadata.json")

                _serializer
                    .decodeFromString<LanguageModelMetadata>(languageMetadataFile.readText())
                    .setRootPath(
                        path = output.resolve(it.directory).toPath(),
                    )
            }

        if (languageModelMetadata.any { !it.isValid() }) {
            throw Exception("Invalid language metadata file")
        }

        return LanguageBundleData(
            bundle = languageBundleMetadata,
            languages = languageModelMetadata,
        )
    }

    /**
     * Gates the install on the bundle's declared leanmt binary format version
     * against the vendored leanmt's `kBinaryFileVersion`, queried from the
     * compiled library. Runs before any JNI model load so an incompatible
     * bundle fails with a clean error instead of leanmt's fail-fast native
     * error at first use. Bundles pre-dating v2.1.0 lack the field and are
     * rejected the same way; a re-download only resolves them once the catalog
     * serves refreshed bundles.
     */
    private fun preFlightBinaryVersion(metadata: LanguageBundleMetadata) {
        val supported = LeanmtVersion().binaryFileVersion()

        Timber
            .tag(TAG)
            .i(
                "Bundle declares leanmt_binary_version ${metadata.leanmtBinaryVersion}, " +
                    "vendored leanmt supports $supported",
            )

        if (!metadata.isBinaryVersionSupported(supported)) {
            throw IncompatibleModelBundleException(
                declared = metadata.leanmtBinaryVersion,
                supported = supported,
            )
        }
    }

    companion object {
        private val TAG = DownloadLanguageWorker::class.java.simpleName
    }
}
