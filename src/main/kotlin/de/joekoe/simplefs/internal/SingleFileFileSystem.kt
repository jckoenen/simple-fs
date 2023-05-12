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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class SingleFileFileSystem(
    container: RandomAccessFile,
    private val path: Path
) : SimpleFileSystem {

    private val channel: FileChannel = container.channel

    private val rootBlock = DirectoryBlock(channel, 0, SimplePath.ROOT)
    private val rootNode = DirectoryNode(rootBlock, this)

    private val blocks = mutableMapOf<SimplePath, DirectoryBlock?>().also {
        it[SimplePath.ROOT] = rootBlock
    }
    private val files = WeakValueMap<AbsolutePath, FileNode>()
    private val directories = WeakValueMap<AbsolutePath, DirectoryNode>().also {
        it[SimplePath.ROOT] = rootNode
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
        val node = DirectoryNode(block, this)

        parent.addOrReplace(DirectoryPointer(path.lastSegment, pos))
        blocks[path] = block
        directories[path] = node

        return node
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

        val node = FileNode(path.lastSegment, channel, parent, this)
        files[path] = node

        return node
    }

    override fun open(path: AbsolutePath): SimpleFileSystemNode? {
        val existing = files[path] ?: directories[path]
        if (existing != null) return existing

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

        blocks.reKey(node.absolutePath, newPath)
        nodeCache(node).reKey(node.absolutePath, newPath)

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
        blocks.reKey(node.absolutePath, newPath)
        nodeCache(node).reKey(node.absolutePath, newPath)

        return newPath
    }

    internal fun delete(node: SimpleFileSystemNode) {
        if (node is DirectoryNode) {
            node.children().forEach(SimpleFileSystemNode::delete)
        }
        requireNotNull(parentBlockOf(node.absolutePath)) { "Parent directory doesn't exist" }
            .delete(node.absolutePath.lastSegment)
        nodeCache(node).remove(node.absolutePath)
        blocks.remove(node.absolutePath)
    }

    private fun parentBlockOf(path: AbsolutePath): DirectoryBlock? =
        path.parent()
            ?.allSubPaths()
            ?.fold(rootBlock) { block, subPath ->
                blockAt(subPath, parent = block) ?: return@parentBlockOf null
            }
            ?: rootBlock

    private fun blockAt(path: AbsolutePath, parent: DirectoryBlock): DirectoryBlock? =
        blocks.computeIfAbsent(path) {
            val pointer = parent.get(path.lastSegment) as? DirectoryPointer
            pointer?.let { DirectoryBlock(channel, it.offset, path) }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : SimpleFileSystemNode> nodeCache(node: T): WeakValueMap<AbsolutePath, T> = when (node) {
        is DirectoryNode -> directories as WeakValueMap<AbsolutePath, T>
        is FileNode -> files as WeakValueMap<AbsolutePath, T>
        else -> error("No node cache registered for $node")
    }

    override fun compact(): SimpleFileSystem {
        val tmpPath = path.resolveSibling("tmp+${hashCode()}.fs")
        (SimpleFileSystem(tmpPath) as SingleFileFileSystem)
            .use { this.compactTo(it) }
        close()
        Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING)

        return SimpleFileSystem(path)
    }

    internal fun compactTo(other: SingleFileFileSystem) {
        breadthFirstTraversal()
            .drop(1) // no need to copy the root
            .forEach { node ->
                when (node) {
                    is DirectoryNode -> {
                        if (node.children().any()) {
                            other.createDirectory(node.absolutePath)
                        }
                    }

                    is FileNode -> {
                        other.createFile(node.absolutePath).writeChannel()
                            .use { wc ->
                                node.readChannel().use { it.copyTo(wc) }
                            }
                    }
                }
            }
    }

    internal fun breadthFirstTraversal(): Sequence<SimpleFileSystemNode> = sequence {
        val q = ArrayDeque<SimpleFileSystemNode>()
        q.add(rootNode)
        while (q.isNotEmpty()) {
            val node = q.removeFirst()
            if (node is DirectoryNode) {
                q.addAll(node.children().toList())
            }
            yield(node)
        }
    }

    override fun close() = channel.close()
}
