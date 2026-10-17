@file:Suppress("UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.MessageFormat as Source
import com.ibm.icu.text.MessagePattern as SourcePattern
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.text.AttributedCharacterIterator
import kotlin.test.*

class MessageFormatOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) {
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is ClassCastException -> "cast"; else -> e.javaClass.simpleName + ":" + e.message?.replace("com.ibm.icu.", "io.github.heapy.icu4k.") }
    }
    private fun normalized(value: Any?): Any? = when (value) {
        is Array<*> -> value.map(::normalized)
        is Map<*, *> -> value.mapValues { normalized(it.value) }
        is Number -> value.toString()
        is java.util.Date -> value.time
        else -> value
    }
    private fun runs(iterator: AttributedCharacterIterator): Any {
        val result = ArrayList<Any>()
        var i = iterator.beginIndex
        while (i < iterator.endIndex) {
            iterator.setIndex(i)
            val limit = iterator.runLimit
            val text = buildString { for (j in i until limit) append(iterator.setIndex(j)) }
            iterator.setIndex(i)
            result.add(listOf(i, limit, text, iterator.attributes.entries.associate { it.key.toString().substringAfter('(') to it.value.toString().substringAfter('(') }))
            i = limit
        }
        return result
    }
    private fun compare(a: Source, b: MessageFormat, values: List<Any?>, label: String) {
        assertEquals(a.toPattern(), b.toPattern(), label)
        assertEquals(a.usesNamedArguments(), b.usesNamedArguments(), label)
        assertEquals(a.argumentNames, b.getArgumentNames(), label)
        assertEquals(a.locale, b.getLocale(), label)
        for (value in values) {
            assertEquals(outcome { a.format(value) }, outcome { b.format(value) }, "$label/$value")
            assertEquals(outcome { runs(a.formatToCharacterIterator(value)) }, outcome { runs(b.formatToCharacterIterator(value)) }, "$label/$value/attributes")
            val at = outcome { a.format(value) }
            if (at is String) {
                val ap = java.text.ParsePosition(0); val bp = ParsePosition(0)
                assertEquals(outcome { normalized(a.parseObject(at, ap)) }, outcome { normalized(b.parseObject(at, bp)) }, "$label/parse/$at")
                assertEquals(listOf(ap.index, ap.errorIndex), listOf(bp.index, bp.errorIndex), "$label/position")
            }
        }
    }
    @Test fun fullPublicEngineMatchesAcrossLocalesModesNumbersDatesAndNestedArguments() {
        val patterns = listOf("", "Hello {0}!", "{0,number}", "{0,number,000.00}", "{0,number,currency}", "{0,number,::precision-integer}",
            "{0,spellout}", "{0,ordinal}", "{0,duration}", "{0,date,short} {0,time,HH:mm}", "{0,date,::yMMMd}",
            "{0,choice,0#none|1#{1}|1<{0,number} items}", "{0,plural,offset:1 =0{none} one{{1} and one} other{{1} and # others}}",
            "'{0}' and ''{1}''", "{who}: {n,plural,one{# item} other{# items}}", "{gender,select,male{He} other{They}}: {n,selectordinal,one{#st} other{#th}}")
        val values = listOf<Any?>(null, emptyArray<Any?>(), arrayOf<Any?>(0, "A"), arrayOf<Any?>(1, "A"), arrayOf<Any?>(2.5, "A"), arrayOf<Any?>(1234L, "A"), arrayOf(java.util.Date(0)), arrayOf("bad", null),
            mapOf("0" to 2, "1" to "B"), mapOf("who" to "A", "n" to 2, "gender" to "male"), mapOf("who" to null, "n" to 1))
        for (id in listOf("en_US", "fr", "ru", "ar", "zh@calendar=chinese", "he@calendar=hebrew")) for (pattern in patterns) for (mode in SourcePattern.ApostropheMode.values()) {
            val a = Source("", SourceLocale(id)); val b = MessageFormat("", ULocale(id))
            assertEquals(outcome { a.applyPattern(pattern, mode) }, outcome { b.applyPattern(pattern, MessagePattern.ApostropheMode.valueOf(mode.name)) }, "$id/$pattern/$mode")
            compare(a, b, values, "$id/$pattern/$mode")
        }
    }
    @Test fun rawJdkChoiceAndCustomFormatsKeepIdentityAndRecursiveAttributes() {
        val a = Source("{0} / {1}", SourceLocale.US); val b = MessageFormat("{0} / {1}", ULocale.US)
        val choice = java.text.ChoiceFormat("0#none|1#{1,number,000}")
        a.setFormatByArgumentIndex(0, choice); b.setFormatByArgumentIndex(0, choice)
        assertSame(choice, b.getFormats()[0])
        for (value in listOf(arrayOf<Any?>(0, 12), arrayOf<Any?>(1, 12), arrayOf<Any?>(2, 3.5))) {
            assertEquals(a.format(value), b.format(value))
            assertEquals(runs(a.formatToCharacterIterator(value)), runs(b.formatToCharacterIterator(value)))
        }
        val custom = object : java.text.Format() {
            override fun format(obj: Any?, out: StringBuffer, pos: java.text.FieldPosition): StringBuffer = out.append("[$obj]")
            override fun parseObject(source: String?, pos: java.text.ParsePosition): Any? { pos.index += 1; return "custom" }
        }
        a.setFormat(0, custom); b.setFormat(0, custom)
        assertSame(custom, b.getFormats()[0])
        val ap = java.text.ParsePosition(0); val bp = ParsePosition(0)
        assertEquals(normalized(a.parse("x / tail", ap)), normalized(b.parse("x / tail", bp)))
        assertEquals(ap.index, bp.index)
        val copy = b.clone()
        assertSame(custom, copy.getFormats()[0])
        copy.setFormat(0, null)
        assertSame(custom, b.getFormats()[0])
    }
    @Test fun localeSettersPatternFailuresAndAutoQuoteMatchSource() {
        for (text in listOf("{0,number}", "{name}", "")) {
            val a = Source(text, SourceLocale.US); val b = MessageFormat(text, ULocale.US)
            for (id in listOf("fr", "ar", "en_US")) {
                a.setLocale(SourceLocale(id)); b.setLocale(ULocale(id))
                compare(a, b, listOf(arrayOf(1234), mapOf("name" to "A")), text)
            }
            a.setFormatByArgumentName("0", null); b.setFormatByArgumentName("0", null)
            assertEquals(outcome { a.toPattern() }, outcome { b.toPattern() })
            assertEquals(outcome { a.setLocale(SourceLocale.JAPAN) }, outcome { b.setLocale(ULocale.JAPAN) })
            assertEquals(a.getULocale().toString(), b.getULocale().toString())
            for (bad in listOf<String?>("{", "{0,unknown}", null)) {
                assertEquals(outcome { a.applyPattern(bad) }, outcome { b.applyPattern(bad) })
                assertEquals(a.toPattern(), b.toPattern())
                assertEquals(outcome { a.hashCode() }, outcome { b.hashCode() })
                assertEquals(outcome { a.formats.size }, outcome { b.getFormats().size })
            }
        }
        for (pattern in listOf("", "Don't", "'{x}'", "'", "''", "x'{", "{0,choice,0#don't|1#{1}}", "'{x} {0}")) assertEquals(Source.autoQuoteApostrophe(pattern), MessageFormat.autoQuoteApostrophe(pattern))
    }
}
