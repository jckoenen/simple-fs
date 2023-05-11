package de.joekoe.simplefs

import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileNodeTest {
    private val thisFile = Path("src/test/kotlin/de/joekoe/simplefs/FileNodeTest.kt")

    @Test
    fun `creating files in the root directory should work`() = withFileSystem { fs ->
        val actual = fs.createFile(SimplePath.of("foo"))
        assertEquals("foo", actual.name)
        assertEquals(SimplePath.of("/foo"), actual.absolutePath)
    }

    @Test
    fun `using deleted files should fail`() = withFileSystem { fs ->
        val subject = fs.createFile(SimplePath.of("foo"))
        subject.delete()

        assertFailsWith<IllegalStateException> { subject.readChannel() }
        assertFailsWith<IllegalStateException> { subject.writeChannel() }
        assertFailsWith<IllegalStateException> { subject.appendChannel() }
        assertFailsWith<IllegalStateException> { subject.rename(SimplePath.Segment.of("bar")) }
        val dir = fs.createDirectory(SimplePath.of("dir"))
        assertFailsWith<IllegalStateException> { subject.moveTo(dir) }
    }

    @Test
    fun `written content should be readable`() = withFileSystem { fs ->
        FileChannel.open(thisFile)
            .use { source ->
                val subject = fs.createFile(SimplePath.of("foo"))
                subject.writeChannel().use(source::copyTo)

                val expected = source.consumeText()
                val actual = subject.readChannel().consumeText()

                assertEquals(expected, actual)
            }
    }

    @Test
    fun `appended content should be readable`() = withFileSystem { fs ->
        FileChannel.open(thisFile)
            .use { source ->
                val subject = fs.createFile(SimplePath.of("foo"))
                subject.writeChannel().use(source::copyTo)
                subject.appendChannel().use(source::copyTo)

                val expected = source.consumeText()
                val actual = subject.readChannel().consumeText()

                assertEquals(expected + expected, actual)
            }
    }

    @Test
    fun `renaming should not affect content`() = withFileSystem { fs ->
        FileChannel.open(thisFile)
            .use { source ->
                val subject = fs.createFile(SimplePath.of("foo"))
                subject.writeChannel().use(source::copyTo)

                val before = subject.readChannel().consumeText()
                subject.rename(SimplePath.Segment.of("bar"))
                val after = subject.readChannel().consumeText()

                assertEquals(SimplePath.of("/bar"), subject.absolutePath)
                assertEquals("bar", subject.name)
                assertEquals(before, after)
            }
    }

    @Test
    fun `moving should be reflected in old and new parents`() = withFileSystem { fs ->
        val old = fs.createDirectory(SimplePath.of("old"))
        val new = fs.createDirectory(SimplePath.of("new"))

        val subject = old.createFile(SimplePath.Segment.of("test"))
        subject.moveTo(new)

        assertTrue(old.children().toList().isEmpty())
        assertEquals(subject, new.children().single())
    }

    @Test
    fun `moving should not affect content`() = withFileSystem { fs ->
        FileChannel.open(thisFile)
            .use { source ->
                val subject = fs.createFile(SimplePath.of("foo"))
                subject.writeChannel().use(source::copyTo)

                val before = subject.readChannel().consumeText()
                val newParent = fs.createDirectory(SimplePath.of("dir"))

                subject.moveTo(newParent)

                val after = subject.readChannel().consumeText()

                assertEquals(SimplePath.of("/dir/foo"), subject.absolutePath)
                assertEquals("foo", subject.name)
                assertEquals(before, after)
            }
    }
}
