package de.joekoe.simplefs

import de.joekoe.simplefs.internal.SimpleFsReadableChannel
import de.joekoe.simplefs.internal.SimpleFsWritableChannel
import de.joekoe.simplefs.internal.SingleFileFileSystem
import de.joekoe.simplefs.internal.directory.DirectoryBlock
import de.joekoe.simplefs.internal.directory.DirectoryEntry
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

public class FileNode internal constructor(
    initialPath: AbsolutePath,
    private val fileChannel: FileChannel,
    private var parent: DirectoryBlock,
    private val fileSystem: SingleFileFileSystem
) : SimpleFileSystemNode {

    override val name: String get() = absolutePath.lastSegment.toString()
    override var absolutePath: AbsolutePath = initialPath
        internal set

    private fun requireNotDeleted() =
        requireNotNull(parent.get(absolutePath.lastSegment) as? DirectoryEntry.FilePointer) {
            "File has already been deleted"
        }

    public fun writeChannel(): WritableByteChannel {
        val pointer = requireNotDeleted()
        val pos = fileChannel.size()
        return SimpleFsWritableChannel(fileChannel, fileChannel.size()) { bytesWritten ->
            parent.addOrReplace(pointer.copy(offset = pos, size = bytesWritten))
        }
    }

    public fun appendChannel(): WritableByteChannel {
        val wc = writeChannel()
        readChannel()
            .use { rc ->
                val buf = ByteBuffer.allocate(8 * 1024)
                while (rc.read(buf) != -1) {
                    buf.rewind()
                    wc.write(buf)
                }
            }
        return wc
    }

    public fun readChannel(): ReadableByteChannel {
        val pointer = requireNotDeleted()

        return SimpleFsReadableChannel(fileChannel, start = pointer.offset, size = pointer.size)
    }

    override fun moveTo(directory: DirectoryNode) {
        parent = fileSystem.moveTo(this, directory.absolutePath)
        absolutePath = directory.absolutePath.child(absolutePath.lastSegment)
    }

    override fun rename(name: SimplePath.Segment) {
        absolutePath = fileSystem.rename(this, name)
    }

    override fun delete() {
        fileSystem.delete(this)
    }
}
