package de.joekoe.simplefs

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class SimplePathTest {
    @Test
    fun `creating invalid Segments should fail`() {
        assertFails { SimplePath.Segment.of("") }
        assertFails { SimplePath.Segment.of(" ") }
        assertFails { SimplePath.Segment.of("foo/bar") }
        assertFails { SimplePath.Segment.of("\t") }
        assertFails { SimplePath.Segment.of("foo\tbal") }
        assertFails {
            SimplePath.Segment.of(
                buildString { repeat(100) { append('a') } }
            )
        }
    }

    @Test
    fun `creating valid Segments should succeed`() {
        // "assertion" is no exception
        SimplePath.Segment.of("a.txt")
        SimplePath.Segment.of("b")
        SimplePath.Segment.of("b a")
        SimplePath.Segment.of("🤷‍♂️")
    }

    @Test
    fun `creating empty Path should fail`() {
        assertFails { SimplePath.of("") }
        assertFails { SimplePath.of("/") }
    }

    @Test
    fun `creating valid Path should succeed`() {
        assertEquals(segments("no", "root"), SimplePath.of("no/root").segments)
        assertEquals(segments("leading", "root"), SimplePath.of("/leading/root").segments)
    }

    @Test
    fun `allSubPaths should return sub paths in order`() {
        val expected = listOf(
            segments("1"),
            segments("1", "2"),
            segments("1", "2", "3"),
            segments("1", "2", "3", "4")
        )
        val actual = SimplePath.of("1/2/3/4")
            .allSubPaths()
            .map(SimplePath::segments)
            .toList()

        assertEquals(expected, actual)
    }

    @Test
    fun `parent should return null if parent is root`() {
        assertEquals(null, SimplePath.of("/1").parent())
    }

    @Test
    fun `parent should return direct parent if not in root`() {
        assertEquals(SimplePath.of("/1"), SimplePath.of("/1/2").parent())
    }

    @Test
    fun `child should append child at the end of the path`() {
        val expected = SimplePath.of("/parent/child")
        val actual = SimplePath.of("/parent").child(SimplePath.Segment.of("child"))

        assertEquals(expected, actual)
    }

    private fun segments(vararg segments: String) =
        segments.map(SimplePath.Segment::of)
}
