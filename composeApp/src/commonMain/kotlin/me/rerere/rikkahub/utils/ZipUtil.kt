package me.rerere.rikkahub.utils


interface ZipWriter : AutoCloseable {
    /**
     * 添加内存数据条目
     */
    fun addEntry(entryName: String, data: ByteArray)
    /**
     * 从文件路径流式添加条目（不全部加载到内存）
     */
    fun addEntryFromFile(entryName: String, filePath: String)
}

interface ZipUtil {
    fun unzip(sourcePath: String, destinationPath: String): Boolean

    fun compress(sourcePath: String, destinationPath: String): Boolean

    fun compressMultiple(sourcePaths: List<String>, destinationPath: String): Boolean

    fun getZipEntryList(zipFilePath: String): List<Pair<String, Boolean>>

    fun getZipEntryContent(zipFilePath: String, entryName: String): ByteArray?

    /**
     * 创建 ZipWriter，调用方逐条添加后 close
     */
    fun createZipWriter(destinationPath: String): ZipWriter
    /**
     * 从 zip 中提取单个条目写入目标文件（流式，不全部加载到内存）
     */
    fun extractEntryToFile(zipFilePath: String, entryName: String, targetFilePath: String): Boolean
}
