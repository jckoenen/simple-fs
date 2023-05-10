package de.joekoe.simplefs.internal.direcotry

import de.joekoe.simplefs.internal.directory.DirectoryBlock
import de.joekoe.simplefs.internal.directory.DirectoryEntry
import de.joekoe.simplefs.withTempFile
import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectoryBlockTest {
    private val fileA = DirectoryEntry.FilePointer("fileA.txt", offset = 456, size = 123)
    private val fileB = DirectoryEntry.FilePointer("fileB", offset = 0, size = 0)

    private val dirA = DirectoryEntry.DirectoryPointer("dirA", offset = 123)
    private val dirB = DirectoryEntry.DirectoryPointer("dirB", offset = 255)

    @Test
    fun `empty blocks should be queryable`() = withTempFile { raf ->
        assertTrue(DirectoryBlock(raf.channel, 0).allEntries().isEmpty())
    }

    @Test
    fun `should read and write single file entry correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0)
        subject.addOrReplace(fileA)

        assertEquals(fileA, subject.allEntries().single())
    }

    @Test
    fun `should delete file entry correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0)
        subject.addOrReplace(fileA)
        subject.delete(fileA.relativeName)

        assertTrue(subject.allEntries().isEmpty())
    }

    @Test
    fun `should read and write single directory entry correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0)
        subject.addOrReplace(dirA)

        assertEquals(dirA, subject.allEntries().single())
    }

    @Test
    fun `should delete directory entry correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0)
        subject.addOrReplace(dirA)
        subject.delete(dirA.relativeName)

        assertTrue(subject.allEntries().isEmpty())
    }

    @Test
    fun `should read and write multiple heterogeneous entries correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0)
        val data = listOf(fileA, dirB, dirA, fileB)
        data.forEach(subject::addOrReplace)

        assertTrue(subject.allEntries().containsAll(data))
    }

    @Test
    fun `entries should be persisted across closed files`() = withTempFile { raf, path ->
        val data = listOf(fileA, dirB)
        data.forEach(DirectoryBlock(raf.channel, 0)::addOrReplace)
        raf.close()

        val beforeDelete = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)
            .use {
                val b = DirectoryBlock(it, 0)
                val entries = b.allEntries()
                b.delete(dirB.relativeName)
                entries
            }

        val afterDelete = FileChannel.open(path)
            .use { DirectoryBlock(it, 0).allEntries() }

        assertTrue(beforeDelete.containsAll(data))
        assertEquals(1, afterDelete.size)
        assertEquals(fileA, afterDelete.single())
    }
}
