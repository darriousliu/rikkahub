package me.rerere.rikkahub.ui.components.richtext

import com.fleeksoft.ksoup.Ksoup

internal typealias RichHtmlDocument = com.fleeksoft.ksoup.nodes.Document
internal typealias RichHtmlElement = com.fleeksoft.ksoup.nodes.Element
internal typealias RichHtmlNode = com.fleeksoft.ksoup.nodes.Node
internal typealias RichHtmlTextNode = com.fleeksoft.ksoup.nodes.TextNode

internal fun parseRichHtml(html: String): RichHtmlDocument = Ksoup.parse(html)
