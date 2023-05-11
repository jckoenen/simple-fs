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
    fun `copying project folder should preserve content`() = withFileSystem { subject, _ ->
        copyProjectToFileSystem(subject)

        compareProjectWithFs(subject)
    }

    @Test
    fun `copying project folder should preserve content across closing`() = withFileSystem { beforeClose, fsPath ->
        copyProjectToFileSystem(beforeClose)
        beforeClose.close()

        SimpleFileSystem(fsPath).use(::compareProjectWithFs)
    }

    private fun copyProjectToFileSystem(fs: SimpleFileSystem) {
        allFilesInProject()
            .forEach { path ->
                val sp = SimplePath.of(path.toString())
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
            }
    }

    private fun compareProjectWithFs(fs: SimpleFileSystem) =
        allFilesInProject()
            .forEach { path ->
                val sp = SimplePath.of(path.toString())
                when {
                    path.isDirectory() -> assertIs<DirectoryNode>(fs.open(sp))
                    path.isRegularFile() -> {
                        val expected = Files.readAllBytes(path)
                        val copy = fs.open(sp)
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
