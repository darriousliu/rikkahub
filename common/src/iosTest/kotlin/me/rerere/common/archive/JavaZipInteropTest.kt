package me.rerere.common.archive

import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class JavaZipInteropTest {
    @Test
    fun readsOriginalJavaWriterIncludingDeflatedEmptyDirectory() = runBlocking {
        val expected = linkedMapOf(
            "settings.json" to "{\"fixture\":\"JDK 2.4.5 API\"}".encodeToByteArray(),
            "rikka_hub.db" to byteArrayOf(0, 1, 2, -1),
            "rikka_hub-wal" to "wal".encodeToByteArray(),
            "rikka_hub-shm" to "shm".encodeToByteArray(),
            "skills/空目录/" to byteArrayOf(),
            "upload/中文 😀.txt" to "正文\nsecond line".encodeToByteArray(),
        )
        val names = mutableListOf<String>()
        PlatformZipArchive.read(Buffer().apply { write(Base64.decode(javaZip)) }) { entry ->
            names += entry.name
            assertEquals(entry.name.endsWith('/'), entry.isDirectory)
            assertContentEquals(expected[entry.name], entry.readBytes(), entry.name)
        }
        assertEquals(expected.keys.toList(), names)
    }

    // Generated independently with java.util.zip.ZipOutputStream, the API used in 2.4.5.
    // All six ZipEntry objects use default DEFLATED/data descriptors, including the empty directory.
    // Each entry uses setTime(1789142400000L). SHA-256 of the 821-byte archive:
    // 648950ddd53d35b7ca7e370267f5ffd28ab4437376089138bff3fab6c5de79bc
    private val javaZip =
        "UEsDBBQACAgIAAAALF0AAAAAAAAAAAAAAAANAAAAc2V0dGluZ3MuanNvbqtWSsusKCktSlWyUvJy8VYw0jPRM1VwDPBUqgUAUEsH" +
        "CD7BYHEdAAAAGwAAAFBLAwQUAAgICAAAACxdAAAAAAAAAAAAAAAADAAAAHJpa2thX2h1Yi5kYmNgZPoPAFBLBwgkOLI/BgAAAAQA" +
        "AABQSwMEFAAICAgAAAAsXQAAAAAAAAAAAAAAAA0AAAByaWtrYV9odWItd2FsK0/MAQBQSwcIUtAdlgUAAAADAAAAUEsDBBQACAgI" +
        "AAAALF0AAAAAAAAAAAAAAAANAAAAcmlra2FfaHViLXNobSvOyAUAUEsHCFHz0TcFAAAAAwAAAFBLAwQUAAgICAAAACxdAAAAAAAA" +
        "AAAAAAAAEQAAAHNraWxscy/nqbrnm67lvZUvAwBQSwcIAAAAAAIAAAAAAAAAUEsDBBQACAgIAAAALF0AAAAAAAAAAAAAAAAWAAAA" +
        "dXBsb2FkL+S4reaWhyDwn5iALnR4dHu2dvGzae1cxanJ+XkpCjmZeakAUEsHCHTdZIUUAAAAEgAAAFBLAQIUABQACAgIAAAALF0+" +
        "wWBxHQAAABsAAAANAAAAAAAAAAAAAAAAAAAAAABzZXR0aW5ncy5qc29uUEsBAhQAFAAICAgAAAAsXSQ4sj8GAAAABAAAAAwAAAAA" +
        "AAAAAAAAAAAAWAAAAHJpa2thX2h1Yi5kYlBLAQIUABQACAgIAAAALF1S0B2WBQAAAAMAAAANAAAAAAAAAAAAAAAAAJgAAAByaWtr" +
        "YV9odWItd2FsUEsBAhQAFAAICAgAAAAsXVHz0TcFAAAAAwAAAA0AAAAAAAAAAAAAAAAA2AAAAHJpa2thX2h1Yi1zaG1QSwECFAAU" +
        "AAgICAAAACxdAAAAAAIAAAAAAAAAEQAAAAAAAAAAAAAAAAAYAQAAc2tpbGxzL+epuuebruW9lS9QSwECFAAUAAgICAAAACxddN1k" +
        "hRQAAAASAAAAFgAAAAAAAAAAAAAAAABZAQAAdXBsb2FkL+S4reaWhyDwn5iALnR4dFBLBQYAAAAABgAGAG4BAACxAQAAAAA="
}
