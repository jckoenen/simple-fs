package de.joekoe.simplefs.internal.directory

import de.joekoe.simplefs.SimplePath
import de.joekoe.simplefs.internal.SimpleFsReadableChannel
import de.joekoe.simplefs.internal.SimpleFsWritableChannel
import de.joekoe.simplefs.internal.byteCount
import de.joekoe.simplefs.internal.directory.DirectoryEntry.DirectoryPointer
import de.joekoe.simplefs.internal.directory.DirectoryEntry.FilePointer
import java.nio.channels.Channels
import java.nio.channels.FileChannel

internal class DirectoryBlock(
    private val fileChannel: FileChannel,
    private val start: Long
) {
    companion object {
        const val MAX_ENTRIES = 255

        private val CHARSET = Charsets.UTF_8
        private const val TAB = '\t'
        private const val NEW_LINE = '\n'
        private const val DIR_TAG = 'd'
        private const val FILE_TAG = 'f'

        private val ENTRY_SIZE = run {
            val metadataSize = buildString {
                repeat(4) { append(TAB) } // separators between data
                repeat(2) { append(Long.MAX_VALUE) } // size and offset
                append(DIR_TAG)
                append(NEW_LINE)
            }.byteCount.toInt()
            SimplePath.Segment.SIZE_LIMIT + metadataSize
        }
        private val BLOCK_SIZE = MAX_ENTRIES * ENTRY_SIZE.toLong()
    }

    private val entries: MutableMap<SimplePath.Segment, DirectoryEntry> by lazy { loadEntries() }

    fun allEntries() = entries.values.toList()

    fun addOrReplace(entry: DirectoryEntry) {
        if (entries.put(entry.relativeName, entry) != entry) {
            saveEntries()
        }
    }

    fun delete(relativeName: SimplePath.Segment) {
        if (entries.remove(relativeName) != null) {
            saveEntries()
        }
    }

    fun get(relativeName: SimplePath.Segment) = entries[relativeName]

    private fun loadEntries() =
        Channels.newReader(SimpleFsReadableChannel(fileChannel, start, BLOCK_SIZE), CHARSET)
            .useLines {
                it.takeWhile(String::isNotBlank)
                    .map { line ->
                        val (tag, offset, size, name) = line.split(TAB)
                        when (tag.singleOrNull()) {
                            DIR_TAG -> DirectoryPointer(
                                relativeName = SimplePath.Segment.of(name),
                                offset = offset.toLong()
                            )

                            FILE_TAG -> FilePointer(
                                relativeName = SimplePath.Segment.of(name),
                                offset = offset.toLong(),
                                size = size.toLong()
                            )

                            else -> error("Unexpected Tag $tag")
                        }
                    }
                    .associateByTo(mutableMapOf(), DirectoryEntry::relativeName)
            }

    private fun saveEntries() {
        SimpleFsWritableChannel(fileChannel, start) {}
            .let { Channels.newWriter(it, CHARSET) }
            .use { writer ->
                val e = allEntries()

                e.joinTo(writer, separator = NEW_LINE.toString()) { it.persistentFormat() }
                // indicate premature end of entries
                if (e.size < MAX_ENTRIES) writer.appendLine().appendLine()
            }
    }

    private fun DirectoryEntry.persistentFormat(): String {
        val tag = when (this) {
            is DirectoryPointer -> DIR_TAG
            is FilePointer -> FILE_TAG
        }
        return "$tag$TAB$offset$TAB${(this as? FilePointer)?.size ?: -1}$TAB$relativeName"
    }
}
