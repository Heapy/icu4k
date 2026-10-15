// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.text.RuleBasedBreakIterator
import io.github.heapy.icu4k.util.ULocale
import java.util.Locale
import kotlin.test.*
import com.ibm.icu.lang.UCharacter as Source
import com.ibm.icu.util.ULocale as SourceLocale

class UCharacterStringCaseOracleTest {
    private val texts = listOf("", "ASCII text", "Iİiı", "I\u0301 J\u0307", "ΟΣ ΣΟΣ", "ή ήλιος", "straße ß", "ijsselmeer i\u0301j\u0301", "123ABC xyz", "hELLO. nEXT!", "𐐨𐐀 😀 \ud800x\udc00", "abc".repeat(40), "one two".repeat(15))
    @Test fun localeStringsAndRawTitleOptions() {
        for (id in listOf("", "en", "tr", "az", "lt", "nl", "el", "de")) {
            val source = SourceLocale(id); val target = ULocale(id)
            for (text in texts) {
                assertEquals(Source.toLowerCase(source, text), UCharacter.toLowerCase(target, text), "$id/lower/$text")
                assertEquals(Source.toUpperCase(source, text), UCharacter.toUpperCase(target, text), "$id/upper/$text")
                for (options in listOf(0, 0x100, 0x200, 0x300, 0x400, 0x600, 0x20, 0x40, 0x420, 0x1440, 0x4000, 0x8000))
                    assertEquals(Source.toTitleCase(source, text, null, options), UCharacter.toTitleCase(target, text, null, options), "$id/title/$options/$text")
            }
        }
    }
    @Test fun explicitIteratorAndValidationOrder() {
        for (options in listOf(0, 0x100, 0x200, 0x400, 0x600, 0x20, 0x60, -1)) for (text in listOf(null, "", "ijsselmeer WORLD")) {
            val source = com.ibm.icu.text.RuleBasedBreakIterator("[a-zA-Z]+;.;")
            val target = RuleBasedBreakIterator("[a-zA-Z]+;.;")
            assertEquals(outcome { Source.toTitleCase(SourceLocale("nl"), text, source, options) }, outcome { UCharacter.toTitleCase(ULocale("nl"), text, target, options) }, "$options/$text")
            assertEquals(source.current(), target.current())
        }
        for (options in listOf(0x60, 0x80, 0xe0, -1))
            assertEquals(outcome { Source.toTitleCase(SourceLocale.ENGLISH, null, null, options) }, outcome { UCharacter.toTitleCase(ULocale.ENGLISH, null, null, options) })
    }
    @Test fun javaLocaleAndDefaultOverloads() {
        val saved = Locale.getDefault()
        val savedDisplay = Locale.getDefault(Locale.Category.DISPLAY)
        val savedFormat = Locale.getDefault(Locale.Category.FORMAT)
        try {
            for (id in listOf("tr", "lt", "el", "nl", "en")) {
                val locale = Locale.forLanguageTag(id)
                Locale.setDefault(locale)
                Locale.setDefault(Locale.Category.FORMAT, Locale.US)
                for (text in texts) {
                    assertEquals(Source.toLowerCase(text), UCharacter.toLowerCase(text))
                    assertEquals(Source.toUpperCase(text), UCharacter.toUpperCase(text))
                    assertEquals(Source.toTitleCase(text, null), UCharacter.toTitleCase(text, null))
                    assertEquals(Source.toLowerCase(locale, text), UCharacter.toLowerCase(locale, text))
                    assertEquals(Source.toUpperCase(locale, text), UCharacter.toUpperCase(locale, text))
                    assertEquals(Source.toTitleCase(locale, text, null, 0x100), UCharacter.toTitleCase(locale, text, null, 0x100))
                    assertEquals(Source.toLowerCase(null as Locale?, text), UCharacter.toLowerCase(null as Locale?, text))
                    assertEquals(Source.toTitleCase(null as Locale?, text, null), UCharacter.toTitleCase(null as Locale?, text, null))
                }
            }
        } finally {
            Locale.setDefault(saved)
            Locale.setDefault(Locale.Category.DISPLAY, savedDisplay)
            Locale.setDefault(Locale.Category.FORMAT, savedFormat)
        }
    }
    @Test fun virtualStringSetTextAndDefaultReadOrder() {
        val failure = java.io.IOException("setText")
        val sourceSeen = mutableListOf<String?>()
        val targetSeen = mutableListOf<String?>()
        val source = SourceStringIterator { sourceSeen.add(it); throw failure }
        val target = StringCaseTestIterator { targetSeen.add(it); throw failure }
        for (text in listOf(null, "istanbul")) {
            assertSame(failure, assertFailsWith<java.io.IOException> { Source.toTitleCase(SourceLocale.ENGLISH, text, source) })
            assertSame(failure, assertFailsWith<java.io.IOException> { UCharacter.toTitleCase(ULocale.ENGLISH, text, target) })
        }
        assertEquals(sourceSeen, targetSeen)
        val saved = Locale.getDefault()
        val savedDisplay = Locale.getDefault(Locale.Category.DISPLAY)
        val savedFormat = Locale.getDefault(Locale.Category.FORMAT)
        try {
            val sourceChanging = SourceStringIterator { Locale.setDefault(Locale.forLanguageTag("tr")) }
            val targetChanging = StringCaseTestIterator { Locale.setDefault(Locale.forLanguageTag("tr")) }
            Locale.setDefault(Locale.US)
            val expected = Source.toTitleCase(null as SourceLocale?, "istanbul", sourceChanging)
            Locale.setDefault(Locale.US)
            assertEquals(expected, UCharacter.toTitleCase(null as ULocale?, "istanbul", targetChanging))
            assertEquals("İstanbul", expected)
            Locale.setDefault(Locale.US)
            val javaExpected = Source.toTitleCase(null as Locale?, "istanbul", sourceChanging)
            Locale.setDefault(Locale.US)
            assertEquals(javaExpected, UCharacter.toTitleCase(null as Locale?, "istanbul", targetChanging))
        } finally {
            Locale.setDefault(saved)
            Locale.setDefault(Locale.Category.DISPLAY, savedDisplay)
            Locale.setDefault(Locale.Category.FORMAT, savedFormat)
        }
    }
    @Test fun caseMapNullableCharSequenceDispatchPrecedesFormatting() {
        val failure = java.io.IOException("CharSequence setText")
        val sourceSeen = mutableListOf<CharSequence?>()
        val targetSeen = mutableListOf<CharSequence?>()
        val source = object : SourceStringIterator({ error("String overload") }) {
            override fun setText(newText: CharSequence?) { sourceSeen.add(newText); throw failure }
        }
        val target = object : StringCaseTestIterator({ error("String overload") }) {
            override fun setText(newText: CharSequence?) { targetSeen.add(newText); throw failure }
        }
        for (text in listOf(null, "istanbul")) {
            assertSame(failure, assertFailsWith<java.io.IOException> { com.ibm.icu.text.CaseMap.toTitle().apply(Locale.US, source, text) })
            assertSame(failure, assertFailsWith<java.io.IOException> { io.github.heapy.icu4k.text.CaseMap.toTitle().apply(ULocale.US, target, text) })
            assertSame(failure, assertFailsWith<java.io.IOException> { com.ibm.icu.text.CaseMap.toTitle().apply(Locale.US, source, text, StringBuilder(), null) })
            assertSame(failure, assertFailsWith<java.io.IOException> { io.github.heapy.icu4k.text.CaseMap.toTitle().apply(ULocale.US, target, text, StringBuilder(), null) })
        }
        assertEquals(sourceSeen, targetSeen)
    }
    private fun outcome(block: () -> String): String = try { "value:${block()}" } catch (e: Exception) {
        if (e is NullPointerException) "NullPointerException" else "${e.javaClass.simpleName}:${e.message}"
    }
}


private open class SourceStringIterator(private val onSet: (String?) -> Unit) : com.ibm.icu.text.BreakIterator() {
    private var text = ""
    private var position = 0
    override fun setText(newText: String?) { onSet(newText); text = newText ?: ""; position = 0 }
    override fun setText(newText: java.text.CharacterIterator) { error("String overload expected") }
    override fun getText(): java.text.CharacterIterator = java.text.StringCharacterIterator(text)
    override fun first(): Int { position = 0; return 0 }
    override fun next(): Int { if (position == 0 && text.isNotEmpty()) { position = text.length; return position }; return DONE }
    override fun current(): Int = position
    override fun last(): Int = error("unused")
    override fun next(n: Int): Int = error("unused")
    override fun previous(): Int = error("unused")
    override fun following(offset: Int): Int = error("unused")
}
