package de.joekoe.simplefs

import de.joekoe.simplefs.internal.byteCount

public class SimplePath private constructor(
    public val segments: List<String>
) {
    public val fileName: String? get() = segments.lastOrNull()

    override fun toString(): String =
        segments.joinToString(prefix = "SimplePath(", postfix = ")", separator = DELIMITER.toString())

    public companion object {
        public const val DELIMITER: Char = '/'
        internal const val MAX_SEGMENT_SIZE = 64

        public fun of(path: String): SimplePath =
            path.splitToSequence(DELIMITER)
                .onEach { segment ->
                    check(segment.isNotBlank()) { "Path segment must not be blank in path \"$path\"" }
                    check('\t' !in segment) { "Illegal tab character in path \"$path\"" }
                    check(segment.byteCount <= MAX_SEGMENT_SIZE) { "Path segment \"$segment\" is too long" }
                }
                .toList()
                .let(::SimplePath)
    }
}
