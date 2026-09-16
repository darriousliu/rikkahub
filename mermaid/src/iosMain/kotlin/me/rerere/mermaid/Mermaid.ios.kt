@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package me.rerere.mermaid

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import me.rerere.mermaid.ffi.rikkahub_mermaid_render_svg_with_config
import me.rerere.mermaid.ffi.rikkahub_mermaid_result_error
import me.rerere.mermaid.ffi.rikkahub_mermaid_result_free
import me.rerere.mermaid.ffi.rikkahub_mermaid_result_svg

internal actual fun nativeRenderMermaidSvg(source: String, configJson: String): String? {
    val bytes = source.encodeToByteArray()
    val config = configJson.encodeToByteArray()
    val result = (bytes + 0.toByte()).usePinned { sourceBuffer ->
        (config + 0.toByte()).usePinned { configBuffer ->
            checkNotNull(rikkahub_mermaid_render_svg_with_config(
                sourceBuffer.addressOf(0).reinterpret(), bytes.size.toULong(),
                configBuffer.addressOf(0).reinterpret(), config.size.toULong(),
            ))
        }
    }
    return try {
        rikkahub_mermaid_result_error(result)?.let {
            throw MermaidRenderException(it.toKString())
        }
        rikkahub_mermaid_result_svg(result)?.toKString()
    } finally {
        rikkahub_mermaid_result_free(result)
    }
}
