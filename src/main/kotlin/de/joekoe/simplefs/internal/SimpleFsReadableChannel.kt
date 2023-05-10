package de.joekoe.simplefs.internal

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.util.concurrent.atomic.AtomicBoolean

internal class SimpleFsReadableChannel(
    private val underlying: FileChannel,
    private val start: Long,
    private val size: Long,
    private val onClose: () -> Unit = {}
) : ReadableByteChannel {
    private var read = 0L
    private val open = AtomicBoolean(true)

    override fun close() {
        if (open.compareAndSet(true, false)) onClose()
    }

    override fun isOpen(): Boolean = open.get() && underlying.isOpen

    override fun read(dst: ByteBuffer): Int {
        if (!isOpen) throw ClosedChannelException()

        return synchronized(this) {
            if (read == size) return -1
            dst.limit(minOf(dst.limit(), (size - read).toInt()))
            val res = underlying.read(dst, start + read)
            if (res != -1) read += res

            res
        }
    }
}
