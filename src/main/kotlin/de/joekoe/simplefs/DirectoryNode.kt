package de.joekoe.simplefs

import de.joekoe.simplefs.internal.directory.DirectoryBlock
import de.joekoe.simplefs.internal.directory.DirectoryEntry
import java.nio.channels.FileChannel

public class DirectoryNode internal constructor(
    initialPath: AbsolutePath,
    private val fileChannel: FileChannel,
    private val block: DirectoryBlock,
    private var parent: DirectoryBlock
) : SimpleFileSystemNode {

    override val name: String get() = absolutePath.lastSegment.toString()
    override var absolutePath: AbsolutePath = initialPath
        internal set

    private fun requireNotDeleted() =
        requireNotNull(parent.get(absolutePath.lastSegment) as? DirectoryEntry.DirectoryPointer) {
            "Directory has already been deleted"
        }

    public fun createDirectory(name: SimplePath.Segment): DirectoryNode {
        requireNotDeleted()

        val pos = fileChannel.size()
        // TODO retrieve block from FS
        val childBlock = DirectoryBlock(fileChannel, pos)
        block.addOrReplace(DirectoryEntry.DirectoryPointer(name, pos))
        return DirectoryNode(absolutePath.child(name), fileChannel, childBlock, block)
    }

    public fun createFile(name: SimplePath.Segment): FileNode {
        requireNotDeleted()

        val pos = fileChannel.size()
        block.addOrReplace(DirectoryEntry.FilePointer(name, pos, 0))
        return FileNode(absolutePath.child(name), fileChannel, block)
    }

    override fun moveTo(directory: DirectoryNode) {
        val pointer = requireNotDeleted()
        val isNotChildOfThis = directory.absolutePath.allSubPaths()
            .take(absolutePath.segmentCount)
            .none { it == absolutePath }
        require(isNotChildOfThis) { "Can't move this directory to a child directory" }

        parent = directory.link(pointer)
    }

    override fun rename(name: SimplePath.Segment) {
        val pointer = requireNotDeleted()
        val parentPath = requireNotNull(absolutePath.parent()) {
            "Cannot rename root directory"
        }

        parent.addOrReplace(pointer.copy(relativeName = name))
        absolutePath = parentPath.child(name)
    }

    override fun delete() {
        requireNotDeleted()

        parent.delete(absolutePath.lastSegment)
    }

    internal fun link(entry: DirectoryEntry): DirectoryBlock = parent.apply { addOrReplace(entry) }
}
