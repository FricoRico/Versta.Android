package app.versta.translate.adapter.inbound

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

interface FileHashValidator {
    /**
     * Validates the hash of the file.
     * @param hashFile The path to the hash file.
     * @param cachedHashFile The hash to validate.
     * @return True if the hash is valid, false otherwise.
     */
    fun validate(hashFile: File, cachedHashFile: File): Boolean

    /**
     * Validates the hash of the file.
     * @param hashFile The path to the hash file.
     * @param cachedHashFile The hash to validate.
     * @return True if the hash is valid, false otherwise.
     */
    fun validate(hashFile: InputStream, cachedHashFile: File): Boolean

    /**
     * Stores the hash of the file in the specified output.
     * @param hashFile The path to the hash file.
     * @param cachedHashFile The path to the cached hash file.
     */
    fun archive(hashFile: Path, cachedHashFile: File)


    /**
     * Stores the hash of the file in the specified output.
     * @param hashFile The path to the hash file.
     * @param cachedHashFile The path to the cached hash file.
     */
    fun archive(hashFile: InputStream, cachedHashFile: File)
}