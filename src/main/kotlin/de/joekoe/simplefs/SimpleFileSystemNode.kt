package de.joekoe.simplefs

public sealed interface SimpleFileSystemNode {
    public val name: String
    public val absolutePath: AbsolutePath

    public fun moveTo(directory: DirectoryNode)

    public fun rename(name: SimplePath.Segment)

    public fun delete()
}
