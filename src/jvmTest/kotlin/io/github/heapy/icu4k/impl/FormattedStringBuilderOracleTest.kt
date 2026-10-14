@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import java.io.*
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.FormattedStringBuilder as Original
import com.ibm.icu.text.NumberFormat.Field as OriginalField

class FormattedStringBuilderOracleTest {
    private val jf = listOf(null, OriginalField.INTEGER, OriginalField.FRACTION, OriginalField.CURRENCY, OriginalField.APPROXIMATELY_SIGN)
    private val kf = listOf(null, NumberFormatField.INTEGER, NumberFormatField.FRACTION, NumberFormatField.CURRENCY, NumberFormatField.APPROXIMATELY_SIGN)
    private fun compare(j: Original, k: FormattedStringBuilder) {
        assertEquals(j.toString(), k.toString())
        assertEquals(j.length, k.length)
        assertEquals(j.codePointCount(), k.codePointCount())
        assertEquals(j.firstCodePoint, k.getFirstCodePoint())
        assertEquals(j.lastCodePoint, k.getLastCodePoint())
        assertEquals(j.toDebugString(), k.toDebugString())
        assertEquals(j.toFieldArray().map { jf.indexOf(it) }, k.toFieldArray().map { kf.indexOf(it) })
        assertContentEquals(j.toCharArray(), k.toCharArray())
        for (index in 0 until j.length) assertEquals(j.codePointAt(index), k.codePointAt(index))
        for (index in 1..j.length) assertEquals(j.codePointBefore(index), k.codePointBefore(index))
    }
    @Test fun randomizedCenteredStorageEditsMatchSource() {
        val random = Random(7563)
        for (capacity in listOf(0, 1, 2, 7, 40)) {
            val j = Original(capacity)
            val k = FormattedStringBuilder(capacity)
            repeat(500) {
                val field = random.nextInt(jf.size)
                val index = random.nextInt(j.length + 1)
                val text = listOf("", "x", "ab", "😀", "\ud800", "\udc00", "日本語")[random.nextInt(7)]
                when (random.nextInt(6)) {
                    0 -> assertEquals(j.insert(index, text, jf[field]), k.insert(index, text, kf[field]))
                    1 -> {
                        val end = random.nextInt(index, j.length + 1)
                        assertEquals(j.splice(index, end, text, 0, text.length, jf[field]), k.splice(index, end, text, 0, text.length, kf[field]))
                    }
                    2 -> { val cp = listOf(0, 65, 0xd800, 0x1f600, 0x10ffff)[random.nextInt(5)]; assertEquals(j.insertCodePoint(index, cp, jf[field]), k.insertCodePoint(index, cp, kf[field])) }
                    3 -> { val jc = Original(j); val kc = FormattedStringBuilder(k); assertEquals(jc.contentEquals(j), kc.contentEquals(k)); j.copyFrom(jc); k.copyFrom(kc) }
                    4 -> if (j.length > 0) {
                        val end = random.nextInt(index, j.length + 1)
                        val js = j.subSequence(index, end) as Original
                        val ks = k.subSequence(index, end) as FormattedStringBuilder
                        compare(js, ks)
                    }
                    else -> { j.clear(); k.clear() }
                }
                compare(j, k)
            }
        }
    }
    @Test fun prefixSuffixAndAppendIndexesMatchSource() {
        for (pattern in listOf("", "literal", "{0}", "pre{0}", "{0}end", "pre{0}end", "😀{0}日本", "x".repeat(65280)))
            for (start in 0..2) for (end in start..3) {
                val compiled = com.ibm.icu.impl.SimpleFormatterImpl.compileToStringMinMaxArguments(pattern, StringBuilder(), 0, 1)
                val j = Original().apply { append("argtail", OriginalField.INTEGER) }
                val k = FormattedStringBuilder().apply { append("argtail", NumberFormatField.INTEGER) }
                assertEquals(com.ibm.icu.impl.SimpleFormatterImpl.formatPrefixSuffix(compiled, OriginalField.CURRENCY, start, end, j), SimpleFormatterImpl.formatPrefixSuffix(compiled, NumberFormatField.CURRENCY, start, end, k))
                compare(j, k)
            }
        for (index in 0..4) {
            val j = Original().apply { append("seed", OriginalField.INTEGER); setAppendIndex(index); setAppendableField(OriginalField.CURRENCY) }
            val k = FormattedStringBuilder().apply { append("seed", NumberFormatField.INTEGER); setAppendIndex(index); setAppendableField(NumberFormatField.CURRENCY) }
            (j as Appendable).append("value", 1, 4).append('!')
            (k as Appendable).append("value", 1, 4).append('!')
            compare(j, k)
        }
    }
    @Test fun arrayInsertionCharSequenceAliasingAndFailureStateMatch() {
        for (index in 0..4) {
            val j = Original(8).apply { append("seed", OriginalField.INTEGER) }
            val k = FormattedStringBuilder(8).apply { append("seed", NumberFormatField.INTEGER) }
            val chars = "😀x".toCharArray()
            val jfields = arrayOf<Any?>(OriginalField.CURRENCY, null, OriginalField.FRACTION)
            val kfields = arrayOf<Any?>(NumberFormatField.CURRENCY, null, NumberFormatField.FRACTION)
            assertEquals(j.insert(index, chars, jfields), k.insert(index, chars, kfields))
            compare(j, k)
            assertEquals(j.insert(0, j as CharSequence, OriginalField.FRACTION), k.insert(0, k as CharSequence, NumberFormatField.FRACTION))
            compare(j, k)
        }
        for (badPoint in listOf(-1, Int.MIN_VALUE, 0x110000, Int.MAX_VALUE)) {
            val j = Original().apply { append("seed", OriginalField.INTEGER) }
            val k = FormattedStringBuilder().apply { append("seed", NumberFormatField.INTEGER) }
            val expected = assertFailsWith<IllegalArgumentException> { j.insertCodePoint(-1, badPoint, null) }
            val actual = assertFailsWith<IllegalArgumentException> { k.insertCodePoint(-1, badPoint, null) }
            assertEquals(expected.message, actual.message)
            compare(j, k)
        }
        val sourceWrap = Original.FieldWrapper { OriginalField.INTEGER }
        val commonWrap = FormattedStringBuilder.FieldWrapper { NumberFormatField.INTEGER }
        val j = Original().apply { append("a", sourceWrap) }
        val k = FormattedStringBuilder().apply { append("a", commonWrap) }
        assertEquals(j.contentEquals(charArrayOf('a'), arrayOf<Any?>(OriginalField.INTEGER)), k.contentEquals(charArrayOf('a'), arrayOf<Any?>(NumberFormatField.INTEGER)))
        val expected = assertFailsWith<AssertionError> { Original.unwrapField("bad") }
        val actual = assertFailsWith<AssertionError> { FormattedStringBuilder.unwrapField("bad") }
        assertEquals(expected.message, actual.message)
    }
    @Test fun numericFieldSerializationRetainsSourceCanonicalization() {
        fun roundtrip(value: Any): Any {
            val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { output -> output.writeObject(value) } }.toByteArray()
            return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        }
        for ((source, common) in listOf(OriginalField.INTEGER to NumberFormatField.INTEGER, OriginalField.CURRENCY to NumberFormatField.CURRENCY, OriginalField.COMPACT to NumberFormatField.COMPACT)) {
            assertSame(source, roundtrip(source))
            assertSame(common, roundtrip(common))
        }
        assertFailsWith<InvalidObjectException> { roundtrip(OriginalField.APPROXIMATELY_SIGN) }
        assertFailsWith<InvalidObjectException> { roundtrip(NumberFormatField.APPROXIMATELY_SIGN) }
    }
}
