package me.rerere.highlight.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The JavaScript to Kotlin regex translation grammars depend on. */
class RegexesTest {
    @Test
    fun `translates constructs java rejects`() {
        // `[^]` is "any character" in JavaScript and a syntax error in Java.
        assertTrue(compilePattern("""a[^]b""").matches("a\nb"))
        // `[]` never matches in JavaScript and is a syntax error in Java.
        assertFalse(compilePattern("""[]""").containsMatchIn("x"))
        // `[` and `&` are literal inside a JavaScript character class, but mean nested class union
        // and class intersection in Java.
        assertTrue(compilePattern("""[{}[\],:]""").matches("["))
        assertTrue(compilePattern("""[a&&b]""").matches("&"))
        // A `{` that opens no quantifier is literal in JavaScript and an error in Java.
        assertTrue(compilePattern("""\$\{|a{""").containsMatchIn("a{"))
        // Real quantifiers must survive untouched.
        assertTrue(compilePattern("""[0-9]{4}(-[0-9][0-9]){0,2}""").matches("2024-05-27"))
    }

    @Test
    fun `unicode mode keeps javascript predefined character class semantics`() {
        val word = compilePattern("""\w""", unicode = true)
        val digit = compilePattern("""\d""", unicode = true)
        val identifier = compilePattern(
            """[\p{XID_Start}_]\p{XID_Continue}*""",
            unicode = true,
        )

        assertTrue(word.matches("a"))
        assertFalse(word.matches("中"))
        assertTrue(digit.matches("1"))
        assertFalse(digit.matches("١"))
        assertTrue(identifier.matches("café2"))
        assertTrue(identifier.matches("变量2"))
        assertFalse(identifier.matches("2value"))
    }

    @Test
    fun `counts capture groups the way highlight_js does`() {
        assertEquals(0, countMatchGroups("""\s+"""))
        assertEquals(0, countMatchGroups("""(?:a|b)"""))
        assertEquals(1, countMatchGroups("""(a|b)"""))
        assertEquals(3, countMatchGroups("""(a)((b))"""))
        // A bracketed `(` is literal and must not be counted.
        assertEquals(1, countMatchGroups("""[(]\w(x)"""))
        assertEquals(1, countMatchGroups("""(?<name>a)"""))
        assertEquals(0, countMatchGroups("""(?<=a)b"""))
    }

    @Test
    fun `renumbers backreferences when expressions are joined`() {
        val joined = rewriteBackreferences(
            listOf("""(['"]).*?\1""", """(\w)-\1"""),
            joinWith = "|",
        )

        // Each expression gains a wrapping group, so the backreferences shift accordingly.
        assertEquals("""((['"]).*?\2)|((\w)-\4)""", joined)

        val pattern = compilePattern(joined)
        assertTrue(pattern.containsMatchIn("""'quoted'"""))
        assertTrue(pattern.containsMatchIn("a-a"))
        assertFalse(pattern.containsMatchIn("a-b"))
    }

    @Test
    fun `builds alternations and lookaheads`() {
        assertEquals("(?:a|b)", either("a", "b"))
        assertEquals("(a|b)", either("a", "b", capture = true))
        assertEquals("(?=x)", lookahead("x"))
        assertEquals("(?:x)?", optional("x"))
        assertEquals("(?:x)*", anyNumberOfTimes("x"))
        assertEquals("ab", concat("a", "b"))
    }
}
