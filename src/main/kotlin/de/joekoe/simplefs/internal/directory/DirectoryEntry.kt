package de.joekoe.simplefs.internal.directory

import de.joekoe.simplefs.SimplePath

internal sealed interface DirectoryEntry {
    val relativeName: SimplePath.Segment
    val offset: Long

    data class FilePointer(
        override val relativeName: SimplePath.Segment,
        override val offset: Long,
        val size: Long
    ) : DirectoryEntry

    data class DirectoryPointer(
        override val relativeName: SimplePath.Segment,
        override val offset: Long
    ) : DirectoryEntry
}

internal fun DirectoryEntry.withNewName(name: SimplePath.Segment) = when (this) {
    is DirectoryEntry.DirectoryPointer -> copy(relativeName = name)
    is DirectoryEntry.FilePointer -> copy(relativeName = name)
}
