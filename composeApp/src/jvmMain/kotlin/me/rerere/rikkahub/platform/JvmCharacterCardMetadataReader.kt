package me.rerere.rikkahub.platform

import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.png.PngChunkType
import com.drew.metadata.png.PngDirectory
import java.io.ByteArrayInputStream

public actual fun createCharacterCardMetadataReader(): CharacterCardMetadataReader =
    MetadataExtractorCharacterCardMetadataReader

private object MetadataExtractorCharacterCardMetadataReader : CharacterCardMetadataReader {
    override fun read(imageBytes: ByteArray): Result<String> = runCatching {
        val metadata = ByteArrayInputStream(imageBytes).use(ImageMetadataReader::readMetadata)
        val pngDirectory = metadata.getDirectoriesOfType(PngDirectory::class.java)
            .firstOrNull { directory ->
                directory.pngChunkType == PngChunkType.tEXt &&
                    directory.getString(PngDirectory.TAG_TEXTUAL_DATA)?.startsWith("[chara:") == true
            } ?: error("No tEXt chunk found, please check if the image is a character card")
        val value = pngDirectory.getString(PngDirectory.TAG_TEXTUAL_DATA)
        Regex("""\[chara:\s*(.+?)]""")
            .find(value)
            ?.groupValues
            ?.get(1)
            ?: error("No character data found")
    }
}
