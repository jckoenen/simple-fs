package de.joekoe.simplefs

public class DirectoryNode internal constructor() : SimpleFileSystemNode {
    override val name: String
        get() = TODO("Not yet implemented")
    override val path: AbsolutePath
        get() = TODO("Not yet implemented")

    public fun createDirectory(name: String): DirectoryNode {
        TODO()
    }

    public fun createFile(name: String): FileNode {
        TODO()
    }
}
