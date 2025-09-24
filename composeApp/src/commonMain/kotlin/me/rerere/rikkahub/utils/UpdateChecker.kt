package me.rerere.rikkahub.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import me.rerere.common.PlatformContext
import kotlin.jvm.JvmInline

expect class UpdateChecker {
    fun checkUpdate(): Flow<UiState<UpdateInfo>>
    fun downloadUpdate(context: PlatformContext, download: UpdateDownload)
}

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String
)

@Serializable
data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>
)

/**
 * 版本号值类，封装版本号字符串并提供比较功能
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {

    /**
     * 将版本号分解为数字数组
     */
    private fun parseVersion(): List<Int> {
        return value.split(".")
            .map { it.toIntOrNull() ?: 0 }
    }

    /**
     * 实现 Comparable 接口的比较方法
     */
    override fun compareTo(other: Version): Int {
        val thisParts = this.parseVersion()
        val otherParts = other.parseVersion()

        val maxLength = maxOf(thisParts.size, otherParts.size)

        for (i in 0 until maxLength) {
            val thisPart = if (i < thisParts.size) thisParts[i] else 0
            val otherPart = if (i < otherParts.size) otherParts[i] else 0

            when {
                thisPart > otherPart -> return 1
                thisPart < otherPart -> return -1
            }
        }

        return 0
    }

    companion object {
        /**
         * 比较两个版本号字符串
         */
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }
    }
}

// 扩展操作符函数，使比较更直观
operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))
