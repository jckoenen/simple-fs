package de.joekoe.simplefs

import de.joekoe.simplefs.SimplePath.Segment
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectoryNodeTest {
    private inline fun test(
        f: (subject: DirectoryNode) -> Unit
    ) = withFileSystem { fs -> f(fs.createDirectory(SimplePath.of("foo"))) }

    @Test
    fun `operations should fail if this directory was already deleted`() = test { subject ->
        subject.delete()

        assertFailsWith<IllegalArgumentException> { subject.createFile(Segment.of("foo")) }
        assertFailsWith<IllegalArgumentException> { subject.createDirectory(Segment.of("foo")) }
        assertFailsWith<IllegalStateException> { subject.rename(Segment.of("foo")) }
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

    @Test
    fun `renaming should not affect content`() = test { subject ->
        val child = subject.createFile(Segment.of("child"))

        child.writeChannel().use { it.write(ByteBuffer.wrap("Hello, World".toByteArray())) }
        val contentBefore = child.readChannel().consumeText()
        val childrenBefore = subject.children().toList().map { it.name }

        subject.rename(Segment.of("another name"))

        val childrenAfter = subject.children().toList().map { it.name }
        val contentAfter = child.readChannel().consumeText()

        assertEquals(childrenBefore, childrenAfter)
        assertEquals(contentBefore, contentAfter)
    }
}
