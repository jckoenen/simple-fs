package de.joekoe.simplefs

import de.joekoe.simplefs.SimplePath.Segment
import de.joekoe.simplefs.internal.SingleFileFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectoryNodeTest {
    private inline fun test(
        f: (subject: DirectoryNode) -> Unit
    ) = withTempFile { raf ->
        val fs = SingleFileFileSystem(raf)
        val path = SimplePath.of("subject")

        val subject = fs.createDirectory(path)

        f(subject)
    }

    @Test
    fun `operations should fail if this directory was already deleted`() = test { subject ->
        subject.delete()

        assertFailsWith<IllegalArgumentException> { subject.createFile(Segment.of("foo")) }
        assertFailsWith<IllegalArgumentException> { subject.createDirectory(Segment.of("foo")) }
        assertFailsWith<IllegalArgumentException> { subject.rename(Segment.of("foo")) }
    }

    @Test
    fun `move to a child directory should fail`() = test { subject ->
        val segment = Segment.of("test")

        val child = subject.createDirectory(segment)
        assertFailsWith<IllegalArgumentException> { subject.moveTo(child) }
    }

    @Test
    fun `moving directories should work`() = test { subject ->
        val segmentA = Segment.of("a")
        val segmentB = Segment.of("b")

        val childA = subject.createDirectory(segmentA)
        val childB = subject.createDirectory(segmentB)

        childA.moveTo(childB)
        val expectedPath = subject.absolutePath.child(segmentB).child(segmentA)

        assertEquals(expectedPath, childA.absolutePath)
        val existsFailure = assertFailsWith<IllegalArgumentException> { childB.createDirectory(segmentA) }
        assertContains(existsFailure.message.orEmpty(), "exists")
    }
}
