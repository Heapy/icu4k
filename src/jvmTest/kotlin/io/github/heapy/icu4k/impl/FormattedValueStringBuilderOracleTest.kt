@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*
import java.text.AttributedCharacterIterator
import java.text.FieldPosition
import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.FormattedStringBuilder as OriginalBuilder
import com.ibm.icu.impl.FormattedValueStringBuilderImpl as Original
import com.ibm.icu.text.ConstrainedFieldPosition as OriginalPosition
import com.ibm.icu.text.NumberFormat.Field as NF
import com.ibm.icu.text.ListFormatter.Field as LF

class FormattedValueStringBuilderOracleTest {
    private class TotalField : FormatField("total")
    private val total = TotalField()
    private val jf = listOf(null, NF.INTEGER, NF.GROUPING_SEPARATOR, NF.DECIMAL_SEPARATOR, NF.FRACTION, NF.SIGN, NF.CURRENCY, LF.ELEMENT, LF.LITERAL, com.ibm.icu.text.ListFormatter.SpanField.LIST_SPAN, total)
    private val kf = listOf(null, NumberFormatField.INTEGER, NumberFormatField.GROUPING_SEPARATOR, NumberFormatField.DECIMAL_SEPARATOR, NumberFormatField.FRACTION, NumberFormatField.SIGN, NumberFormatField.CURRENCY, ListFormatField.ELEMENT, ListFormatField.LITERAL, ListSpanField.LIST_SPAN, total)
    private fun positions(j: OriginalBuilder, k: FormattedStringBuilder, mode: Int, numeric: Boolean) {
        val jp = OriginalPosition()
        val kp = ConstrainedFieldPosition()
        when (mode) {
            1 -> { jp.constrainField(NF.INTEGER); kp.constrainField(NumberFormatField.INTEGER) }
            2 -> { jp.constrainField(LF.ELEMENT); kp.constrainField(ListFormatField.ELEMENT) }
            3 -> { jp.constrainClass(NF::class.java); kp.constrainClass(NumberFormatField::class) }
            4 -> { jp.constrainFieldAndValue(com.ibm.icu.text.ListFormatter.SpanField.LIST_SPAN, 1); kp.constrainFieldAndValue(ListSpanField.LIST_SPAN, 1) }
            5 -> { jp.constrainClass(com.ibm.icu.text.UFormat.SpanField::class.java); kp.constrainClass(FormatSpanField::class) }
            6 -> { jp.constrainField(total); kp.constrainField(total) }
        }
        var count = 0
        while (true) {
            assertTrue(count++ < 500, "iterator did not terminate")
            val expected = Original.nextPosition(j, jp, if (numeric) total else null)
            val actual = FormattedValueStringBuilderImpl.nextPosition(k, kp, if (numeric) total else null)
            assertEquals(expected, actual)
            assertEquals(listOf(jf.indexOf(jp.field), jp.fieldValue, jp.start, jp.limit), listOf(kf.indexOf(kp.getField()), kp.getFieldValue(), kp.getStart(), kp.getLimit()))
            if (!expected) break
        }
    }
    private fun attributes(iterator: AttributedCharacterIterator, fields: List<FormatField?>): List<Any> {
        val result = mutableListOf<Any>()
        iterator.first()
        while (iterator.index < iterator.endIndex) {
            val data = iterator.attributes.entries.map { (key, value) -> fields.indexOf(key) to (if (value is FormatField) fields.indexOf(value) else value) }.sortedBy { it.first }
            result.add(listOf(iterator.index, iterator.current(), data, iterator.runStart, iterator.runLimit))
            iterator.next()
        }
        return result
    }
    @Test fun numericListSpanConstraintsAndExportsMatchGeneratedBuffers() {
        val random = Random(519)
        repeat(80) { sample ->
            val j = OriginalBuilder()
            val k = FormattedStringBuilder()
            repeat(12) {
                val field = random.nextInt(9)
                val text = listOf("", "1", "ab", ",", " ", "\t", "\u200e", "\ufe0f", "😀")[random.nextInt(9)]
                j.append(text, jf[field]); k.append(text, kf[field])
            }
            if (sample % 2 == 0) {
                val start = j.length
                j.append(" list element ", LF.ELEMENT)
                k.append(" list element ", ListFormatField.ELEMENT)
                Original.applySpanRange(j, com.ibm.icu.text.ListFormatter.SpanField.LIST_SPAN, sample % 3, start, j.length)
                FormattedValueStringBuilderImpl.applySpanRange(k, ListSpanField.LIST_SPAN, sample % 3, start, k.length)
                assertEquals(Original.findSpan(j, sample % 3), FormattedValueStringBuilderImpl.findSpan(k, sample % 3))
            }
            for (numeric in listOf(false, true)) {
                for (mode in 0..6) positions(j, k, mode, numeric)
                assertEquals(attributes(Original.toCharacterIterator(j, if (numeric) total else null), jf), attributes(FormattedValueStringBuilderImpl.toCharacterIterator(k, if (numeric) total else null), kf))
            }
            for (field in 0..8) {
                val jp = if (field == 0) FieldPosition(0) else if (field == 4) FieldPosition(1) else FieldPosition(jf[field])
                val kp = if (field == 0) FormatFieldPosition(0) else if (field == 4) FormatFieldPosition(1) else FormatFieldPosition(kf[field])
                if (field in 7..8) {
                    assertFailsWith<IllegalArgumentException> { Original.nextFieldPosition(j, jp) }
                    assertFailsWith<IllegalArgumentException> { FormattedValueStringBuilderImpl.nextFieldPosition(k, kp) }
                    continue
                }
                repeat(8) {
                    assertEquals(Original.nextFieldPosition(j, jp), FormattedValueStringBuilderImpl.nextFieldPosition(k, kp))
                    assertEquals(listOf(jp.beginIndex, jp.endIndex), listOf(kp.getBeginIndex(), kp.getEndIndex()))
                }
            }
        }
    }
}
