package me.rerere.rikkahub.data.sync.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WebDavDateParserTest {
    @Test
    fun `parses rfc 1123 date`() {
        assertEquals(
            "1994-11-15T08:12:31Z",
            parseWebDavLastModified("Tue, 15 Nov 1994 08:12:31 GMT")?.toString(),
        )
    }

    @Test
    fun `rejects rfc 850 when reduced year conflicts with weekday`() {
        withDefaultLocale(Locale.US) {
            assertNull(parseWebDavLastModified("Tuesday, 15-Nov-94 08:12:31 GMT"))
        }
    }

    @Test
    fun `preserves legacy rfc 850 reduced year behavior`() {
        withDefaultLocale(Locale.US) {
            assertEquals(
                "2094-11-15T08:12:31Z",
                parseWebDavLastModified("Monday, 15-Nov-94 08:12:31 GMT")?.toString(),
            )
        }
    }

    @Test
    fun `parses iso instant fallback`() {
        assertEquals(
            "2024-03-01T00:30:00Z",
            parseWebDavLastModified("2024-03-01T00:30:00Z")?.toString(),
        )
    }

    @Test
    fun `blank and invalid dates return null`() {
        assertNull(parseWebDavLastModified(null))
        assertNull(parseWebDavLastModified(" "))
        assertNull(parseWebDavLastModified("not-a-date"))
    }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
