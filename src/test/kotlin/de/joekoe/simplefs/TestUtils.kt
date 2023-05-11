package de.joekoe.simplefs

import de.joekoe.simplefs.internal.SingleFileFileSystem
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.io.Reader
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.Path

internal inline fun withTempFile(test: (raf: RandomAccessFile, path: Path) -> Unit) {
    Files.createTempFile("test", ".tmp")
        .toFile()
        .also(File::deleteOnExit)
        .let { RandomAccessFile(it, "rw").use { raf -> test(raf, it.toPath()) } }
}

internal inline fun withFileSystem(test: (fs: SingleFileFileSystem) -> Unit) =
    withTempFile { raf -> SingleFileFileSystem(raf).use(test) }

internal inline fun withFileSystem(test: (fs: SingleFileFileSystem, path: Path) -> Unit) =
    withTempFile { raf, path -> test(SingleFileFileSystem(raf), path) }

internal inline fun withTempFile(test: (raf: RandomAccessFile) -> Unit) = withTempFile { raf, _ -> test(raf) }

internal fun ReadableByteChannel.consumeText() =
    Channels.newReader(this, Charsets.UTF_8)
        .use(Reader::readText)

internal fun ReadableByteChannel.consumeBytes() =
    Channels.newInputStream(this)
        .use(InputStream::readAllBytes)

internal fun FileChannel.copyTo(channel: WritableByteChannel) {
    var written = 0L
    while (written < size()) written = transferTo(written, size(), channel)
}
