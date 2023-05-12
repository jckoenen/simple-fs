package de.joekoe.simplefs.internal

import de.joekoe.simplefs.DirectoryNode
import de.joekoe.simplefs.FileNode
import de.joekoe.simplefs.SimpleFileSystem
import de.joekoe.simplefs.SimplePath
import de.joekoe.simplefs.consumeBytes
import de.joekoe.simplefs.copyTo
import de.joekoe.simplefs.withFileSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.channels.FileChannel
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DisabledOnOs(OS.WINDOWS) // TODO
class SingleFileFileSystemTest {

    private val ignoredFolders = listOf(
        "build/test-results", // fails on any stdout/sterr output. gradle writes them to a file
        ".gradle", // fails on windows due to file lock
        "build/classes/kotlin/test", // inline functions create class files, the test names won't fit into a segment
        "build/kotlin" // cache files on windows can exceed the node limit per directory
    )

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

    private fun copyProjectToFileSystem(fs: SimpleFileSystem) {
        allFilesInProject()
            .forEach { path ->
                val sp = SimplePath.of(path.toString())
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
                val sp = SimplePath.of(path.toString())
                try {
                    when {
                        path.isDirectory() -> assertIs<DirectoryNode>(fs.open(sp))
                        path.isRegularFile() -> {
                            val expected = Files.readAllBytes(path)
                            val copy = fs.open(sp)
                            assertIs<FileNode>(copy)
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
}
