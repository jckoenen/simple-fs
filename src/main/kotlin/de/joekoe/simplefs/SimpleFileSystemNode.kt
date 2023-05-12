package de.joekoe.simplefs

/**
 * Denotes a single entry in a [SimpleFileSystem].
 *
 * @see FileNode
 * @see DirectoryNode
 */
public sealed interface SimpleFileSystemNode {
    /**
     * The file name of this node. Includes the extension if there is one.
     *
     * @sample "foo.txt"
     * @sample "my folder"
     */
    public val name: String

    /**
     * The absolute path of this node in its [SimpleFileSystem].
     *
     * @sample "/my folder/readme.md"
     */
    public val absolutePath: AbsolutePath

    /**
     * Moves this node (and possibly all its children) to another parent directory
     *
     * @throws IllegalArgumentException when trying to move the root directory
     * @throws IllegalArgumentException when trying to move to a child directory
     * @throws IllegalStateException when internal invariants are violated
     */
    public fun moveTo(directory: DirectoryNode)

    /**
     * Renames this node.
     *
     * @throws IllegalStateException when internal invariants are violated
     */
    public fun rename(name: SimplePath.Segment)

    /**
     * Deletes this node, making it unreachable from other nodes.
     * Invalidates all references to this [absolutePath].
     *
     * @throws IllegalStateException when internal invariants are violated
     */
    public fun delete()
}
