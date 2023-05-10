package de.joekoe.simplefs.internal

import de.joekoe.simplefs.consumeText
import de.joekoe.simplefs.withTempFile
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleFsReadableChannelTest {

    @Test
    fun `onClose should only be called once`() = withTempFile { raf ->
        val closeCounter = AtomicInteger(0)
        val subject = SimpleFsReadableChannel(raf.channel, 0, 0, closeCounter::incrementAndGet)
        assertEquals(closeCounter.get(), 0)

        List(10) { thread(start = false) { repeat(10) { subject.close() } } }
            .onEach { it.start() }
            .forEach { it.join() }

        assertEquals(closeCounter.get(), 1)
    }

    @Test
    fun `close should not close underlying channel`() = withTempFile { raf ->
        val subject = SimpleFsReadableChannel(raf.channel, 0, 0)
        assertTrue(subject.isOpen)

        subject.close()
        assertFalse(subject.isOpen)
        assertTrue(raf.channel.isOpen)
    }

    @Test
    fun `read should start at offset and not read past given size`() = withTempFile { raf ->
        val foo = "foo"
        val bar = "bar"
        raf.writeBytes(foo)
        raf.writeBytes(bar)

        val actualFoo = SimpleFsReadableChannel(raf.channel, 0, foo.byteCount)
            .consumeText()
        assertEquals(foo, actualFoo)

        val actualBar = SimpleFsReadableChannel(raf.channel, foo.byteCount, bar.byteCount)
            .consumeText()

        assertEquals(bar, actualBar)
    }
}
