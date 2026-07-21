package app.versta.translate.utils

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Total size in bytes of all files under this directory, recursively.
 */
fun Path.directorySize(): Long {
    var folderSize: Long = 0

    Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            folderSize += Files.size(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) {
                throw exc
            }

            return FileVisitResult.CONTINUE
        }
    })

    return folderSize
}
