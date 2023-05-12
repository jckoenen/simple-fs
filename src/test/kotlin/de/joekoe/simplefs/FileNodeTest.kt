package de.joekoe.simplefs

import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import kotlin.io.path.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileNodeTest {
    private val thisFile = Path("src/test/kotlin/de/joekoe/simplefs/FileNodeTest.kt")

    private val testPath = SimplePath.of("/foo")

    @Test
    fun `creating files in the root directory should work`() = withFileSystem { fs ->
        val actual = fs.createFile(testPath)
        assertEquals("foo", actual.name)
        assertEquals(SimplePath.of("/foo"), actual.absolutePath)
    }

    @Test
    fun `using deleted files should fail`() = withFileSystem { fs ->
        val subject = fs.createFile(testPath)
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
                val subject = fs.createFile(testPath)
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
                val subject = fs.createFile(testPath)
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
                val subject = fs.createFile(testPath)
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

        assertTrue(old.children().none())
        assertEquals(subject, new.children().single())
    }

    @Test
    fun `moving should not affect content`() = withFileSystem { fs ->
        FileChannel.open(thisFile)
            .use { source ->
                val subject = fs.createFile(testPath)
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

    @Test
    fun `changes should be reflected in all references to the same file`() = withFileSystem { fs ->
        val nodes = generateSequence(fs.createFile(testPath) as SimpleFileSystemNode?) {
            fs.open(testPath)
        }
            .map { assertIs<FileNode>(it) }
            .take(10)
            .toList()

        nodes.zipWithNext().forEach { (l, r) -> assertEquals(l, r) }

        val expectedContent = FileChannel.open(thisFile)
            .use { source ->
                nodes[nodes.size / 2].writeChannel().use(source::copyTo)

                source.consumeBytes()
            }

        val rename = SimplePath.Segment.of("renamed")
        nodes[nodes.size / 3].rename(rename)

        val newParent = fs.createDirectory(SimplePath.of("dir"))
        nodes[nodes.size / 5].moveTo(newParent)
        val expectedPath = newParent.absolutePath.child(rename)

        nodes.forEach { node ->
            assertEquals(expectedPath, node.absolutePath)
            assertContentEquals(expectedContent, node.readChannel().consumeBytes())
        }
    }

    @Test
    fun `deleting and recreating a file should not show old content`() = withFileSystem { fs ->
        val beforeDeletion = fs.createFile(testPath)

        FileChannel.open(thisFile)
            .use { source -> beforeDeletion.writeChannel().use(source::copyTo) }

        beforeDeletion.delete()

        assertTrue(fs.createFile(testPath).readChannel().consumeText().isEmpty())
        assertFails { beforeDeletion.readChannel().consumeText() }
    }
}
