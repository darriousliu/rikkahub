package me.rerere.mermaid

/**
 * Renders Mermaid to a resvg-compatible SVG, or returns null if there is no diagram.
 * This is a synchronous CPU operation; callers should run it off the UI thread.
 * [configJson] supplies site-level Mermaid defaults (e.g. theme); source frontmatter stays intact.
 *
 * @throws MermaidRenderException if merman cannot parse or render the source.
 */
@Throws(MermaidRenderException::class)
fun renderMermaidSvg(source: String, configJson: String = "{}"): String? = nativeRenderMermaidSvg(source, configJson)

class MermaidRenderException(message: String) : Exception(message)

internal expect fun nativeRenderMermaidSvg(source: String, configJson: String): String?
