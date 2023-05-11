package de.joekoe.simplefs.internal

import de.joekoe.simplefs.AbsolutePath
import de.joekoe.simplefs.DirectoryNode
import de.joekoe.simplefs.FileNode
import de.joekoe.simplefs.SimpleFileSystem
import de.joekoe.simplefs.SimpleFileSystemNode
import de.joekoe.simplefs.SimplePath
import de.joekoe.simplefs.internal.directory.DirectoryBlock
import de.joekoe.simplefs.internal.directory.DirectoryEntry.DirectoryPointer
import de.joekoe.simplefs.internal.directory.DirectoryEntry.FilePointer
import de.joekoe.simplefs.internal.directory.withNewName
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

internal class SingleFileFileSystem(
    container: RandomAccessFile
) : SimpleFileSystem {

    private val channel: FileChannel = container.channel

    private val root = DirectoryBlock(channel, 0, SimplePath.ROOT)
    private val blocks = mutableMapOf<SimplePath, DirectoryBlock?>().also {
        it[SimplePath.ROOT] = root
    }

    override fun createDirectory(path: AbsolutePath): DirectoryNode {
        val parent = requireNotNull(parentBlockOf(path)) {
            "Cannot create directory at non-existent path ${path.parent()}"
        }
        require(parent.get(path.lastSegment) == null) {
            "Cannot create a directory at $path - already exists"
        }

        val pos = channel.size()
        val block = DirectoryBlock(channel, pos, path)
        parent.addOrReplace(DirectoryPointer(path.lastSegment, pos))
        blocks[path] = block

        return DirectoryNode(block, this)
    }

    override fun createFile(path: AbsolutePath): FileNode {
        val parent = requireNotNull(parentBlockOf(path)) {
            "Cannot create file at non-existent path ${path.parent()}"
        }
        require(parent.get(path.lastSegment) == null) {
            "Cannot create a file at $path - already exists"
        }

        val pos = channel.size()
        parent.addOrReplace(FilePointer(path.lastSegment, pos, 0))

        return FileNode(path.lastSegment, channel, parent, this)
    }

    override fun open(path: AbsolutePath): SimpleFileSystemNode? {
        val parent = parentBlockOf(path) ?: return null
        return when (parent.get(path.lastSegment)) {
            null -> null
            is FilePointer -> FileNode(
                segment = path.lastSegment,
                fileChannel = channel,
                parent = parent,
                fileSystem = this
            )

            is DirectoryPointer -> DirectoryNode(
                block = requireNotNull(blockAt(path, parent)),
                fileSystem = this
            )
        }
    }

    internal fun moveTo(node: SimpleFileSystemNode, path: AbsolutePath): Pair<AbsolutePath, DirectoryBlock> {
        val notRecursive = path.allSubPaths()
            .none { it == node.absolutePath }
        require(notRecursive) { "Can't move this node to a child directory" }

        val oldParent = checkNotNull(parentBlockOf(node.absolutePath)) {
            "Source directory doesn't exist"
        }
        val newParent = checkNotNull(parentBlockOf(path)?.let { blockAt(path, it) }) {
            "Target directory doesn't exist"
        }
        val oldPointer = checkNotNull(oldParent.get(node.absolutePath.lastSegment)) {
            "Node already deleted"
        }

        newParent.addOrReplace(oldPointer)
        oldParent.delete(node.absolutePath.lastSegment)
        val newPath = path.child(node.absolutePath.lastSegment)
        blocks.remove(node.absolutePath)?.let {
            blocks[path.child(node.absolutePath.lastSegment)] = it
        }

        return newPath to newParent
    }

    internal fun rename(node: SimpleFileSystemNode, name: SimplePath.Segment): AbsolutePath {
        val parent = requireNotNull(parentBlockOf(node.absolutePath)) {
            "Parent directory doesn't exist"
        }
        val oldName = node.absolutePath.lastSegment
        val newPath = requireNotNull(node.absolutePath.parent()?.child(name))
        val oldPointer = checkNotNull(parent.get(oldName)) { "Node no longer linked to parent" }

        parent.addOrReplace(oldPointer.withNewName(name))
        blocks.remove(node.absolutePath)?.let { blocks[newPath] = it }

        return newPath
    }

    internal fun delete(node: SimpleFileSystemNode) {
        val parent = requireNotNull(parentBlockOf(node.absolutePath)) {
            "Parent directory doesn't exist"
        }
        parent.delete(node.absolutePath.lastSegment)
        blocks.remove(node.absolutePath)
    }

    private fun parentBlockOf(path: AbsolutePath): DirectoryBlock? =
        path.parent()
            ?.allSubPaths()
            ?.fold(root) { block, subPath ->
                blockAt(subPath, parent = block) ?: return@parentBlockOf null
            }
            ?: root

    private fun blockAt(path: AbsolutePath, parent: DirectoryBlock): DirectoryBlock? =
        blocks.computeIfAbsent(path) {
            val pointer = parent.get(path.lastSegment) as? DirectoryPointer
            pointer?.let { DirectoryBlock(channel, it.offset, path) }
        }

    override fun compact() {
        TODO("Not yet implemented")
    }

    override fun close() = channel.close()
}
