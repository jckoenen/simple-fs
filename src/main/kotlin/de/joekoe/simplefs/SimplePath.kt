package de.joekoe.simplefs

import de.joekoe.simplefs.internal.byteCount

@JvmInline
public value class SimplePath private constructor(
    public val segments: List<Segment>
) {
    @JvmInline
    public value class Segment private constructor(private val underlying: String) {
        override fun toString(): String = underlying

        public companion object {
            internal const val SIZE_LIMIT = 64
            public fun of(s: String): Segment {
                check(s.isNotBlank()) { "Path segment must not be blank" }
                check('\t' !in s) { "Illegal tab character in segment \"$s\"" }
                check(s.byteCount <= SIZE_LIMIT) { "Path segment \"$s\" is too long" }
                return Segment(s)
            }
        }
    }

    public val fileName: Segment get() = segments.last()

    internal fun parent(): SimplePath? =
        if (segments.size == 1) null else SimplePath(segments.dropLast(1))

    internal fun allSubPaths() =
        (1..segments.size)
            .asSequence()
            .map(segments::take)
            .map(::SimplePath)

    override fun toString(): String =
        segments.joinToString(prefix = "SimplePath(", postfix = ")", separator = DELIMITER.toString())

    public companion object {
        public const val DELIMITER: Char = '/'

        public fun of(path: String): SimplePath =
            path.split(DELIMITER)
                .dropWhile(String::isBlank) // ignore leading delimiters
                .also { require(it.isNotEmpty()) { "Path cannot be empty" } }
                .map(Segment::of)
                .let(::SimplePath)
    }
}
