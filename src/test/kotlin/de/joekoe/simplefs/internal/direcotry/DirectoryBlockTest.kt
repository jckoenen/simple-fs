package de.joekoe.simplefs.internal.direcotry

import de.joekoe.simplefs.SimplePath
import de.joekoe.simplefs.SimplePath.Segment
import de.joekoe.simplefs.internal.directory.DirectoryBlock
import de.joekoe.simplefs.internal.directory.DirectoryEntry
import de.joekoe.simplefs.withTempFile
import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class DirectoryBlockTest {
    private val fileA = DirectoryEntry.FilePointer(Segment.of("fileA.txt"), offset = 456, size = 123)
    private val fileB = DirectoryEntry.FilePointer(Segment.of("fileB"), offset = 0, size = 0)

    private val dirA = DirectoryEntry.DirectoryPointer(Segment.of("dirA"), offset = 123)
    private val dirB = DirectoryEntry.DirectoryPointer(Segment.of("dirB"), offset = 255)

    @Test
    fun `empty blocks should be queryable`() = withTempFile { raf ->
        assertTrue(DirectoryBlock(raf.channel, 0, SimplePath.ROOT).allEntries().isEmpty())
    }

    @Test
    fun `should read and write single file entry correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0, SimplePath.ROOT)
        subject.addOrReplace(fileA)

        assertEquals(fileA, subject.allEntries().single())
    }

    @Test
    fun `should delete file entry correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0, SimplePath.ROOT)
        subject.addOrReplace(fileA)
        subject.unlink(fileA.relativeName)

        assertTrue(subject.allEntries().isEmpty())
    }

    @Test
    fun `should read and write single directory entry correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0, SimplePath.ROOT)
        subject.addOrReplace(dirA)

        assertEquals(dirA, subject.allEntries().single())
    }

    @Test
    fun `should delete directory entry correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0, SimplePath.ROOT)
        subject.addOrReplace(dirA)
        subject.unlink(dirA.relativeName)

        assertTrue(subject.allEntries().isEmpty())
    }

    @Test
    fun `should read and write multiple heterogeneous entries correctly`() = withTempFile { raf ->
        val subject = DirectoryBlock(raf.channel, 0, SimplePath.ROOT)
        val data = listOf(fileA, dirB, dirA, fileB)
        data.forEach(subject::addOrReplace)

        assertTrue(subject.allEntries().containsAll(data))
    }

    @Test
    fun `entries should be persisted across closed files`() = withTempFile { raf, path ->
        val data = listOf(fileA, dirB)
        data.forEach(DirectoryBlock(raf.channel, 0, SimplePath.ROOT)::addOrReplace)
        raf.close()

        val beforeDelete = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)
            .use {
                val b = DirectoryBlock(it, 0, SimplePath.ROOT)
                val entries = b.allEntries()
                b.unlink(dirB.relativeName)
                entries
            }

        val afterDelete = FileChannel.open(path)
            .use { DirectoryBlock(it, 0, SimplePath.ROOT).allEntries() }

        assertTrue(beforeDelete.containsAll(data))
        assertEquals(1, afterDelete.size)
        assertEquals(fileA, afterDelete.single())
    }

    @Test
    fun `maximum number of entries should be persisted across files`() = withTempFile { raf, path ->
        val entries = uniqueEntries().take(DirectoryBlock.MAX_ENTRIES).toSet()

        val initialBlock = DirectoryBlock(raf.channel, 0, SimplePath.ROOT)
        entries.forEach(initialBlock::addOrReplace)
        raf.close()

        val afterReopen = FileChannel.open(path)
            .use { DirectoryBlock(it, 0, SimplePath.ROOT).allEntries() }
            .toSet()

        assertEquals(entries, afterReopen)
    }

    @Test
    fun `writing more than allowed entries should fail`() = withTempFile { raf ->
        val entries = uniqueEntries().take(DirectoryBlock.MAX_ENTRIES + 1).toSet()

        val subject = DirectoryBlock(raf.channel, 0, SimplePath.ROOT)
        val ex = assertFails {
            entries.forEach(subject::addOrReplace)
        }
        assertContains(ex.message.orEmpty(), "exceeded")
    }

    private fun uniqueEntries() =
        generateSequence(buildString { repeat(Segment.SIZE_LIMIT) { append('a') } }) { it }
            .mapIndexed { i, s ->
                val toSet = i % 16
                val toChange = Char(('a'.code + i / 16) + 1)

                s.toCharArray()
                    .apply { set(toSet, toChange) }
                    .let(::String)
            }
            .map(Segment::of)
            .map { DirectoryEntry.FilePointer(it, Long.MAX_VALUE, Long.MAX_VALUE) }
            .distinct()
}
