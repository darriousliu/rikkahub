package me.rerere.common.archive

import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readUIntLe
import kotlinx.io.readUShortLe
import kotlinx.io.writeUIntLe
import kotlinx.io.writeUShortLe

internal class CommonZipArchive(
    private val rawDeflateCodec: RawDeflateCodec,
) : ZipArchive {
    override fun create(sink: Sink, writeEntries: ZipArchiveWriter.() -> Unit) {
        try {
            Writer(sink, rawDeflateCodec).apply(writeEntries).finish()
        } finally {
            sink.close()
        }
    }

    override suspend fun read(source: Source, readEntry: suspend (ZipArchiveEntry) -> Unit) {
        try {
            while (source.request(Int.SIZE_BYTES.toLong())) {
                when (val signature = source.readUIntLe()) {
                    LOCAL_FILE_HEADER_SIGNATURE -> readLocalEntry(source, readEntry)
                    CENTRAL_DIRECTORY_HEADER_SIGNATURE, END_OF_CENTRAL_DIRECTORY_SIGNATURE -> return
                    else -> throw ZipArchiveException("Invalid ZIP record signature: 0x${signature.toString(16)}")
                }
            }
        } finally {
            source.close()
        }
    }

    private suspend fun readLocalEntry(
        source: Source,
        readEntry: suspend (ZipArchiveEntry) -> Unit,
    ) {
        source.readUShortLe() // version needed
        val flags = source.readUShortLe().toInt()
        val method = source.readUShortLe().toInt()
        source.skip(4) // modification time/date
        val headerCrc = source.readUIntLe()
        val headerCompressedSize = source.readUIntLe()
        val headerUncompressedSize = source.readUIntLe()
        val nameLength = source.readUShortLe().toInt()
        val extraLength = source.readUShortLe().toLong()
        val rawName = source.readByteArray(nameLength).decodeToString()
        source.skip(extraLength)

        if ((flags and ENCRYPTED_FLAG) != 0) {
            throw ZipArchiveException("Encrypted ZIP entries are not supported: $rawName")
        }
        if (method != STORED_METHOD && method != DEFLATED_METHOD) {
            throw ZipArchiveException("Unsupported ZIP compression method $method: $rawName")
        }

        val isDirectory = rawName.endsWith('/')
        val normalizedName = ZipEntryPathPolicy.normalizeOrNull(rawName)
            ?: throw ZipArchiveException("Unsafe ZIP entry path: $rawName")
        val entryName = if (isDirectory) "$normalizedName/" else normalizedName
        val usesDataDescriptor = (flags and DATA_DESCRIPTOR_FLAG) != 0
        if (method == STORED_METHOD && usesDataDescriptor) {
            throw ZipArchiveException("Stored ZIP entries with data descriptors are not supported: $entryName")
        }

        val entry = StreamingEntry(
            name = entryName,
            isDirectory = isDirectory,
            source = source,
            rawDeflateCodec = rawDeflateCodec,
            method = method,
            usesDataDescriptor = usesDataDescriptor,
            headerCrc = headerCrc,
            headerCompressedSize = headerCompressedSize,
            headerUncompressedSize = headerUncompressedSize,
        )
        readEntry(entry)
        entry.discardIfNeeded()
    }

    private class StreamingEntry(
        override val name: String,
        override val isDirectory: Boolean,
        private val source: Source,
        private val rawDeflateCodec: RawDeflateCodec,
        private val method: Int,
        private val usesDataDescriptor: Boolean,
        private val headerCrc: UInt,
        private val headerCompressedSize: UInt,
        private val headerUncompressedSize: UInt,
    ) : ZipArchiveEntry {
        private var consumed = false

        override fun copyTo(sink: Sink): Long {
            check(!consumed) { "ZIP entry content can only be consumed once: $name" }
            consumed = true
            val crc = ZipCrc32()
            val result = when (method) {
                STORED_METHOD -> copyStored(source, sink, headerCompressedSize.toLong(), crc)
                DEFLATED_METHOD -> rawDeflateCodec.inflate(source, sink, crc::update)
                else -> error("Unsupported ZIP method")
            }
            sink.flush()

            val expected = if (usesDataDescriptor) {
                readDataDescriptor(source)
            } else {
                Descriptor(
                    crc = headerCrc,
                    compressedSize = headerCompressedSize,
                    uncompressedSize = headerUncompressedSize,
                )
            }
            validateEntry(expected, result, crc.value, name)
            return result.outputByteCount
        }

        fun discardIfNeeded() {
            if (!consumed) {
                val sink = DiscardingRawSink().buffered()
                try {
                    copyTo(sink)
                } finally {
                    sink.close()
                }
            }
        }
    }

    private class Writer(
        private val sink: Sink,
        private val rawDeflateCodec: RawDeflateCodec,
    ) : ZipArchiveWriter {
        private val entries = mutableListOf<WrittenEntry>()
        private val entryNames = mutableSetOf<String>()
        private var offset = 0L
        private var finished = false

        override fun add(name: String, source: Source) {
            check(!finished) { "ZIP archive is already finished" }
            val normalizedName = normalizeOutputName(name, isDirectory = false)
            if (!entryNames.add(normalizedName)) {
                throw ZipArchiveException("Duplicate ZIP entry: $normalizedName")
            }
            val nameBytes = normalizedName.encodeToByteArray()
            val localHeaderOffset = offset.toZipUInt("Archive offset")
            writeLocalHeader(
                nameBytes = nameBytes,
                flags = UTF8_FLAG or DATA_DESCRIPTOR_FLAG,
                method = DEFLATED_METHOD,
                crc = 0u,
                compressedSize = 0u,
                uncompressedSize = 0u,
            )

            val crc = ZipCrc32()
            val result = try {
                rawDeflateCodec.deflate(source, sink, crc::update)
            } finally {
                source.close()
            }
            offset += result.outputByteCount
            val compressedSize = result.outputByteCount.toZipUInt("Compressed entry size")
            val uncompressedSize = result.inputByteCount.toZipUInt("Uncompressed entry size")
            writeDataDescriptor(crc.value, compressedSize, uncompressedSize)
            entries += WrittenEntry(
                nameBytes = nameBytes,
                flags = UTF8_FLAG or DATA_DESCRIPTOR_FLAG,
                method = DEFLATED_METHOD,
                crc = crc.value,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localHeaderOffset,
                isDirectory = false,
            )
        }

        override fun addDirectory(name: String) {
            check(!finished) { "ZIP archive is already finished" }
            val normalizedName = normalizeOutputName(name, isDirectory = true)
            if (!entryNames.add(normalizedName)) {
                throw ZipArchiveException("Duplicate ZIP entry: $normalizedName")
            }
            val nameBytes = normalizedName.encodeToByteArray()
            val localHeaderOffset = offset.toZipUInt("Archive offset")
            writeLocalHeader(
                nameBytes = nameBytes,
                flags = UTF8_FLAG,
                method = STORED_METHOD,
                crc = 0u,
                compressedSize = 0u,
                uncompressedSize = 0u,
            )
            entries += WrittenEntry(
                nameBytes = nameBytes,
                flags = UTF8_FLAG,
                method = STORED_METHOD,
                crc = 0u,
                compressedSize = 0u,
                uncompressedSize = 0u,
                localHeaderOffset = localHeaderOffset,
                isDirectory = true,
            )
        }

        fun finish() {
            check(!finished) { "ZIP archive is already finished" }
            finished = true
            require(entries.size <= UShort.MAX_VALUE.toInt()) { "ZIP64 entry counts are not supported" }
            val centralDirectoryOffset = offset.toZipUInt("Central directory offset")
            entries.forEach(::writeCentralDirectoryEntry)
            val centralDirectorySize = (offset - centralDirectoryOffset.toLong()).toZipUInt("Central directory size")
            sink.writeUIntLe(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
            sink.writeUShortLe(0u)
            sink.writeUShortLe(0u)
            sink.writeUShortLe(entries.size.toUShort())
            sink.writeUShortLe(entries.size.toUShort())
            sink.writeUIntLe(centralDirectorySize)
            sink.writeUIntLe(centralDirectoryOffset)
            sink.writeUShortLe(0u)
            offset += END_OF_CENTRAL_DIRECTORY_SIZE
            sink.flush()
        }

        private fun writeLocalHeader(
            nameBytes: ByteArray,
            flags: Int,
            method: Int,
            crc: UInt,
            compressedSize: UInt,
            uncompressedSize: UInt,
        ) {
            require(nameBytes.size <= UShort.MAX_VALUE.toInt()) { "ZIP entry name is too long" }
            sink.writeUIntLe(LOCAL_FILE_HEADER_SIGNATURE)
            sink.writeUShortLe(VERSION_NEEDED)
            sink.writeUShortLe(flags.toUShort())
            sink.writeUShortLe(method.toUShort())
            sink.writeUIntLe(0u) // modification time/date
            sink.writeUIntLe(crc)
            sink.writeUIntLe(compressedSize)
            sink.writeUIntLe(uncompressedSize)
            sink.writeUShortLe(nameBytes.size.toUShort())
            sink.writeUShortLe(0u)
            sink.write(nameBytes)
            offset += LOCAL_FILE_HEADER_SIZE + nameBytes.size
        }

        private fun writeDataDescriptor(crc: UInt, compressedSize: UInt, uncompressedSize: UInt) {
            sink.writeUIntLe(DATA_DESCRIPTOR_SIGNATURE)
            sink.writeUIntLe(crc)
            sink.writeUIntLe(compressedSize)
            sink.writeUIntLe(uncompressedSize)
            offset += DATA_DESCRIPTOR_SIZE
        }

        private fun writeCentralDirectoryEntry(entry: WrittenEntry) {
            sink.writeUIntLe(CENTRAL_DIRECTORY_HEADER_SIGNATURE)
            sink.writeUShortLe(VERSION_NEEDED)
            sink.writeUShortLe(VERSION_NEEDED)
            sink.writeUShortLe(entry.flags.toUShort())
            sink.writeUShortLe(entry.method.toUShort())
            sink.writeUIntLe(0u) // modification time/date
            sink.writeUIntLe(entry.crc)
            sink.writeUIntLe(entry.compressedSize)
            sink.writeUIntLe(entry.uncompressedSize)
            sink.writeUShortLe(entry.nameBytes.size.toUShort())
            sink.writeUShortLe(0u) // extra length
            sink.writeUShortLe(0u) // comment length
            sink.writeUShortLe(0u) // disk number
            sink.writeUShortLe(0u) // internal attributes
            sink.writeUIntLe(if (entry.isDirectory) DIRECTORY_ATTRIBUTE else 0u)
            sink.writeUIntLe(entry.localHeaderOffset)
            sink.write(entry.nameBytes)
            offset += CENTRAL_DIRECTORY_HEADER_SIZE + entry.nameBytes.size
        }
    }

    private data class WrittenEntry(
        val nameBytes: ByteArray,
        val flags: Int,
        val method: Int,
        val crc: UInt,
        val compressedSize: UInt,
        val uncompressedSize: UInt,
        val localHeaderOffset: UInt,
        val isDirectory: Boolean,
    )

    private data class Descriptor(
        val crc: UInt,
        val compressedSize: UInt,
        val uncompressedSize: UInt,
    )

    private class DiscardingRawSink : RawSink {
        override fun write(source: Buffer, byteCount: Long) {
            source.skip(byteCount)
        }

        override fun flush() = Unit
        override fun close() = Unit
    }

    private companion object {
        const val ENCRYPTED_FLAG = 0x0001
        const val DATA_DESCRIPTOR_FLAG = 0x0008
        const val UTF8_FLAG = 0x0800
        const val STORED_METHOD = 0
        const val DEFLATED_METHOD = 8

        const val LOCAL_FILE_HEADER_SIZE = 30L
        const val DATA_DESCRIPTOR_SIZE = 16L
        const val CENTRAL_DIRECTORY_HEADER_SIZE = 46L
        const val END_OF_CENTRAL_DIRECTORY_SIZE = 22L
        const val IO_BUFFER_SIZE = 8 * 1024

        val VERSION_NEEDED: UShort = 20u
        val DIRECTORY_ATTRIBUTE: UInt = 0x10u
        val LOCAL_FILE_HEADER_SIGNATURE: UInt = 0x04034b50u
        val DATA_DESCRIPTOR_SIGNATURE: UInt = 0x08074b50u
        val CENTRAL_DIRECTORY_HEADER_SIGNATURE: UInt = 0x02014b50u
        val END_OF_CENTRAL_DIRECTORY_SIGNATURE: UInt = 0x06054b50u

        fun normalizeOutputName(name: String, isDirectory: Boolean): String {
            val normalized = ZipEntryPathPolicy.normalizeOrNull(name)
                ?: throw ZipArchiveException("Unsafe ZIP entry path: $name")
            return if (isDirectory) "$normalized/" else normalized
        }

        fun copyStored(source: Source, sink: Sink, byteCount: Long, crc: ZipCrc32): RawDeflateResult {
            var remaining = byteCount
            val buffer = ByteArray(IO_BUFFER_SIZE)
            while (remaining > 0) {
                val requested = minOf(buffer.size.toLong(), remaining).toInt()
                val read = source.readAtMostTo(buffer, 0, requested)
                if (read == -1) throw ZipArchiveException("Truncated stored ZIP entry")
                crc.update(buffer, read)
                sink.write(buffer, 0, read)
                remaining -= read
            }
            return RawDeflateResult(inputByteCount = byteCount, outputByteCount = byteCount)
        }

        fun readDataDescriptor(source: Source): Descriptor {
            val first = source.readUIntLe()
            val crc = if (first == DATA_DESCRIPTOR_SIGNATURE) source.readUIntLe() else first
            return Descriptor(
                crc = crc,
                compressedSize = source.readUIntLe(),
                uncompressedSize = source.readUIntLe(),
            )
        }

        fun validateEntry(expected: Descriptor, actual: RawDeflateResult, crc: UInt, name: String) {
            if (expected.crc != crc) {
                throw ZipArchiveException(
                    "ZIP entry CRC mismatch for $name: expected 0x${expected.crc.toString(16)}, " +
                        "actual 0x${crc.toString(16)}"
                )
            }
            if (expected.compressedSize.toLong() != actual.inputByteCount) {
                throw ZipArchiveException("ZIP entry compressed size mismatch for $name")
            }
            if (expected.uncompressedSize.toLong() != actual.outputByteCount) {
                throw ZipArchiveException("ZIP entry uncompressed size mismatch for $name")
            }
        }

        fun Long.toZipUInt(label: String): UInt {
            require(this in 0..UInt.MAX_VALUE.toLong()) { "$label exceeds ZIP32 limits: $this" }
            return toUInt()
        }
    }
}
