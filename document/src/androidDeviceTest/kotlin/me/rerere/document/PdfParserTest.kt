package me.rerere.document

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfParserTest {
    @Test
    fun packagedMupdfStillParsesPdfAfterMovingToAndroidSourceSet() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cmp79.pdf")
        file.writeBytes(Base64.decode(
                "JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUg" +
                "L1BhZ2VzIC9LaWRzIFszIDAgUl0gL0NvdW50IDEgPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAg" +
                "UiAvTWVkaWFCb3ggWzAgMCAzMDAgMjAwXSAvUmVzb3VyY2VzIDw8IC9Gb250IDw8IC9GMSA0IDAgUiA+PiA+PiAvQ29udGVudHMg" +
                "NSAwIFIgPj4KZW5kb2JqCjQgMCBvYmoKPDwgL1R5cGUgL0ZvbnQgL1N1YnR5cGUgL1R5cGUxIC9CYXNlRm9udCAvSGVsdmV0aWNh" +
                "ID4+CmVuZG9iago1IDAgb2JqCjw8IC9MZW5ndGggNDkgPj4Kc3RyZWFtCkJUIC9GMSAxNCBUZiAyMCAxMDAgVGQgKENNUDc5IFBE" +
                "RiByZXRhaW5lZCkgVGogRVQKZW5kc3RyZWFtCmVuZG9iagp4cmVmCjAgNgowMDAwMDAwMDAwIDY1NTM1IGYgCjAwMDAwMDAwMDkg" +
                "MDAwMDAgbiAKMDAwMDAwMDA1OCAwMDAwMCBuIAowMDAwMDAwMTE1IDAwMDAwIG4gCjAwMDAwMDAyNDEgMDAwMDAgbiAKMDAwMDAw" +
                "MDMxMSAwMDAwMCBuIAp0cmFpbGVyCjw8IC9TaXplIDYgL1Jvb3QgMSAwIFIgPj4Kc3RhcnR4cmVmCjQxMAolJUVPRgo="
        ))
        try {
            val text = PdfParser.parserPdf(file)
            assertTrue(text.startsWith("---Page 1:\n"))
            assertTrue(text.contains("CMP79 PDF retained"))
        } finally {
            file.delete()
        }
    }
}
