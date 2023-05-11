package de.joekoe.simplefs.internal

import de.joekoe.simplefs.DirectoryNode
import de.joekoe.simplefs.FileNode
import de.joekoe.simplefs.SimplePath
import de.joekoe.simplefs.consumeBytes
import de.joekoe.simplefs.copyTo
import de.joekoe.simplefs.withFileSystem
import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SingleFileFileSystemTest {
    private fun allFilesInProject() =
        Files.walk(Path("")).filter { it.toString().isNotBlank() }
            .asSequence()

    @Test
    fun `copying project folder should preserve content`() = withFileSystem { subject ->
        allFilesInProject()
            .forEach { path ->
                val sp = SimplePath.of(path.toString())
                when {
                    path.isDirectory() -> subject.createDirectory(sp)
                    path.isRegularFile() -> {
                        val target = subject.createFile(sp)
                        FileChannel.open(path)
                            .use { fc ->
                                target.writeChannel().use { fc.copyTo(it) }
                            }
                    }

                    else -> error("Skipping path $path - neither file nor directory")
                }
            }

        allFilesInProject()
            .forEach { path ->
                val sp = SimplePath.of(path.toString())
                when {
                    path.isDirectory() -> assertIs<DirectoryNode>(subject.open(sp))
                    path.isRegularFile() -> {
                        val expected = Files.readAllBytes(path)
                        val copy = subject.open(sp)
                        assertIs<FileNode>(copy)
                        val actual = copy.readChannel().consumeBytes()

                        assertEquals(expected.size, actual.size)
                        expected.zip(actual)
                            .forEach { (expectedByte, actualByte) ->
                                assertEquals(expectedByte, actualByte)
                            }
                    }

                    else -> error("Skipping path $path - neither file nor directory")
                }
            }
    }
}