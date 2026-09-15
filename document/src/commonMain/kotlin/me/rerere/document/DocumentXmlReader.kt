package me.rerere.document

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming

/** Keeps XmlPullParser.next() semantics when using xmlutil on all platforms. */
internal class DocumentXmlReader(xml: String, private val namespaceAware: Boolean = true) {
    private val reader = xmlStreaming.newGenericReader(xml, expandEntities = true)
    private var pending = false
    var eventType = EventType.START_DOCUMENT
        private set
    var text: String? = null
        private set
    val depth: Int get() = reader.depth
    val name: String
        get() = if (namespaceAware || reader.prefix.isEmpty()) reader.localName
        else "${reader.prefix}:${reader.localName}"

    fun getAttributeValue(namespace: String?, name: String): String? {
        if (namespaceAware) return reader.getAttributeValue(namespace, name)
        return (0 until reader.attributeCount).firstOrNull { index ->
            val prefix = reader.getAttributePrefix(index)
            val qualifiedName = if (prefix.isEmpty()) reader.getAttributeLocalName(index)
            else "$prefix:${reader.getAttributeLocalName(index)}"
            qualifiedName == name
        }?.let(reader::getAttributeValue)
    }

    fun next(): EventType {
        if (eventType == EventType.END_DOCUMENT) return eventType
        text = null
        var event = if (pending) reader.eventType else reader.next()
        pending = false
        while (event in skippedEvents) event = reader.next()
        if (event in textEvents) {
            val content = StringBuilder()
            // XmlPullParser coalesces text, entities and CDATA, including across comments.
            // The original DOCX/PPTX code reads exactly one TEXT event per run.
            do {
                if (event in textEvents) content.append(reader.text)
                event = reader.next()
            } while (event in textEvents || event in skippedEvents)
            pending = true
            text = content.toString()
            eventType = EventType.TEXT
        } else {
            eventType = event
        }
        return eventType
    }

    private companion object {
        val textEvents = setOf(EventType.TEXT, EventType.CDSECT, EventType.IGNORABLE_WHITESPACE)
        val skippedEvents = setOf(EventType.COMMENT, EventType.PROCESSING_INSTRUCTION, EventType.DOCDECL)
    }
}
