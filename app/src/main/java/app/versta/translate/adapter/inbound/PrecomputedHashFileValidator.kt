package app.versta.translate.adapter.inbound

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class PrecomputedHashFileValidator : FileHashValidator {
    /**
     * Validates the hash of the file.
     * @param hashFile The path to the hash file.
     * @param cachedHashFile The hash to validate.
     * @return True if the hash is valid, false otherwise.
     */
    override fun validate(hashFile: File, cachedHashFile: File): Boolean {
        return validate(hashFile.inputStream(), cachedHashFile)
    }

    override fun validate(
        hashFile: InputStream,
        cachedHashFile: File
    ): Boolean {
        if (!cachedHashFile.exists()) {
            return false
        }

        return hashFile.use { hashStream ->
            cachedHashFile.inputStream().use { cachedHashStream ->
                val hashBytes = hashStream.readBytes()
                val cachedHashBytes = cachedHashStream.readBytes()

                return hashBytes.contentEquals(cachedHashBytes)
            }
        }
    }

    /**
     * Stores the hash of the file in the specified output.
     * @param hashFile The path to the hash file.
     * @param cachedHashFile The path to the cached hash file.
     */
    override fun archive(hashFile: Path, cachedHashFile: File) {
        archive(hashFile.inputStream(), cachedHashFile)
    }

    /**
     * Stores the hash of the file in the specified output.
     * @param hashFile The path to the hash file.
     * @param cachedHashFile The path to the cached hash file.
     */
    override fun archive(hashFile: InputStream, cachedHashFile: File) {
        hashFile.copyTo(cachedHashFile.outputStream())
    }
}