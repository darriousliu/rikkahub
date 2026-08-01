package me.rerere.rikkahub.ui.components.richtext

import com.fleeksoft.ksoup.Ksoup

typealias RichHtmlDocument = com.fleeksoft.ksoup.nodes.Document
typealias RichHtmlElement = com.fleeksoft.ksoup.nodes.Element
typealias RichHtmlNode = com.fleeksoft.ksoup.nodes.Node
typealias RichHtmlTextNode = com.fleeksoft.ksoup.nodes.TextNode

fun parseRichHtml(html: String): RichHtmlDocument = Ksoup.parse(html)
