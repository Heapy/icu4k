@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.ICUUncheckedIOException
import java.io.IOException
import kotlin.test.*
import com.ibm.icu.text.ListFormatter as Original
import com.ibm.icu.util.ULocale as OriginalLocale

class ListFormatterOracleTest {
    private fun key(field: Any?): Int = when (field) {
        null -> -1
        Original.Field.LITERAL, ListFormatField.LITERAL -> 0
        Original.Field.ELEMENT, ListFormatField.ELEMENT -> 1
        Original.SpanField.LIST_SPAN, ListSpanField.LIST_SPAN -> 2
        else -> error("unknown field $field")
    }
    private fun compare(j: Original, k: ListFormatter, items: List<Any?>) {
        assertEquals(j.format(items), k.format(items))
        val jv = j.formatToValue(items); val kv = k.formatToValue(items)
        assertEquals(jv.toString(), kv.toString()); assertEquals(jv.length, kv.length)
        val jp = com.ibm.icu.text.ConstrainedFieldPosition(); val kp = ConstrainedFieldPosition()
        repeat(200) {
            val found = jv.nextPosition(jp)
            assertEquals(found, kv.nextPosition(kp))
            assertEquals(listOf(key(jp.field), jp.fieldValue, jp.start, jp.limit), listOf(key(kp.getField()), kp.getFieldValue(), kp.getStart(), kp.getLimit()))
            if (!found) {
                val ja = jv.toCharacterIterator(); val ka = kv.toCharacterIterator()
                for (i in 0..kv.length) {
                    assertEquals(ja.setIndex(i), ka.setIndex(i))
                    assertEquals(ja.attributes.entries.associate { key(it.key) to if (it.value is java.text.Format.Field) key(it.value) else it.value }, ka.attributes.entries.associate { key(it.key) to if (it.value is FormatField) key(it.value) else it.value })
                    assertEquals(ja.runStart, ka.runStart); assertEquals(ja.runLimit, ka.runLimit)
                }
                return
            }
        }
        fail("iteration did not terminate")
    }
    @Test fun localeTypesWidthsAndContextualRules() {
        val tails = listOf("i", "isla", "I", "hi", "hilo", "hielo", "HIA", "o", "HO", "8", "11", "110", "İ", "א", "ב", "😀", "", "\n", "hi\n", "hi\r\n", "i\n", "o\n", "\nA", "a\u2028")
        for (locale in listOf("root", "en", "fr", "ar", "ja", "es", "es_MX", "he", "iw", "en_US_POSIX")) {
            for (type in ListFormatter.Type.entries) for (width in ListFormatter.Width.entries) {
                val j = Original.getInstance(OriginalLocale(locale), Original.Type.valueOf(type.name), Original.Width.valueOf(width.name))
                val k = ListFormatter.getInstance(ULocale(locale), type, width)
                assertEquals(j.locale.toString(), k.getLocale().toString())
                for (count in 0..6) compare(j, k, List(count) { if (it % 2 == 0) "😀" else "" })
                for (tail in tails) { compare(j, k, listOf("a", tail)); compare(j, k, listOf("a", "b", tail)) }
                for (count in 1..6) assertEquals(j.getPatternForNumItems(count), k.getPatternForNumItems(count))
            }
        }
    }
    @Test fun suppliedPatternsAndCallbackOrder() {
        for (pattern in listOf("{0}/{1}", "{1}/{0}", "{1}", "{1}:{1}", "{0}:{1}:{0}", "'{0}' {1} {0}", "{0}  {1}")) {
            val j = Original(pattern, pattern, pattern, pattern); val k = ListFormatter(pattern, pattern, pattern, pattern)
            for (count in 0..7) compare(j, k, List(count) { "item$it" })
        }
        val jTrace = mutableListOf<Int>(); val kTrace = mutableListOf<Int>()
        fun item(index: Int, trace: MutableList<Int>) = object { override fun toString(): String { trace.add(index); return "item$index" } }
        val j = Original.getInstance(OriginalLocale.ENGLISH); val k = ListFormatter.getInstance(ULocale.ENGLISH)
        for (count in 0..6) {
            jTrace.clear(); kTrace.clear()
            assertEquals(j.format(List(count) { item(it, jTrace) }), k.format(List(count) { item(it, kTrace) }))
            assertEquals(jTrace, kTrace)
        }
        val value = k.formatToValue("a", "b")
        val failure = IOException("append failure")
        val appendable = object : Appendable {
            override fun append(value: CharSequence?): Appendable = throw failure
            override fun append(value: CharSequence?, start: Int, end: Int): Appendable = throw failure
            override fun append(value: Char): Appendable = throw failure
        }
        assertSame(failure, assertFailsWith<ICUUncheckedIOException> { value.appendTo(appendable) }.cause)
    }
}
