package me.rerere.rikkahub.utils

import android.content.Context
import androidx.core.net.toUri
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.buffered
import kotlin.io.copyTo
import kotlin.io.inputStream
import kotlin.io.outputStream
import kotlin.io.readBytes
import kotlin.use

class AndroidZipWriter(destinationPath: String) : ZipWriter {
    private val zipOut = ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationPath)))

    override fun addEntry(entryName: String, data: ByteArray) {
        zipOut.putNextEntry(ZipEntry(entryName))
        zipOut.write(data)
        zipOut.closeEntry()
    }

    override fun addEntryFromFile(entryName: String, filePath: String) {
        val file = File(filePath)
        zipOut.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            fis.copyTo(zipOut, bufferSize = 8192)
        }
        zipOut.closeEntry()
    }

    override fun close() {
        zipOut.close()
    }
}

class AndroidZipUtil : ZipUtil, KoinComponent {
    override fun unzip(sourcePath: String, destinationPath: String): Boolean {
        return try {
            val inputStream = getInputStream(sourcePath) ?: return false

            val destDir = File(destinationPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            inputStream.use { fis ->
                ZipInputStream(fis.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(destinationPath, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            // Zip Slip protection
                            val destPath = destDir.canonicalPath
                            val entryPath = newFile.canonicalPath
                            if (!entryPath.startsWith(destPath + File.separator)) {
                                zis.closeEntry()
                                entry = zis.nextEntry
                                continue
                            }

                            newFile.parentFile?.mkdirs()
                            newFile.outputStream().buffered().use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun compress(
        sourcePath: String,
        destinationPath: String
    ): Boolean {
        return try {
            val sourceFile = File(sourcePath)
            val destOutputStream = getOutputStream(destinationPath)

            ZipOutputStream(destOutputStream?.buffered()).use { zos ->
                compressRecursive(sourceFile, sourceFile.name, zos)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun compressMultiple(sourcePaths: List<String>, destinationPath: String): Boolean {
        return try {
            val destOutputStream = getOutputStream(destinationPath)

            ZipOutputStream(destOutputStream?.buffered()).use { zos ->
                for (sourcePath in sourcePaths) {
                    val sourceFile = File(sourcePath)
                    compressRecursive(sourceFile, sourceFile.name, zos)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun compressRecursive(file: File, fileName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                val entry = ZipEntry("$fileName/")
                zos.putNextEntry(entry)
                zos.closeEntry()
            } else {
                for (child in children) {
                    compressRecursive(child, "$fileName/${child.name}", zos)
                }
            }
        } else {
            val entry = ZipEntry(fileName)
            zos.putNextEntry(entry)
            file.inputStream().buffered().use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    override fun getZipEntryList(zipFilePath: String): List<Pair<String, Boolean>> {
        val entryList = mutableListOf<Pair<String, Boolean>>()
        try {
            val inputStream = getInputStream(zipFilePath)

            inputStream?.use { fis ->
                ZipInputStream(fis.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        entryList.add(entry.name to entry.isDirectory)
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return entryList
    }

    override fun getZipEntryContent(
        zipFilePath: String,
        entryName: String
    ): ByteArray? {
        return try {
            val inputStream = getInputStream(zipFilePath)

            return inputStream?.use { fis ->
                ZipInputStream(fis.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == entryName) {
                            return@use zis.readBytes()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun createZipWriter(destinationPath: String): ZipWriter {
        return AndroidZipWriter(destinationPath)
    }

    override fun extractEntryToFile(
        zipFilePath: String,
        entryName: String,
        targetFilePath: String
    ): Boolean {
        return try {
            val zipFile = ZipFile(zipFilePath)
            zipFile.use { zf ->
                val entry = zf.getEntry(entryName) ?: return false
                val targetFile = File(targetFilePath)
                targetFile.parentFile?.mkdirs()
                zf.getInputStream(entry).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getInputStream(path: String): InputStream? {
        return if (path.startsWith("content://") || path.startsWith("file://")) {
            get<Context>().contentResolver.openInputStream(path.toUri())
        } else {
            File(path).inputStream()
        }
    }

    private fun getOutputStream(path: String): OutputStream? {
        return if (path.startsWith("content://") || path.startsWith("file://")) {
            get<Context>().contentResolver.openOutputStream(path.toUri())
        } else {
            File(path).outputStream()
        }
    }
}
