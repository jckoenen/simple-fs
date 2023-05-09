package de.joekoe.simplefs

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

internal inline fun withTempFile(test: (raf: RandomAccessFile) -> Unit) {
    Files.createTempFile("test", ".tmp")
        .toFile()
        .also(File::deleteOnExit)
        .let { RandomAccessFile(it, "rw") }
        .use(test)
}

internal val String.byteCount get() =
    toByteArray(Charsets.UTF_8).size.toLong()
