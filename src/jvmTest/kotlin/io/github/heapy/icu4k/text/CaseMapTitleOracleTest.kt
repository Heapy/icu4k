package io.github.heapy.icu4k.text

import java.util.Locale
import kotlin.random.Random
import kotlin.test.*

class CaseMapTitleOracleTest {
    private fun trace(e: Edits): List<List<Int>> {
        val i = e.getFineIterator(); val r = mutableListOf<List<Int>>()
        while (i.next()) r.add(listOf(if (i.hasChange()) 1 else 0, i.oldLength(), i.newLength(), i.sourceIndex(), i.destinationIndex(), i.replacementIndex()))
        return r
    }
    private fun trace(e: com.ibm.icu.text.Edits): List<List<Int>> {
        val i = e.fineIterator; val r = mutableListOf<List<Int>>()
        while (i.next()) r.add(listOf(if (i.hasChange()) 1 else 0, i.oldLength(), i.newLength(), i.sourceIndex(), i.destinationIndex(), i.replacementIndex()))
        return r
    }
    @Test fun allTitleModesOptionsAndEditTracesMatch() {
        val random = Random(7803)
        val parts = listOf("i", "j", "I", "J", "İ", "ß", "Σ", "α", "\u0301", "\u0307", " ", ". ", "1", "😀", "𐐨", "\ud800", "\udc00")
        val texts = listOf("", "hELLO wORLD. nEXT.", "ijsselmeer i\u0301j\u0301", "123ABC xyz", "ßa ΟΣ", "a".repeat(101)) + List(40) { buildString { repeat(random.nextInt(1, 20)) { append(parts.random(random)) } } }
        for (id in listOf("", "en", "tr", "lt", "nl", "el")) for (mode in 0..2) for (adjust in 0..2) for (lower in listOf(false, true)) for (omit in listOf(false, true)) {
            var j = com.ibm.icu.text.CaseMap.toTitle(); var k = CaseMap.toTitle()
            if (mode == 1) { j = j.wholeString(); k = k.wholeString() }
            if (mode == 2) { j = j.sentences(); k = k.sentences() }
            if (adjust == 1) { j = j.noBreakAdjustment(); k = k.noBreakAdjustment() }
            if (adjust == 2) { j = j.adjustToCased(); k = k.adjustToCased() }
            if (!lower) { j = j.noLowercase(); k = k.noLowercase() }
            if (omit) { j = j.omitUnchangedText(); k = k.omitUnchangedText() }
            val locale = Locale.forLanguageTag(id)
            for (text in texts) {
                assertEquals(j.apply(locale, null, text), k.apply(locale, null, text), "$id/$mode/$adjust/$lower/$omit/$text")
                val a = StringBuilder("prefix:"); val b = StringBuilder("prefix:")
                val je = com.ibm.icu.text.Edits().also { it.addReplace(9, 2) }; val ke = Edits().also { it.addReplace(9, 2) }
                j.apply(locale, null, text, a, je); k.apply(locale, null, text, b, ke)
                assertEquals(a.toString(), b.toString()); assertEquals(trace(je), trace(ke))
            }
        }
    }
    @Test fun explicitIteratorIsUsedAndLeftAtSourcePosition() {
        for (text in listOf("", "hello WORLD. next", "ijsselmeer ijzer")) {
            val ji = com.ibm.icu.text.RuleBasedBreakIterator("[a-zA-Z]+;.;")
            val ki = RuleBasedBreakIterator("[a-zA-Z]+;.;")
            assertEquals(com.ibm.icu.text.CaseMap.toTitle().apply(Locale.forLanguageTag("nl"), ji, text), CaseMap.toTitle().apply(Locale.forLanguageTag("nl"), ki, text))
            assertEquals(ji.current(), ki.current()); assertEquals(ji.text.endIndex, ki.getText().getEndIndex())
        }
    }
    @Test fun iteratorIoFailuresAreWrappedInBothStringAndAppendableApis() {
        val failure = java.io.IOException("iterator")
        val j = object : com.ibm.icu.text.BreakIterator() {
            private var text: java.text.CharacterIterator = java.text.StringCharacterIterator("")
            override fun first(): Int = throw failure
            override fun last(): Int = 0
            override fun next(n: Int): Int = -1
            override fun next(): Int = -1
            override fun previous(): Int = -1
            override fun following(offset: Int): Int = -1
            override fun current(): Int = 0
            override fun getText(): java.text.CharacterIterator = text
            override fun setText(newText: java.text.CharacterIterator) { text = newText }
        }
        val k = object : BreakIterator() {
            private var text: CharacterIterator = StringCharacterIterator("")
            override fun clone(): BreakIterator = this
            override fun first(): Int = throw failure
            override fun last(): Int = 0
            override fun next(n: Int): Int = -1
            override fun next(): Int = -1
            override fun previous(): Int = -1
            override fun following(offset: Int): Int = -1
            override fun current(): Int = 0
            override fun getText(): CharacterIterator = text
            override fun setText(newText: CharacterIterator) { text = newText }
        }
        for (text in listOf("abc", "abc".repeat(40))) {
            assertSame(failure, assertFailsWith<com.ibm.icu.util.ICUUncheckedIOException> { com.ibm.icu.text.CaseMap.toTitle().apply(Locale.ROOT, j, text) }.cause)
            assertSame(failure, assertFailsWith<io.github.heapy.icu4k.util.ICUUncheckedIOException> { CaseMap.toTitle().apply(Locale.ROOT, k, text) }.cause)
            assertSame(failure, assertFailsWith<com.ibm.icu.util.ICUUncheckedIOException> { com.ibm.icu.text.CaseMap.toTitle().apply(Locale.ROOT, j, text, StringBuilder(), null) }.cause)
            assertSame(failure, assertFailsWith<io.github.heapy.icu4k.util.ICUUncheckedIOException> { CaseMap.toTitle().apply(Locale.ROOT, k, text, StringBuilder(), null) }.cause)
        }
    }

