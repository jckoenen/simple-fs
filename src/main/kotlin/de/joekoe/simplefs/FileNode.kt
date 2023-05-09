package de.joekoe.simplefs

import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

public class FileNode internal constructor() : SimpleFileSystemNode {

    override val name: String
        get() = TODO("Not yet implemented")
    override val path: AbsolutePath
        get() = TODO("Not yet implemented")

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
