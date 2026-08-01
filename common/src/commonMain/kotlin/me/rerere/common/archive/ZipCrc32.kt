package me.rerere.common.archive

internal class ZipCrc32 {
    private var crc: Int = -1

    val value: UInt
        get() = (crc xor -1).toUInt()

    fun update(bytes: ByteArray, byteCount: Int) {
        require(byteCount in 0..bytes.size)
        for (index in 0 until byteCount) {
            crc = (crc ushr 8) xor TABLE[(crc xor bytes[index].toInt()) and 0xff]
        }
    }

    private companion object {
        val TABLE = IntArray(256) { seed ->
            var value = seed
            repeat(8) {
                value = if ((value and 1) != 0) {
                    (value ushr 1) xor POLYNOMIAL
                } else {
                    value ushr 1
                }
            }
            value
        }

        const val POLYNOMIAL: Int = -306674912
    }
}
