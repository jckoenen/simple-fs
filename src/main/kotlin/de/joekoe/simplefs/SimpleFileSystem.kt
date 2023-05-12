package de.joekoe.simplefs

import de.joekoe.simplefs.internal.SingleFileFileSystem
import java.io.Closeable
import java.io.RandomAccessFile
import java.nio.file.Path

public typealias AbsolutePath = SimplePath

/**
 * A minimal implementation of a FileSystem.
 *
 * Nodes can either be a [DirectoryNode] or a [FileNode], which offer additional
 * APIs i.e. for reading or traversal.
 * Closing an instance will invalidate all [SimpleFileSystemNode] references.
 *
 * This class and all related classes are not safe for concurrent use, doing so
 * might lead to a corrupted state and data loss.
 */
public interface SimpleFileSystem : Closeable {

    /**
     * Creates a new, empty directory at the given [AbsolutePath].
     *
     * @throws IllegalArgumentException if there is already a [SimpleFileSystemNode] at the given path
     * @throws IllegalArgumentException if the parent path does not exist
     * @return an empty [DirectoryNode]
     */
    public fun createDirectory(path: AbsolutePath): DirectoryNode

    /**
     * Creates a new, empty file at the given [AbsolutePath].
     *
     * @throws IllegalArgumentException if there is already a [SimpleFileSystemNode] at the given path
     * @throws IllegalArgumentException if the parent path does not exist
     * @return an empty [FileNode]
     */
    public fun createFile(path: AbsolutePath): FileNode

    /**
     * Attempts to open an existing node at the given [AbsolutePath].
     *
     * @return null if this [AbsolutePath] does not exist
     * @return [DirectoryNode] if this path denotes an existing directory
     * @return [FileNode] if this path denotes an existing file
     */
    public fun open(path: AbsolutePath): SimpleFileSystemNode?

    /**
     * Optimises the amount of disk space used by the filesystem.
     *
     * This function will remove all previously deleted nodes, and all directories
     * without children.
     * It additionally behaves like [close], invalidating all [SimpleFileSystemNode] created from it.
     *
     * @return A new, open instance, containing the same data at the same paths as this instance
     */
    public fun compact(): SimpleFileSystem

    public companion object {

        /** Creates a new [SimpleFileSystem] backed by single file on disk. */
        public operator fun invoke(path: Path): SimpleFileSystem =
            SingleFileFileSystem(RandomAccessFile(path.toFile(), "rw"), path)
    }
}
