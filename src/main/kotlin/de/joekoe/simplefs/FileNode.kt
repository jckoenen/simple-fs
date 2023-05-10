package de.joekoe.simplefs

import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

public class FileNode internal constructor(
    path: SimplePath,
    internal var start: Long
) : SimpleFileSystemNode {

    override val name: String get() = absolutePath.fileName.toString()
    override var absolutePath: SimplePath = path
        internal set

    public fun writeChannel(): WritableByteChannel {
        TODO()
    }

    public fun appendChannel(): WritableByteChannel {
        TODO()
    }

    public fun readChannel(): ReadableByteChannel {
        TODO()
    }
}
