package de.joekoe.simplefs.internal

internal val String.byteCount get() =
    toByteArray(Charsets.UTF_8).size.toLong()
