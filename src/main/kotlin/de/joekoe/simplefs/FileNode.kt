package de.joekoe.simplefs

import de.joekoe.simplefs.internal.SimpleFsReadableChannel
import de.joekoe.simplefs.internal.SimpleFsWritableChannel
import de.joekoe.simplefs.internal.SingleFileFileSystem
import de.joekoe.simplefs.internal.copyTo
import de.joekoe.simplefs.internal.directory.DirectoryBlock
import de.joekoe.simplefs.internal.directory.DirectoryEntry
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

public class FileNode internal constructor(
    private var segment: SimplePath.Segment,
    private val fileChannel: FileChannel,
    private var parent: DirectoryBlock,
    private val fileSystem: SingleFileFileSystem
) : SimpleFileSystemNode {

    override val name: String get() = segment.toString()
    override val absolutePath: AbsolutePath get() = parent.absolutePath.child(segment)
    private var closed = false

    private fun requireNotDeleted(): DirectoryEntry.FilePointer {
        check(!closed) { "File has already been deleted" }

        return checkNotNull(parent.get(absolutePath.lastSegment) as? DirectoryEntry.FilePointer) {
            "File has already been deleted"
        }
    }

    /**
     * Opens a new [WritableByteChannel] to overwrite data contained in this node.
     *
     * The channel MUST be closed after use.
     */
    public fun writeChannel(): WritableByteChannel {
        val pointer = requireNotDeleted()
        val pos = fileChannel.size()
        return SimpleFsWritableChannel(fileChannel, fileChannel.size()) { bytesWritten ->
            parent.addOrReplace(pointer.copy(offset = pos, size = bytesWritten))
        }
    }

    /**
     * Opens a new [WritableByteChannel] to append data to this node.
     *
     * The channel MUST be closed after use.
     */
    public fun appendChannel(): WritableByteChannel {
        val wc = writeChannel()
        readChannel().use { it.copyTo(wc) }
        return wc
    }

    /**
     * Opens a new [ReadableByteChannel] to retrieve data contained in this node.
     *
     * The channel MUST be closed after use.
     */
    public fun readChannel(): ReadableByteChannel {
        val pointer = requireNotDeleted()

        return SimpleFsReadableChannel(fileChannel, start = pointer.offset, size = pointer.size)
    }

    override fun moveTo(directory: DirectoryNode) {
        parent = fileSystem.moveTo(this, directory.absolutePath).second
    }

    override fun rename(name: SimplePath.Segment) {
        requireNotDeleted()

        fileSystem.rename(this, name)
        segment = name
    }

    override fun delete() {
        fileSystem.delete(this)
    }

    internal fun close() {
        closed = true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileNode

        if (fileSystem != other.fileSystem) return false
        return absolutePath == other.absolutePath
    }

    override fun hashCode(): Int {
        var result = fileSystem.hashCode()
        result = 31 * result + absolutePath.hashCode()
        return result
    }

    override fun toString(): String = buildString {
        append("FileNode(")
        append("absolutePath="); append(absolutePath)
        append(", deleted="); append(closed)
        if (!closed) {
            val pointer = requireNotDeleted()
            append(", offset=", pointer.offset)
            append(", size=", pointer.size)
        }
        append(')')
    }
}
