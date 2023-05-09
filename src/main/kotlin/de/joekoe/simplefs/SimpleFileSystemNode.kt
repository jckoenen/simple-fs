package de.joekoe.simplefs

public sealed interface SimpleFileSystemNode {
    public val name: String
    public val path: AbsolutePath

    public fun moveTo(directory: DirectoryNode) {
        TODO()
    }

    public fun rename(name: String) {
        TODO()
    }

    public fun delete() {
        TODO()
    }
}
