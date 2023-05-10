package de.joekoe.simplefs.internal

import de.joekoe.simplefs.withTempFile
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleFsWritableChannelTest {
    @Test
    fun `onClose should only be called once`() = withTempFile { raf ->
        val closeCounter = AtomicInteger(0)
        val subject = SimpleFsWritableChannel(raf.channel, 0) { closeCounter.incrementAndGet() }
        assertEquals(closeCounter.get(), 0)

        List(10) { thread(start = false) { repeat(10) { subject.close() } } }
            .onEach { it.start() }
            .forEach { it.join() }

        assertEquals(closeCounter.get(), 1)
    }

    @Test
    fun `close should not close underlying channel`() = withTempFile { raf ->
        val subject = SimpleFsWritableChannel(raf.channel, 0) {}
        assertTrue(subject.isOpen)

        subject.close()
        assertFalse(subject.isOpen)
        assertTrue(raf.channel.isOpen)
    }

    @Test
    fun `close should invoke callback with number of bytes written`() = withTempFile { raf ->
        var written = -1L
        val text = "test"
        val subject = SimpleFsWritableChannel(raf.channel, 0) { written = it }

        subject.write(ByteBuffer.wrap(text.toByteArray()))
        subject.close()

        assertEquals(text.byteCount, written)
    }
}
