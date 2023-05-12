package de.joekoe.simplefs.internal

import de.joekoe.simplefs.DirectoryNode
import de.joekoe.simplefs.FileNode
import de.joekoe.simplefs.SimpleFileSystem
import de.joekoe.simplefs.SimplePath
import de.joekoe.simplefs.consumeBytes
import de.joekoe.simplefs.copyTo
import de.joekoe.simplefs.withFileSystem
import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingleFileFileSystemTest {

    private val ignoredFolders = listOf(
        "build", // gradle touches multiple files in this directory while testing, breaks comparison
        ".gradle" // fails on windows due to file lock
    )

    private val systemFileSeparator = FileSystems.getDefault().separator

    private fun allFilesInProject() =
        Files.walk(Path(""))
            .asSequence()
            .drop(1) // ignore the root
            .filterNot { ignoredFolders.any(it::startsWith) }

    @Test
    fun `copying project folder should preserve content`() = withFileSystem { subject ->
        copyProjectToFileSystem(subject)

        compareProjectWithFs(subject)
    }

    @Test
    fun `copying project folder should preserve content across closing`() = withFileSystem { beforeClose, fsPath ->
        copyProjectToFileSystem(beforeClose)
        beforeClose.close()

        SimpleFileSystem(fsPath).use(::compareProjectWithFs)
    }

    @Test
    fun `compacting should preserve data integrity`() = withFileSystem { source ->
        copyProjectToFileSystem(source)

        withFileSystem { target ->
            source.compactTo(target)

            val sourceEntries = source.breadthFirstTraversal()
                .filter { it !is DirectoryNode || it.children().any() }
                .map { it.absolutePath.toString() }
                .toSortedSet()
            val targetEntries = target.breadthFirstTraversal()
                .filter { it !is DirectoryNode || it.children().any() }
                .map { it.absolutePath.toString() }
                .toSortedSet()

            assertEquals(emptySet(), sourceEntries - targetEntries)

            source.breadthFirstTraversal()
                .drop(1)
                .forEach { sourceNode ->
                    val targetNode = target.open(sourceNode.absolutePath)

                    when (sourceNode) {
                        is DirectoryNode -> {
                            if (sourceNode.children().any()) {
                                assertIs<DirectoryNode>(targetNode)
                                assertEquals(sourceNode.name, targetNode.name)
                            } else {
                                assertNull(targetNode)
                            }
                        }

                        is FileNode -> {
                            assertIs<FileNode>(targetNode)
                            assertEquals(sourceNode.name, targetNode.name)
                            assertContentEquals(
                                sourceNode.readChannel().consumeBytes(),
                                targetNode.readChannel().consumeBytes()
                            )
                        }
                    }
                }
        }
    }

    @Test
    fun `compacting in place should not fail`() = withFileSystem { fs ->
        copyProjectToFileSystem(fs)

        val new = fs.compact()
        assertNotSame(fs, new)

        compareProjectWithFs(new)
    }

    @Test
    fun `Verify store - delete - compact - store - read cycle`() = withFileSystem { original, originalPath ->
        copyProjectToFileSystem(original)

        val deletionChance = 70
        val deletedPaths = original.breadthFirstTraversal()
            .filterIsInstance<FileNode>()
            .filter { (it.hashCode() % 100) < deletionChance }
            .map {
                val path = it.absolutePath
                it.delete()
                path
            }
            .toHashSet()

        withFileSystem { compacted, compactedPath ->
            original.compactTo(compacted)
            assertTrue(originalPath.fileSize() > compactedPath.fileSize())

            val prefix = SimplePath.of("second-write")
            compacted.createDirectory(prefix)

            copyProjectToFileSystem(compacted, prefix)

            compacted.close()
            original.close()

            val originalReopened = SimpleFileSystem(originalPath)
            val compactedReopened = SimpleFileSystem(compactedPath)

            allFilesInProject()
                .map { it.asSimplePath() }
                .forEach { pathFromFirstWrite ->
                    val pathFromSecondWrite = prefix + pathFromFirstWrite

                    when {
                        deletedPaths.contains(pathFromFirstWrite) -> {
                            assertNull(originalReopened.open(pathFromFirstWrite))
                            assertNull(compactedReopened.open(pathFromFirstWrite))
                            assertNotNull(compactedReopened.open(pathFromSecondWrite))
                        }

                        originalReopened.open(pathFromFirstWrite) is DirectoryNode -> {
                            assertIs<DirectoryNode>(compactedReopened.open(pathFromSecondWrite))

                            val hasChildren = assertIs<DirectoryNode>(original.open(pathFromFirstWrite))
                                .children()
                                .any()
                            if (hasChildren) {
                                assertIs<DirectoryNode>(compactedReopened.open(pathFromFirstWrite))
                            } else {
                                assertNull(compactedReopened.open(pathFromFirstWrite))
                            }
                        }

                        originalReopened.open(pathFromFirstWrite) is FileNode -> {
                            val inFirstWrite = assertIs<FileNode>(compactedReopened.open(pathFromFirstWrite))
                            val inSecondWrite = assertIs<FileNode>(compactedReopened.open(pathFromSecondWrite))

                            val expectedBytes = assertIs<FileNode>(originalReopened.open(pathFromFirstWrite))
                                .readChannel()
                                .consumeBytes()

                            assertContentEquals(expectedBytes, inFirstWrite.readChannel().consumeBytes())
                            assertContentEquals(expectedBytes, inSecondWrite.readChannel().consumeBytes())
                        }
                    }
                }
        }
    }

    private fun copyProjectToFileSystem(fs: SimpleFileSystem, pathPrefix: SimplePath = SimplePath.ROOT) {
        allFilesInProject()
            .forEach { path ->
                val sp = pathPrefix + path.asSimplePath()
                try {
                    when {
                        path.isDirectory() -> fs.createDirectory(sp)
                        path.isRegularFile() -> {
                            val target = fs.createFile(sp)
                            FileChannel.open(path)
                                .use { fc ->
                                    target.writeChannel().use { fc.copyTo(it) }
                                }
                        }

                        else -> error("Skipping path $path - neither file nor directory")
                    }
                } catch (ex: Throwable) {
                    throw IllegalStateException("Couldn't copy $path to filesystem", ex)
                }
            }
    }

    private fun compareProjectWithFs(fs: SimpleFileSystem) =
        allFilesInProject()
            .forEach { path ->
                val sp = path.asSimplePath()
                try {
                    when {
                        path.isDirectory() -> {
                            val hasChildren = Files.newDirectoryStream(path)
                                .any()
                            if (hasChildren) {
                                assertIs<DirectoryNode>(fs.open(sp))
                            } else {
                                // null is okey after compaction
                                fs.open(sp)?.let { assertIs<DirectoryNode>(it) }
                            }
                        }
                        path.isRegularFile() -> {
                            val expected = Files.readAllBytes(path)
                            val copy = assertIs<FileNode>(fs.open(sp))
                            val actual = copy.readChannel().consumeBytes()

                            assertEquals(expected.size, actual.size)
                            assertContentEquals(expected, actual)
                        }

                        else -> error("Skipping path $path - neither file nor directory")
                    }
                } catch (ex: Throwable) {
                    throw IllegalStateException("Comparison failed at $path", ex)
                }
            }

    private fun java.nio.file.Path.asSimplePath() =
        SimplePath.of(toString().replace(systemFileSeparator, "/"))
}
