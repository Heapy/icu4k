package io.github.heapy.icu4k.text

import java.util.Locale
import kotlin.random.Random
import kotlin.test.*

class CaseMapLocaleOracleTest {
    private fun trace(edits: Edits): List<List<Int>> {
        val result = mutableListOf<List<Int>>(); val i = edits.getFineIterator()
        while (i.next()) result.add(listOf(if (i.hasChange()) 1 else 0, i.oldLength(), i.newLength(), i.sourceIndex(), i.destinationIndex(), i.replacementIndex()))
        return result
    }
    private fun trace(edits: com.ibm.icu.text.Edits): List<List<Int>> {
        val result = mutableListOf<List<Int>>(); val i = edits.fineIterator
        while (i.next()) result.add(listOf(if (i.hasChange()) 1 else 0, i.oldLength(), i.newLength(), i.sourceIndex(), i.destinationIndex(), i.replacementIndex()))
        return result
    }
    @Test fun localeSensitiveApisAndEditsMatch() {
        val random = Random(7803)
        val alphabet = listOf("a", "I", "İ", "ı", "ß", "Σ", "σ", "ς", "ή", "ά", "ΐ", "\u0301", "\u0307", "\u0345", "𐐀", "𐐨", "\ud800", "\udc00", " ")
        val texts = listOf("", "ΑΣ ΣΑΣ ΟΣ", "iIİı", "Straße", "I\u0301 J\u0307", "ή ήλιος", "ΐ άι", "a".repeat(101)) + List(200) { buildString { repeat(random.nextInt(1, 25)) { append(alphabet.random(random)) } } }
        for (id in listOf("", "en", "tr", "az", "lt", "el", "nl", "hy")) {
            val locale = Locale.forLanguageTag(id)
            for (text in texts) for (omit in listOf(false, true)) {
                val jl = if (omit) com.ibm.icu.text.CaseMap.toLower().omitUnchangedText() else com.ibm.icu.text.CaseMap.toLower()
                val kl = if (omit) CaseMap.toLower().omitUnchangedText() else CaseMap.toLower()
                val ju = if (omit) com.ibm.icu.text.CaseMap.toUpper().omitUnchangedText() else com.ibm.icu.text.CaseMap.toUpper()
                val ku = if (omit) CaseMap.toUpper().omitUnchangedText() else CaseMap.toUpper()
                assertEquals(jl.apply(locale, text), kl.apply(locale, text)); assertEquals(ju.apply(locale, text), ku.apply(locale, text))
                val a = StringBuilder("prefix:"); val b = StringBuilder("prefix:")
                val je = com.ibm.icu.text.Edits().also { it.addUnchanged(99) }; val ke = Edits().also { it.addUnchanged(99) }
                jl.apply(locale, text, a, je); kl.apply(locale, text, b, ke)
                assertEquals(a.toString(), b.toString()); assertEquals(trace(je), trace(ke))
                a.setLength(0); b.setLength(0)
                ju.apply(locale, text, a, je); ku.apply(locale, text, b, ke)
                assertEquals(a.toString(), b.toString()); assertEquals(trace(je), trace(ke))
            }
        }
    }
    @Test fun appendIOExceptionIsWrappedAndOriginalCauseRetained() {
        val failure = java.io.IOException("append")
        val broken = object : Appendable {
            override fun append(value: Char): Appendable = throw failure
            override fun append(value: CharSequence?): Appendable = throw failure
            override fun append(value: CharSequence?, start: Int, end: Int): Appendable = throw failure
        }
        assertSame(failure, assertFailsWith<io.github.heapy.icu4k.util.ICUUncheckedIOException> { CaseMap.toLower().apply(Locale.ROOT, "A", broken, null) }.cause)
        assertSame(failure, assertFailsWith<io.github.heapy.icu4k.util.ICUUncheckedIOException> { CaseMap.toUpper().apply(Locale.ROOT, "a", broken, null) }.cause)
        assertSame(failure, assertFailsWith<io.github.heapy.icu4k.util.ICUUncheckedIOException> { CaseMap.fold().apply("A", broken, null) }.cause)
    }
    @Test fun nullSourceAndDestinationOutcomesAndEditsMatch() {
        fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Exception) { failure.javaClass.simpleName }
        for (text in listOf(null, "", "A", "a", "ß", "Ι\u0301")) for (omit in listOf(false, true)) for (mode in 0..2) {
            val j = com.ibm.icu.text.Edits().also { it.addReplace(3, 1) }
            val k = Edits().also { it.addReplace(3, 1) }
            val expected = outcome {
                when (mode) {
                    0 -> (if (omit) com.ibm.icu.text.CaseMap.toLower().omitUnchangedText() else com.ibm.icu.text.CaseMap.toLower()).apply(Locale.ROOT, text, null as Appendable?, j)
                    1 -> (if (omit) com.ibm.icu.text.CaseMap.toUpper().omitUnchangedText() else com.ibm.icu.text.CaseMap.toUpper()).apply(Locale.ROOT, text, null as Appendable?, j)
                    else -> (if (omit) com.ibm.icu.text.CaseMap.fold().omitUnchangedText() else com.ibm.icu.text.CaseMap.fold()).apply(text, null as Appendable?, j)
                }
            }
            val actual = outcome {
                when (mode) {
                    0 -> (if (omit) CaseMap.toLower().omitUnchangedText() else CaseMap.toLower()).apply(Locale.ROOT, text, null as Appendable?, k)
                    1 -> (if (omit) CaseMap.toUpper().omitUnchangedText() else CaseMap.toUpper()).apply(Locale.ROOT, text, null as Appendable?, k)
                    else -> (if (omit) CaseMap.fold().omitUnchangedText() else CaseMap.fold()).apply(text, null as Appendable?, k)
                }
            }
            assertEquals(expected, actual, "$mode/$omit/$text"); assertEquals(trace(j), trace(k))
        }
    }

    @Test fun nullLocaleUsesCurrentDefault() {
        val saved = Locale.getDefault()
        try {
            for (id in listOf("tr", "lt", "el", "en")) {
                Locale.setDefault(Locale.forLanguageTag(id))
                val locale: Locale? = null
                for (text in listOf("Iİiı", "ΟΣ", "I\u0301", "ή ήλιος")) {
                    assertEquals(com.ibm.icu.text.CaseMap.toLower().apply(locale, text), CaseMap.toLower().apply(locale, text))
                    assertEquals(com.ibm.icu.text.CaseMap.toUpper().apply(locale, text), CaseMap.toUpper().apply(locale, text))
                }
            }
        } finally { Locale.setDefault(saved) }
    }

}
