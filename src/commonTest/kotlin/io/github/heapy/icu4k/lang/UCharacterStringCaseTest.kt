// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.text.RuleBasedBreakIterator
import io.github.heapy.icu4k.text.BreakIterator
import io.github.heapy.icu4k.text.CharacterIterator
import io.github.heapy.icu4k.text.StringCharacterIterator
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class UCharacterStringCaseTest {
    @Test fun localeSensitiveStringsAndTitleOptions() {
        assertEquals("ıi", UCharacter.toLowerCase(ULocale("tr"), "Iİ"))
        assertEquals("I\u0307\u0301".lowercase(), UCharacter.toLowerCase(ULocale("lt"), "I\u0301"))
        assertEquals("STRASSE", UCharacter.toUpperCase(ULocale.GERMAN, "straße"))
        assertEquals("ος", UCharacter.toLowerCase(ULocale("el"), "ΟΣ"))
        assertEquals("IJsselmeer IJzer", UCharacter.toTitleCase(ULocale("nl"), "ijsselmeer ijzer", null))
        assertEquals("Hello WORLD", UCharacter.toTitleCase(ULocale.ENGLISH, "hello WORLD", null, UCharacter.TITLECASE_NO_LOWERCASE))
        assertEquals("123abc", UCharacter.toTitleCase(ULocale.ENGLISH, "123ABC", null, UCharacter.TITLECASE_NO_BREAK_ADJUSTMENT))
        assertFailsWith<NullPointerException> { UCharacter.toLowerCase(ULocale.ENGLISH, null) }
        assertFailsWith<NullPointerException> { UCharacter.toUpperCase(ULocale.ENGLISH, null) }
        assertFailsWith<IllegalArgumentException> { UCharacter.toTitleCase(ULocale.ENGLISH, "x", null, 0x60) }
        val iterator = RuleBasedBreakIterator("[a-zA-Z]+;.;")
        assertFailsWith<IllegalArgumentException> { UCharacter.toTitleCase(ULocale.ENGLISH, "x", iterator, 0x20) }
        assertEquals("Hello WORLD", UCharacter.toTitleCase(ULocale.ENGLISH, "hello WORLD", iterator, 0x100))
        assertEquals(11, iterator.getText().getEndIndex())
    }
    @Test fun nullableStringDispatchAndIteratorValidation() {
        val seen = mutableListOf<String?>()
        val failure = IllegalArgumentException("setText callback")
        val iterator = StringCaseTestIterator { seen.add(it); throw failure }
        assertSame(failure, assertFailsWith<IllegalArgumentException> { UCharacter.toTitleCase(ULocale.ENGLISH, null, iterator) })
        assertEquals(listOf<String?>(null), seen)
        seen.clear()
        assertFailsWith<IllegalArgumentException> { UCharacter.toTitleCase(ULocale.ENGLISH, null, iterator, 0x20) }
        assertTrue(seen.isEmpty())
    }
    @Test fun defaultAndNullLocaleTrackGeneralDefault() {
        val saved = ULocale.getDefault()
        val savedDisplay = ULocale.getDefault(ULocale.Category.DISPLAY)
        val savedFormat = ULocale.getDefault(ULocale.Category.FORMAT)
        try {
            ULocale.setDefault(ULocale("tr"))
            assertEquals("ı", UCharacter.toLowerCase("I"))
            assertEquals("İ", UCharacter.toUpperCase("i"))
            assertEquals("İstanbul", UCharacter.toTitleCase("istanbul", null))
            assertEquals("ı", UCharacter.toLowerCase(null as ULocale?, "I"))
            ULocale.setDefault(ULocale.ENGLISH)
            assertEquals("i", UCharacter.toLowerCase("I"))
        } finally {
            ULocale.setDefault(saved)
            ULocale.setDefault(ULocale.Category.DISPLAY, savedDisplay)
            ULocale.setDefault(ULocale.Category.FORMAT, savedFormat)
        }
    }
}


internal open class StringCaseTestIterator(private val onSet: (String?) -> Unit) : BreakIterator() {
    private var text = ""
    private var position = 0
    override fun setText(newText: String?) { onSet(newText); text = newText ?: ""; position = 0 }
    override fun setText(newText: CharacterIterator) { error("String overload expected") }
    override fun getText(): CharacterIterator = StringCharacterIterator(text)
    override fun clone(): BreakIterator = StringCaseTestIterator(onSet).also { it.text = text; it.position = position }
    override fun first(): Int { position = 0; return 0 }
    override fun next(): Int { if (position == 0 && text.isNotEmpty()) { position = text.length; return position }; return DONE }
    override fun current(): Int = position
    override fun last(): Int = error("unused")
    override fun next(n: Int): Int = error("unused")
    override fun previous(): Int = error("unused")
    override fun following(offset: Int): Int = error("unused")
}
