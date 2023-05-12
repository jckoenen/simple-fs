package de.joekoe.simplefs.internal

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

internal val String.byteCount get() =
    toByteArray(Charsets.UTF_8).size.toLong()

internal fun ReadableByteChannel.copyTo(writeChannel: WritableByteChannel) {
    val buf = ByteBuffer.allocate(8 * 1024)
    while (read(buf) != -1) {
        buf.flip()
        writeChannel.write(buf)
        buf.compact()
    }
    buf.flip()
    while (buf.hasRemaining()) writeChannel.write(buf)
}