    @Test fun emptyCharSequenceCallbackOrderMatchesAcrossStringApis() {
        fun input(log: MutableList<String>) = object : CharSequence {
            override val length: Int get() { log.add("length"); return 0 }
            override fun get(index: Int): Char = error("empty get")
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("empty subsequence")
            override fun toString(): String { log.add("toString"); return "" }
        }
        for (mode in 0..3) for (omit in listOf(false, true)) {
            val a = mutableListOf<String>(); val b = mutableListOf<String>()
            val j = input(a); val k = input(b)
            val expected = when (mode) {
                0 -> (if (omit) com.ibm.icu.text.CaseMap.toLower().omitUnchangedText() else com.ibm.icu.text.CaseMap.toLower()).apply(Locale.ROOT, j)
                1 -> (if (omit) com.ibm.icu.text.CaseMap.toUpper().omitUnchangedText() else com.ibm.icu.text.CaseMap.toUpper()).apply(Locale.ROOT, j)
                2 -> (if (omit) com.ibm.icu.text.CaseMap.fold().omitUnchangedText() else com.ibm.icu.text.CaseMap.fold()).apply(j)
                else -> (if (omit) com.ibm.icu.text.CaseMap.toTitle().wholeString().omitUnchangedText() else com.ibm.icu.text.CaseMap.toTitle().wholeString()).apply(Locale.ROOT, null, j)
            }
            val actual = when (mode) {
                0 -> (if (omit) CaseMap.toLower().omitUnchangedText() else CaseMap.toLower()).apply(Locale.ROOT, k)
                1 -> (if (omit) CaseMap.toUpper().omitUnchangedText() else CaseMap.toUpper()).apply(Locale.ROOT, k)
                2 -> (if (omit) CaseMap.fold().omitUnchangedText() else CaseMap.fold()).apply(k)
                else -> (if (omit) CaseMap.toTitle().wholeString().omitUnchangedText() else CaseMap.toTitle().wholeString()).apply(Locale.ROOT, null, k)
            }
            assertEquals(expected, actual); assertEquals(a, b, "$mode/$omit")
        }
    }

}
