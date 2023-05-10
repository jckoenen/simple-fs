package de.joekoe.simplefs.internal.directory

internal sealed interface DirectoryEntry {
    val relativeName: String
    val offset: Long

    data class FilePointer(
        override val relativeName: String,
        override val offset: Long,
        val size: Long
    ) : DirectoryEntry

    data class DirectoryPointer(
        override val relativeName: String,
        override val offset: Long
    ) : DirectoryEntry
}
