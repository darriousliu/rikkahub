package me.rerere.rikkahub.data.files

import kotlinx.cinterop.ExperimentalForeignApi
import me.rerere.rikkahub.utils.mkdirs
import me.rerere.rikkahub.utils.resolve
import platform.posix.symlink
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalForeignApi::class)
class SkillSymlinkTest {
    private val fixture = SkillTestFixture()

    @AfterTest
    fun close() = fixture.close()

    @Test
    fun canonicalPathRejectsEscapingDirectorySymlinkEvenWhenChildDoesNotExist() {
        val root = fixture.manager.getSkillsDir()
        val outside = fixture.root.resolve("outside").apply { mkdirs() }
        val directory = root.resolve("disk").apply { mkdirs() }
        assertEquals(0, symlink(outside.toString(), root.resolve("alias").toString()))
        assertEquals(0, symlink(outside.toString(), directory.resolve("link").toString()))
        assertNull(fixture.manager.getSkillDir("alias"))
        assertNull(fixture.manager.resolveSkillFile("disk", "link/missing/child"))
    }
}
