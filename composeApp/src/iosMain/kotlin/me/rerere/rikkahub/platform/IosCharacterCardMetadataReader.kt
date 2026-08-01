package me.rerere.rikkahub.platform

public actual fun createCharacterCardMetadataReader(): CharacterCardMetadataReader =
    IosPngCharacterCardMetadataReader

private object IosPngCharacterCardMetadataReader : CharacterCardMetadataReader {
    override fun read(imageBytes: ByteArray): Result<String> = runCatching {
        require(imageBytes.size >= PNG_SIGNATURE.size &&
            imageBytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
        ) { "Invalid PNG signature" }

        var offset = PNG_SIGNATURE.size
        var characterData: String? = null
        var reachedEnd = false
        while (offset < imageBytes.size) {
            require(offset + PNG_CHUNK_OVERHEAD <= imageBytes.size) { "Truncated PNG chunk header" }
            val length = imageBytes.readUnsignedInt(offset)
            require(length <= Int.MAX_VALUE) { "PNG chunk is too large" }
            val dataStart = offset + PNG_CHUNK_HEADER_SIZE
            val dataEnd = dataStart.toLong() + length
            val chunkEnd = dataEnd + PNG_CRC_SIZE
            require(chunkEnd <= imageBytes.size) { "Truncated PNG chunk data" }

            val type = imageBytes.ascii(offset + PNG_LENGTH_SIZE, dataStart)
            val dataEndIndex = dataEnd.toInt()
            val expectedCrc = imageBytes.readUnsignedInt(dataEndIndex)
            val actualCrc = imageBytes.crc32(
                startIndex = offset + PNG_LENGTH_SIZE,
                endIndex = dataEndIndex,
            )
            require(actualCrc == expectedCrc) { "Invalid PNG chunk CRC" }

            if (type == TEXT_CHUNK) {
                val separator = imageBytes.indexOf(0, startIndex = dataStart, endIndex = dataEndIndex)
                if (separator > dataStart && imageBytes.ascii(dataStart, separator) == CHARACTER_KEYWORD) {
                    characterData = imageBytes.ascii(separator + 1, dataEndIndex)
                }
            }
            offset = chunkEnd.toInt()
            if (type == END_CHUNK) {
                require(length == 0L) { "Invalid IEND chunk" }
                reachedEnd = true
                break
            }
        }
        require(reachedEnd) { "Missing IEND chunk" }
        characterData ?: error("No tEXt chunk found, please check if the image is a character card")
    }
}

private fun ByteArray.readUnsignedInt(offset: Int): Long =
    ((this[offset].toLong() and 0xffL) shl 24) or
        ((this[offset + 1].toLong() and 0xffL) shl 16) or
        ((this[offset + 2].toLong() and 0xffL) shl 8) or
        (this[offset + 3].toLong() and 0xffL)

private fun ByteArray.ascii(startIndex: Int, endIndex: Int): String = buildString(endIndex - startIndex) {
    for (index in startIndex until endIndex) {
        append((this@ascii[index].toInt() and 0xff).toChar())
    }
}

private fun ByteArray.indexOf(value: Int, startIndex: Int, endIndex: Int): Int {
    for (index in startIndex until endIndex) {
        if ((this[index].toInt() and 0xff) == value) return index
    }
    return -1
}

private fun ByteArray.crc32(startIndex: Int, endIndex: Int): Long {
    var crc = -1
    for (index in startIndex until endIndex) {
        crc = crc xor (this[index].toInt() and 0xff)
        repeat(8) {
            crc = (crc ushr 1) xor (CRC32_POLYNOMIAL and -(crc and 1))
        }
    }
    return (crc xor -1).toLong() and 0xffffffffL
}

private const val PNG_LENGTH_SIZE = 4
private const val PNG_CHUNK_HEADER_SIZE = 8
private const val PNG_CRC_SIZE = 4
private const val PNG_CHUNK_OVERHEAD = PNG_CHUNK_HEADER_SIZE + PNG_CRC_SIZE
private const val TEXT_CHUNK = "tEXt"
private const val END_CHUNK = "IEND"
private const val CHARACTER_KEYWORD = "chara"
private const val CRC32_POLYNOMIAL = -306674912
private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
