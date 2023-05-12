package de.joekoe.simplefs

import de.joekoe.simplefs.SimplePath.Segment
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectoryNodeTest {
    private val path = SimplePath.of("test")
    private inline fun test(
        f: (subject: DirectoryNode) -> Unit
    ) = withFileSystem { fs -> f(fs.createDirectory(path)) }

    @Test
    fun `operations should fail if this directory was already deleted`() = test { subject ->
        subject.delete()

        assertFailsWith<IllegalArgumentException> { subject.createFile(Segment.of("foo")) }
        assertFailsWith<IllegalArgumentException> { subject.createDirectory(Segment.of("foo")) }
        assertFailsWith<IllegalStateException> { subject.rename(Segment.of("foo")) }
    }

    @Test
    fun `move to a child directory should fail`() = test { subject ->
        val segment = Segment.of("child")

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

    @Test
    fun `changes should be reflected in all references to the same directory`() = withFileSystem { fs ->
        val path = SimplePath.of("test")
        val nodes = generateSequence(fs.createDirectory(path) as SimpleFileSystemNode?) {
            fs.open(path)
        }
            .map { assertIs<DirectoryNode>(it) }
            .take(10)
            .toList()

        nodes.zipWithNext().forEach { (l, r) -> assertEquals(l, r) }

        val childrenCount = setOf(
            nodes[nodes.size / 2].createFile(Segment.of("file")),
            nodes[nodes.size / 3].createDirectory(Segment.of("dir"))
        ).size

        val rename = Segment.of("renamed")
        nodes[nodes.size / 4].rename(rename)

        val newParent = fs.createDirectory(SimplePath.of("new parent"))
        nodes[nodes.size / 5].moveTo(newParent)
        val expectedPath = newParent.absolutePath.child(rename)

        nodes.forEach { node ->
            assertEquals(expectedPath, node.absolutePath)
            assertEquals(childrenCount, node.children().count())
        }
    }

    @Test
    fun `deleting and recreating a directory should not contain old children`() = withFileSystem { fs ->
        val beforeDeletion = fs.createDirectory(path)
        val filePath = Segment.of("file")
        val dirPath = Segment.of("dir")

        beforeDeletion.createFile(filePath)
            .writeChannel().use { it.write(ByteBuffer.wrap("Hello, World".toByteArray())) }
        beforeDeletion.createDirectory(dirPath)

        beforeDeletion.delete()

        val afterDeletion = fs.createDirectory(path)

        assertTrue(afterDeletion.children().toList().isEmpty())
        assertNull(afterDeletion.open(filePath))
        assertNull(afterDeletion.open(dirPath))
    }
}
