package de.joekoe.simplefs.internal

import de.joekoe.simplefs.withTempFile
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.Channels
import kotlin.test.assertEquals

class ChannelIntegrationTest {
    @Test
    fun `read channel should return what write channel wrote`() = withTempFile { raf ->
        var written = -1L
        val text = "Hello, World!"
        SimpleFsWritableChannel(raf.channel, 0) { written = it }
            .use { it.write(ByteBuffer.wrap(text.toByteArray())) }
        val rc = SimpleFsReadableChannel(raf.channel, 0, written) {}

        val actual = Channels.newReader(rc, Charsets.UTF_8)
            .use { it.readText() }

        assertEquals(text, actual)
    }
}
