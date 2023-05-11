package de.joekoe.simplefs

import de.joekoe.simplefs.internal.SingleFileFileSystem
import de.joekoe.simplefs.internal.directory.DirectoryBlock
import de.joekoe.simplefs.internal.directory.DirectoryEntry

public class DirectoryNode internal constructor(
    initialPath: AbsolutePath,
    private var parent: DirectoryBlock,
    private val fileSystem: SingleFileFileSystem
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

        return fileSystem.createDirectory(absolutePath.child(name))
    }

    public fun createFile(name: SimplePath.Segment): FileNode {
        requireNotDeleted()

        return fileSystem.createFile(absolutePath.child(name))
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
