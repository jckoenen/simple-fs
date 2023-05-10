package de.joekoe.simplefs

import de.joekoe.simplefs.internal.directory.DirectoryBlock

public class DirectoryNode internal constructor(
    path: SimplePath,
    internal val block: DirectoryBlock
) : SimpleFileSystemNode {

    override val name: String get() = absolutePath.fileName.toString()
    override var absolutePath: SimplePath = path
        internal set

    public fun createDirectory(name: String): DirectoryNode {
        TODO()
    }

    public fun createFile(name: String): FileNode {
        TODO()
    }
}
