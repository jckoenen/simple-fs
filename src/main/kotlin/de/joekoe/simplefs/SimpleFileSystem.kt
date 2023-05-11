package de.joekoe.simplefs

import de.joekoe.simplefs.internal.SingleFileFileSystem
import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.file.Path

public typealias AbsolutePath = SimplePath

public interface SimpleFileSystem : Closeable {
    public fun createDirectory(path: AbsolutePath): DirectoryNode
    public fun createFile(path: AbsolutePath): FileNode
    public fun open(path: AbsolutePath): SimpleFileSystemNode?

    public fun compact()

    public companion object {
        public operator fun invoke(path: Path): SimpleFileSystem =
            SingleFileFileSystem(RandomAccessFile(path.toFile(), "rw"))
    }
}
