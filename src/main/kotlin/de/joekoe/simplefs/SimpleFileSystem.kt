package de.joekoe.simplefs

import java.io.Closeable

public typealias AbsolutePath = String

public interface SimpleFileSystem : Closeable {
    public fun createDirectory(path: AbsolutePath): DirectoryNode
    public fun createFile(path: AbsolutePath): FileNode
    public fun open(path: AbsolutePath): SimpleFileSystemNode?

    public fun compact()
}
