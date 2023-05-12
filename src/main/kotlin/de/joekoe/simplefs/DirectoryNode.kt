package de.joekoe.simplefs

import de.joekoe.simplefs.internal.SingleFileFileSystem
import de.joekoe.simplefs.internal.directory.DirectoryBlock

public class DirectoryNode internal constructor(
    private val block: DirectoryBlock,
    private val fileSystem: SingleFileFileSystem
) : SimpleFileSystemNode {

    override val name: String
        get() = if (absolutePath == SimplePath.ROOT) "/" else absolutePath.lastSegment.toString()

    override val absolutePath: AbsolutePath get() = block.absolutePath

    public fun createDirectory(name: SimplePath.Segment): DirectoryNode =
        fileSystem.createDirectory(absolutePath.child(name))

    public fun createFile(name: SimplePath.Segment): FileNode =
        fileSystem.createFile(absolutePath.child(name))

    public fun children(): Sequence<SimpleFileSystemNode> =
        block
            .allEntries()
            .asSequence()
            .mapNotNull { e ->
                val path = absolutePath.child(e.relativeName)
                fileSystem.open(path)
            }

    public fun open(pathSegment: SimplePath.Segment): SimpleFileSystemNode? =
        fileSystem.open(absolutePath.child(pathSegment))

    override fun moveTo(directory: DirectoryNode) {
        require(absolutePath != SimplePath.ROOT) { "Cannot move root directory" }
        block.absolutePath = fileSystem.moveTo(this, directory.absolutePath).first
    }

    override fun rename(name: SimplePath.Segment) {
        block.absolutePath = fileSystem.rename(this, name)
    }

    override fun delete() {
        fileSystem.delete(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DirectoryNode

        if (fileSystem != other.fileSystem) return false
        return absolutePath == other.absolutePath
    }

    override fun hashCode(): Int {
        var result = fileSystem.hashCode()
        result = 31 * result + absolutePath.hashCode()
        return result
    }

    override fun toString(): String = "DirectoryNode(absolutePath=$absolutePath)"
}
