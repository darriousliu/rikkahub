package me.rerere.rikkahub.platform

public fun interface CharacterCardMetadataReader {
    public fun read(imageBytes: ByteArray): Result<String>
}

public expect fun createCharacterCardMetadataReader(): CharacterCardMetadataReader
