package de.joekoe.simplefs.internal

import de.joekoe.simplefs.consumeText
import de.joekoe.simplefs.copyTo
import de.joekoe.simplefs.withTempFile
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelIntegrationTest {
    @Test
    fun `read channel should return string that write channel wrote`() = withTempFile { raf ->
        var written = -1L
        val text = "Hello, World!"
        SimpleFsWritableChannel(raf.channel, 0) { written = it }
            .use { it.write(ByteBuffer.wrap(text.toByteArray())) }

        val actual = SimpleFsReadableChannel(raf.channel, 0, written)
            .consumeText()

        assertEquals(text, actual)
    }

    @Test
    fun `read channel should return content that write channel wrote`() = withTempFile { raf ->
        var written = -1L
        val thisFile = Path("src/test/kotlin/de/joekoe/simplefs/internal/ChannelIntegrationTest.kt")
        assertTrue(thisFile.exists())

        FileChannel.open(thisFile)
            .use { fc ->
                SimpleFsWritableChannel(raf.channel, 0) { written = it }
                    .use { wc -> fc.copyTo(wc) }

                val actual = SimpleFsReadableChannel(raf.channel, 0, written)
                    .consumeText()
                val expect = Channels.newReader(fc, Charsets.UTF_8)
                    .readText()

                assertEquals(expect, actual)
            }
    }
}
