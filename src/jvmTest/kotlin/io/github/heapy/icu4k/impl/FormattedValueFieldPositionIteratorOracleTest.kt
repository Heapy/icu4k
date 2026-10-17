@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.FormattedValueFieldPositionIteratorImpl as Source
import com.ibm.icu.text.ConstrainedFieldPosition as SourcePosition
import com.ibm.icu.text.DateFormat as SourceDateFormat
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import java.text.FieldPosition
import kotlin.random.Random
import kotlin.test.*

class FormattedValueFieldPositionIteratorOracleTest {
    private class Span : FormatSpanField("oracle-span")
    private val span = Span()
    private fun copy(items: List<FieldPosition>) = items.map { f -> FieldPosition(f.fieldAttribute, f.field).also { it.beginIndex = f.beginIndex; it.endIndex = f.endIndex } }.toMutableList()
    private fun sourceRows(items: List<FieldPosition>, position: SourcePosition = SourcePosition()): List<List<Any?>> = buildList {
        while (Source.nextPosition(items, position)) add(listOf(position.field, position.fieldValue, position.start, position.limit))
    }
    private fun portRows(items: List<FieldPosition>, position: ConstrainedFieldPosition = ConstrainedFieldPosition()): List<List<Any?>> = buildList {
        while (FormattedValueFieldPositionIteratorImpl.nextPosition(items, position)) add(listOf(position.getField(), position.getFieldValue(), position.getStart(), position.getLimit()))
    }
    @Test fun shuffledDuplicateFieldsAndSpansMatchSourceIncludingCharacterRuns() {
        val random = Random(793)
        val fields = listOf(SourceDateFormat.Field.YEAR, SourceDateFormat.Field.MONTH, SourceDateFormat.Field.DAY_OF_MONTH, SourceDateFormat.Field.HOUR_OF_DAY0)
        repeat(120) { round ->
            val original = (0 until random.nextInt(1, 25)).map { i -> FieldPosition(fields.random(random)).also {
                it.beginIndex = i / 2 * 3; it.endIndex = i / 2 * 3 + random.nextInt(1, 4)
            } }.shuffled(random)
            val a = copy(original); val b = copy(original)
            Source.addOverlapSpans(a, span, round % 3)
            FormattedValueFieldPositionIteratorImpl.addOverlapSpans(b, span, round % 3)
            assertEquals(sourceRows(a), portRows(b), "$round/insertion")
            Source.sort(a); FormattedValueFieldPositionIteratorImpl.sort(b)
            assertEquals(sourceRows(a), portRows(b), "$round/sorted")
            val sa = SourcePosition().also { it.constrainFieldAndValue(span, 1) }
            val sb = ConstrainedFieldPosition().also { it.constrainFieldAndValue(span, 1) }
            assertEquals(sourceRows(a, sa), portRows(b, sb))
            assertEquals(sa.int64IterationContext, sb.getInt64IterationContext())
            val text = "x".repeat(original.size * 3)
            val ai = Source.toCharacterIterator(text, a)
            val bi = FormattedValueFieldPositionIteratorImpl.toCharacterIterator(text, b)
            for (i in text.indices) {
                ai.setIndex(i); bi.setIndex(i)
                assertEquals(ai.attributes, bi.getAttributes(), "$round/$i")
                assertEquals(ai.runStart, bi.getRunStart()); assertEquals(ai.runLimit, bi.getRunLimit())
            }
        }
    }
    @Test fun simpleDateFormatCollectedFieldsBypassPublicCalendarFormatOverride() {
        val a = object : com.ibm.icu.text.SimpleDateFormat("yyyy MMM d HH:mm:ss", com.ibm.icu.util.ULocale.US) {
            override fun format(cal: com.ibm.icu.util.Calendar?, output: StringBuffer?, pos: FieldPosition?): StringBuffer = error("public override")
        }
        val b = object : SimpleDateFormat("yyyy MMM d HH:mm:ss", ULocale.US) {
            override fun format(calendar: Calendar?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = error("public override")
        }
        val method = com.ibm.icu.text.SimpleDateFormat::class.java.getDeclaredMethod("format", com.ibm.icu.util.Calendar::class.java, StringBuffer::class.java, FieldPosition::class.java, List::class.java).also { it.isAccessible = true }
        val ac = com.ibm.icu.util.Calendar.getInstance(com.ibm.icu.util.TimeZone.getTimeZone("UTC"), com.ibm.icu.util.ULocale("en@calendar=hebrew"))
        val bc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), ULocale("en@calendar=hebrew"))
        ac.timeInMillis = 1710679800123L; bc.setTimeInMillis(1710679800123L)
        val ap = mutableListOf<FieldPosition>(); val bp = mutableListOf<FormatFieldPosition>()
        val ao = StringBuffer("prefix:"); val bo = StringBuilder("prefix:")
        val af = FieldPosition(1); val bf = FormatFieldPosition(1)
        method.invoke(a, ac, ao, af, ap)
        b.format(bc, bo, bf, bp)
        assertEquals(ao.toString(), bo.toString())
        assertEquals(af.beginIndex, bf.getBeginIndex()); assertEquals(af.endIndex, bf.getEndIndex())
        fun fieldName(field: Any?) = field.toString().substringAfterLast('(')
        assertEquals(ap.map { listOf(fieldName(it.fieldAttribute), it.beginIndex, it.endIndex) }, bp.map { listOf(fieldName(it.getFieldAttribute()), it.getBeginIndex(), it.getEndIndex()) })
        assertEquals(a.timeZone.id, b.getTimeZone()!!.getID())
    }
}
