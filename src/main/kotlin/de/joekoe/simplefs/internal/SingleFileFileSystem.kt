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
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

internal class SingleFileFileSystem(
    container: RandomAccessFile
) : SimpleFileSystem {

    private val channel: FileChannel = container.channel

    private val root = DirectoryBlock(channel, 0)
    private val blocks = mutableMapOf<SimplePath, DirectoryBlock?>()

    override fun createDirectory(path: AbsolutePath): DirectoryNode {
        val parent = requireNotNull(parentBlockOf(path)) {
            "Cannot create directory at non-existent path ${path.parent()}"
        }
        require(parent.get(path.lastSegment) == null) {
            "Cannot create a directory at $path - already exists"
        }

        val pos = channel.size()
        val block = DirectoryBlock(channel, pos)
        parent.addOrReplace(DirectoryPointer(path.lastSegment, pos))
        blocks[path] = block

        return DirectoryNode(path, channel, block, parent)
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

        return FileNode(path, channel, parent)
    }

    override fun open(path: AbsolutePath): SimpleFileSystemNode? {
        val parent = parentBlockOf(path) ?: return null
        return when (parent.get(path.lastSegment)) {
            null -> null
            is FilePointer -> FileNode(
                initialPath = path,
                fileChannel = channel,
                parent = parent
            )

            is DirectoryPointer -> DirectoryNode(
                initialPath = path,
                fileChannel = channel,
                block = checkNotNull(blockAt(path, parent)),
                parent = parent
            )
        }
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
            pointer?.let { DirectoryBlock(channel, it.offset) }
        }

    override fun compact() {
        TODO("Not yet implemented")
    }

    override fun close() = channel.close()
}
