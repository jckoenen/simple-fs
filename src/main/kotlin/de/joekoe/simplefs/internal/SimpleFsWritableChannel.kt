package de.joekoe.simplefs.internal

import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import java.util.concurrent.atomic.AtomicBoolean

internal class SimpleFsWritableChannel(
    private val underlying: FileChannel,
    private val start: Long,
    private val onClose: (Long) -> Unit
) : WritableByteChannel {
    private val open = AtomicBoolean(true)
    private var written = 0L

    override fun close() {
        if (open.compareAndSet(true, false)) onClose(written)
    }

    override fun isOpen(): Boolean = open.get() && underlying.isOpen

    override fun write(src: ByteBuffer): Int {
        if (!isOpen) throw ClosedChannelException()

        return synchronized(this) {
            underlying.write(src, start + written)
                .also { written += it }
        }
    }
}
