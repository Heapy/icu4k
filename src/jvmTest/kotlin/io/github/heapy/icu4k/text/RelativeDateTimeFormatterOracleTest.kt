// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.RelativeDateTimeFormatter as Source
import com.ibm.icu.text.ConstrainedFieldPosition as SourcePosition
import com.ibm.icu.text.DisplayContext as SourceContext
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class RelativeDateTimeFormatterOracleTest {
    private val locales = listOf("en", "en_GB", "fr", "fr_CA", "de", "ru", "uk", "pl", "cs", "sr", "sr_Latn",
        "ar", "he", "ja", "zh", "zh_Hant", "ko", "fi", "tr", "es", "pt_PT", "sw", "th@calendar=buddhist", "fa", "root")
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.cause!! else e
        listOf(cause.javaClass.simpleName, if (cause is NullPointerException || cause is IndexOutOfBoundsException || cause is AssertionError) null else cause.message)
    }
    private fun key(value: Any?): String? = value?.toString()?.substringAfterLast('(')
    private fun sourceStyle(style: RelativeDateTimeFormatter.Style) = Source.Style.valueOf(style.name)
    private fun sourceUnit(unit: RelativeDateTimeFormatter.RelativeDateTimeUnit) = Source.RelativeDateTimeUnit.valueOf(unit.name)
    private fun sourceDirection(direction: RelativeDateTimeFormatter.Direction) = Source.Direction.valueOf(direction.name)
    private fun pair(locale: String, style: RelativeDateTimeFormatter.Style): Pair<Source, RelativeDateTimeFormatter> =
        Source.getInstance(SourceLocale(locale), null, sourceStyle(style), SourceContext.CAPITALIZATION_NONE) to
            RelativeDateTimeFormatter.getInstance(ULocale(locale), null, style, DisplayContext.CAPITALIZATION_NONE)

    @Test fun numericAndQualitativeFormsAcrossLocalesStylesAndUnits() {
        val offsets = listOf(Double.NEGATIVE_INFINITY, -1000.125, -5.0, -2.1, -2.005, -2.0, -1.995, -1.99,
            -1.005, -1.0, -0.995, -0.005, -0.0049, -0.0, 0.0, 0.0049, 0.005, 0.5, 0.995, 1.0, 1.005,
            1.99, 1.995, 2.0, 2.005, 2.1, 3.5, 12345.6789, Double.POSITIVE_INFINITY, Double.NaN)
        for (locale in locales) for (style in RelativeDateTimeFormatter.Style.entries) {
            val (source, port) = pair(locale, style)
            for (unit in RelativeDateTimeFormatter.RelativeDateTimeUnit.entries) for (offset in offsets) {
                val label = "$locale/$style/$unit/$offset"
                assertEquals(outcome { source.formatNumeric(offset, sourceUnit(unit)) }, outcome { port.formatNumeric(offset, unit) }, "numeric $label")
                assertEquals(outcome { source.format(offset, sourceUnit(unit)) }, outcome { port.format(offset, unit) }, "relative $label")
            }
            for (unit in RelativeDateTimeFormatter.RelativeUnit.entries) for (direction in RelativeDateTimeFormatter.Direction.entries) {
                assertEquals(outcome { source.format(1.25, sourceDirection(direction), Source.RelativeUnit.valueOf(unit.name)) },
                    outcome { port.format(1.25, direction, unit) }, "$locale/$style/$unit/$direction")
            }
            for (unit in RelativeDateTimeFormatter.AbsoluteUnit.entries) for (direction in RelativeDateTimeFormatter.Direction.entries) {
                assertEquals(outcome { source.format(sourceDirection(direction), Source.AbsoluteUnit.valueOf(unit.name)) },
                    outcome { port.format(direction, unit) }, "$locale/$style/$unit/$direction")
            }
            for ((date, time) in listOf("tomorrow" to "15:30", "" to "", "{1} 'date'" to "{0} time", null to "noon", "today" to null))
                assertEquals(outcome { source.combineDateAndTime(date, time) }, outcome { port.combineDateAndTime(date, time) }, "combine $locale/$style")
        }
    }

    private fun compare(source: Source.FormattedRelativeDateTime?, port: RelativeDateTimeFormatter.FormattedRelativeDateTime?, label: String) {
        assertEquals(source?.toString(), port?.toString(), label)
        if (source == null || port == null) { assertNull(source); assertNull(port); return }
        assertEquals(source.length, port.length, label)
        for (index in -1..source.length) assertEquals(outcome { source[index] }, outcome { port[index] }, "$label char $index")
        for ((start, end) in listOf(0 to 0, 0 to source.length, 1 to source.length, source.length to source.length, -1 to 0, 1 to 0, 0 to source.length + 1))
            assertEquals(outcome { source.subSequence(start, end).toString() }, outcome { port.subSequence(start, end).toString() }, "$label slice $start $end")
        val sb = StringBuilder("prefix"); val pb = StringBuilder("prefix")
        assertSame(sb, source.appendTo(sb)); assertSame(pb, port.appendTo(pb)); assertEquals(sb.toString(), pb.toString())
        for (constraint in listOf("all", "literal", "numeric", "integer", "numberClass", "relativeClass")) {
            val sp = SourcePosition(); val pp = ConstrainedFieldPosition()
            when (constraint) {
                "literal" -> { sp.constrainField(Source.Field.LITERAL); pp.constrainField(RelativeDateTimeFormatter.Field.LITERAL) }
                "numeric" -> { sp.constrainField(Source.Field.NUMERIC); pp.constrainField(RelativeDateTimeFormatter.Field.NUMERIC) }
                "integer" -> { sp.constrainField(com.ibm.icu.text.NumberFormat.Field.INTEGER); pp.constrainField(NumberFormatField.INTEGER) }
                "numberClass" -> { sp.constrainClass(com.ibm.icu.text.NumberFormat.Field::class.java); pp.constrainClass(NumberFormatField::class) }
                "relativeClass" -> { sp.constrainClass(Source.Field::class.java); pp.constrainClass(RelativeDateTimeFormatter.Field::class) }
            }
            var count = 0
            while (true) {
                check(count++ < 100)
                val found = source.nextPosition(sp)
                assertEquals(found, port.nextPosition(pp), "$label $constraint")
                assertEquals(listOf(key(sp.field), key(sp.fieldValue), sp.start, sp.limit),
                    listOf(key(pp.getField()), key(pp.getFieldValue()), pp.getStart(), pp.getLimit()), "$label $constraint")
                if (!found) break
            }
        }
        val si = source.toCharacterIterator(); val pi = port.toCharacterIterator()
        for (i in 0..port.length) {
            assertEquals(si.setIndex(i), pi.setIndex(i), label)
            assertEquals(si.attributes.entries.associate { key(it.key) to key(it.value) }, pi.attributes.entries.associate { key(it.key) to key(it.value) }, label)
            assertEquals(si.runStart, pi.runStart, label); assertEquals(si.runLimit, pi.runLimit, label)
        }
    }

    @Test fun formattedValueFieldsConstraintsIteratorsAndViews() {
        for (locale in listOf("en", "fr", "ar", "sr", "ja", "ru")) for (style in RelativeDateTimeFormatter.Style.entries) {
            val (source, port) = pair(locale, style)
            for (offset in listOf(-12345.67, -0.0, 0.0, 1.25, Double.NaN)) {
                compare(source.formatNumericToValue(offset, Source.RelativeDateTimeUnit.DAY),
                    port.formatNumericToValue(offset, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY), "$locale/$style/$offset")
                compare(source.formatToValue(offset, Source.RelativeDateTimeUnit.DAY),
                    port.formatToValue(offset, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY), "relative $locale/$style/$offset")
            }
            for (direction in RelativeDateTimeFormatter.Direction.entries) {
                compare(source.formatToValue(sourceDirection(direction), Source.AbsoluteUnit.DAY),
                    port.formatToValue(direction, RelativeDateTimeFormatter.AbsoluteUnit.DAY), "absolute $locale/$style/$direction")
            }
            compare(source.formatToValue(1.5, Source.Direction.NEXT, Source.RelativeUnit.MONTHS),
                port.formatToValue(1.5, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.MONTHS), "quantity $locale/$style")
        }
        val (source, port) = pair("en", RelativeDateTimeFormatter.Style.LONG)
        val failure = IOException("append failure")
        val appendable = object : Appendable {
            override fun append(csq: CharSequence?): Appendable = throw failure
            override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = throw failure
            override fun append(c: Char): Appendable = throw failure
        }
        assertSame(failure, assertFailsWith<com.ibm.icu.util.ICUUncheckedIOException> { source.formatToValue(1.0, Source.RelativeDateTimeUnit.DAY).appendTo(appendable) }.cause)
        assertSame(failure, assertFailsWith<io.github.heapy.icu4k.util.ICUUncheckedIOException> { port.formatToValue(1.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY).appendTo(appendable) }.cause)
    }

    @Test fun customNumberFormatsRoundedPluralsAndCloneIsolation() {
        for (locale in listOf("en", "fr", "sr", "ar", "ru")) for (pattern in listOf("0", "0.00", "#,##0.0%", "0.00E0")) {
            val snf = com.ibm.icu.text.DecimalFormat(pattern, com.ibm.icu.text.DecimalFormatSymbols(SourceLocale(locale)))
            val pnf = DecimalFormat(pattern, DecimalFormatSymbols(ULocale(locale)))
            val source = Source.getInstance(SourceLocale(locale), snf)
            val port = RelativeDateTimeFormatter.getInstance(ULocale(locale), pnf)
            snf.applyPattern("0.0000"); pnf.applyPattern("0.0000")
            for (value in listOf(0.0, 0.1, 1.0, 1.01, 1.5, 2.0, 3.14, 11.0, 21.0, 1000.0)) {
                assertEquals(source.format(value, Source.Direction.NEXT, Source.RelativeUnit.DAYS),
                    port.format(value, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.DAYS), "$locale/$pattern/$value")
                compare(source.formatToValue(value, Source.Direction.LAST, Source.RelativeUnit.MONTHS),
                    port.formatToValue(value, RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.MONTHS), "$locale/$pattern/$value")
            }
            source.numberFormat.minimumFractionDigits = 5; port.getNumberFormat().setMinimumFractionDigits(5)
            assertEquals(source.numberFormat.format(1.25), port.getNumberFormat().format(1.25))
            assertNotSame(port.getNumberFormat(), port.getNumberFormat())
        }
        val source = Source.getInstance(SourceLocale.US, com.ibm.icu.text.RuleBasedNumberFormat(SourceLocale.US, com.ibm.icu.text.RuleBasedNumberFormat.SPELLOUT))
        val port = RelativeDateTimeFormatter.getInstance(ULocale.US, RuleBasedNumberFormat(ULocale.US, RuleBasedNumberFormat.SPELLOUT))
        compare(source.formatToValue(12.0, Source.Direction.NEXT, Source.RelativeUnit.DAYS),
            port.formatToValue(12.0, RelativeDateTimeFormatter.Direction.NEXT, RelativeDateTimeFormatter.RelativeUnit.DAYS), "rbnf")
    }

    @Test fun capitalizationAndValueRejectionOrder() {
        for (locale in listOf("en", "fr", "ru", "fi", "nl", "tr")) for (context in DisplayContext.entries.filter { it.type() == DisplayContext.Type.CAPITALIZATION }) {
            val source = Source.getInstance(SourceLocale(locale), null, Source.Style.LONG, SourceContext.valueOf(context.name))
            val port = RelativeDateTimeFormatter.getInstance(ULocale(locale), null, RelativeDateTimeFormatter.Style.LONG, context)
            assertEquals(source.capitalizationContext.name, port.getCapitalizationContext().name)
            assertEquals(source.formatStyle.name, port.getFormatStyle()!!.name)
            for (offset in listOf(-2.0, -1.0, 0.0, 1.0, 2.5)) {
                assertEquals(source.format(offset, Source.RelativeDateTimeUnit.DAY), port.format(offset, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY))
                assertEquals(source.formatNumeric(offset, Source.RelativeDateTimeUnit.DAY), port.formatNumeric(offset, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY))
                assertEquals(outcome { source.formatToValue(offset, Source.RelativeDateTimeUnit.DAY)?.toString() },
                    outcome { port.formatToValue(offset, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY).toString() })
            }
            assertEquals(outcome { source.formatToValue(1.0, null, null) }, outcome { port.formatToValue(1.0, null, null) })
            assertEquals(outcome { source.formatToValue(null as Source.Direction?, null as Source.AbsoluteUnit?) },
                outcome { port.formatToValue(null as RelativeDateTimeFormatter.Direction?, null as RelativeDateTimeFormatter.AbsoluteUnit?) })
        }
    }

    @Test fun nullableFactoriesInvalidContextsAndUnits() {
        assertEquals(outcome { Source.getInstance(null as SourceLocale?) }, outcome { RelativeDateTimeFormatter.getInstance(null as ULocale?) })
        for (context in SourceContext.values().toList() + null) {
            assertEquals(outcome { Source.getInstance(SourceLocale.US, null, Source.Style.LONG, context); "ok" },
                outcome { RelativeDateTimeFormatter.getInstance(ULocale.US, null, RelativeDateTimeFormatter.Style.LONG, context?.let { DisplayContext.valueOf(it.name) }); "ok" })
        }
        for (style in listOf(Source.Style.LONG, null)) {
            val source = Source.getInstance(SourceLocale.US, null, style, SourceContext.CAPITALIZATION_NONE)
            val port = RelativeDateTimeFormatter.getInstance(ULocale.US, null, style?.let { RelativeDateTimeFormatter.Style.valueOf(it.name) }, DisplayContext.CAPITALIZATION_NONE)
            assertEquals(source.formatStyle?.name, port.getFormatStyle()?.name)
            assertEquals(outcome { source.formatNumeric(-0.0, null) }, outcome { port.formatNumeric(-0.0, null) })
            assertEquals(outcome { source.format(1.0, null as Source.RelativeDateTimeUnit?) }, outcome { port.format(1.0, null as RelativeDateTimeFormatter.RelativeDateTimeUnit?) })
            assertEquals(outcome { source.format(1.0, Source.Direction.NEXT, null) }, outcome { port.format(1.0, RelativeDateTimeFormatter.Direction.NEXT, null) })
            for (direction in listOf(null, Source.Direction.PLAIN, Source.Direction.NEXT))
                assertEquals(outcome { source.format(direction, null as Source.AbsoluteUnit?) },
                    outcome { port.format(direction?.let { RelativeDateTimeFormatter.Direction.valueOf(it.name) }, null as RelativeDateTimeFormatter.AbsoluteUnit?) })
        }
        assertEquals(Source.getInstance(java.util.Locale.US).format(1.0, Source.RelativeDateTimeUnit.DAY), RelativeDateTimeFormatter.getInstance(java.util.Locale.US).format(1.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY))
    }
    @Test fun everyAvailableLocaleLoadsAndFormatsWithStyleFallbacks() {
        for (locale in SourceLocale.getAvailableLocales()) for (style in RelativeDateTimeFormatter.Style.entries) {
            val (source, port) = pair(locale.toString(), style)
            assertEquals(source.formatNumeric(1.5, Source.RelativeDateTimeUnit.DAY),
                port.formatNumeric(1.5, RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY), "$locale/$style/day")
            assertEquals(source.formatNumeric(2.0, Source.RelativeDateTimeUnit.QUARTER),
                port.formatNumeric(2.0, RelativeDateTimeFormatter.RelativeDateTimeUnit.QUARTER), "$locale/$style/quarter")
            assertEquals(source.format(Source.Direction.PLAIN, Source.AbsoluteUnit.FRIDAY),
                port.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.FRIDAY), "$locale/$style/weekday")
            assertEquals(source.combineDateAndTime("date", "time"), port.combineDateAndTime("date", "time"), "$locale/$style/combine")
        }
    }

}
