package me.rerere.rikkahub.ui.components.richtext

import org.jsoup.Jsoup

internal typealias RichHtmlDocument = org.jsoup.nodes.Document
internal typealias RichHtmlElement = org.jsoup.nodes.Element
internal typealias RichHtmlNode = org.jsoup.nodes.Node
internal typealias RichHtmlTextNode = org.jsoup.nodes.TextNode

internal fun parseRichHtml(html: String): RichHtmlDocument = Jsoup.parse(html)
