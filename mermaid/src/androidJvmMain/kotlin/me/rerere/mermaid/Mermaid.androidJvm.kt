package me.rerere.mermaid

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal interface MermaidNativeLibrary : Library {
    fun rikkahub_mermaid_render_svg_with_config(
        source: ByteArray, length: Long, config: ByteArray, configLength: Long,
    ): Pointer
    fun rikkahub_mermaid_result_svg(result: Pointer): Pointer?
    fun rikkahub_mermaid_result_error(result: Pointer): Pointer?
    fun rikkahub_mermaid_result_free(result: Pointer)
}

private val nativeLibrary: MermaidNativeLibrary by lazy {
    Native.load("rikkahub_mermaid", MermaidNativeLibrary::class.java)
}

internal actual fun nativeRenderMermaidSvg(source: String, configJson: String): String? {
    val bytes = source.encodeToByteArray()
    val config = configJson.encodeToByteArray()
    // JNA requires a nonempty array even when the native input length is zero.
    val result = nativeLibrary.rikkahub_mermaid_render_svg_with_config(
        bytes + 0.toByte(), bytes.size.toLong(), config + 0.toByte(), config.size.toLong(),
    )
    return try {
        nativeLibrary.rikkahub_mermaid_result_error(result)?.let {
            throw MermaidRenderException(it.getString(0, "UTF-8"))
        }
        nativeLibrary.rikkahub_mermaid_result_svg(result)?.getString(0, "UTF-8")
    } finally {
        nativeLibrary.rikkahub_mermaid_result_free(result)
    }
}
