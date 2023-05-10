package de.joekoe.simplefs

import java.io.File
import java.io.RandomAccessFile
import java.io.Reader
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.file.Files
import java.nio.file.Path

internal inline fun withTempFile(test: (raf: RandomAccessFile, path: Path) -> Unit) {
    Files.createTempFile("test", ".tmp")
        .toFile()
        .also(File::deleteOnExit)
        .let { RandomAccessFile(it, "rw").use { raf -> test(raf, it.toPath()) } }
}

internal inline fun withTempFile(test: (raf: RandomAccessFile) -> Unit) = withTempFile { raf, _ -> test(raf) }

internal fun ReadableByteChannel.consumeText() =
    Channels.newReader(this, Charsets.UTF_8)
        .use(Reader::readText)
