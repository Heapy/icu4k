@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.number.NumberFormatter as JFormatter
import com.ibm.icu.number.FormattedNumber as JResult
import com.ibm.icu.number.Notation as JNotation
import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.text.ConstrainedFieldPosition as JPosition
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.Currency as JCurrency
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal
import kotlin.test.*

class FormattedNumberOracleTest {
    private fun key(field: Any?): String? = field?.toString()?.substringAfterLast('(')
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        val cause = if (e is InvocationTargetException) e.cause!! else e
        when (cause) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> "${cause.javaClass.simpleName}:${cause.message}" }
    }
    private fun compare(j: JResult, k: FormattedNumber, label: String) {
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.length, k.length, label)
        for (index in -1..j.length) assertEquals(outcome { j[index] }, outcome { k[index] }, "$label/char/$index")
        for ((start, end) in listOf(0 to 0, 0 to j.length, 1 to j.length, j.length to j.length, -1 to 0, 1 to 0, 0 to j.length + 1)) {
            assertEquals(outcome { j.subSequence(start, end).toString() }, outcome { k.subSequence(start, end).toString() }, "$label/slice/$start/$end")
            if (start in 0..end && end <= j.length) assertIs<String>(k.subSequence(start, end))
        }
        val ja = StringBuilder("prefix"); val ka = StringBuilder("prefix")
        assertSame(ja, j.appendTo(ja)); assertSame(ka, k.appendTo(ka)); assertEquals(ja.toString(), ka.toString())
        assertEquals(outcome { j.toBigDecimal() }, outcome { k.toBigDecimal() }, "$label/decimal")
        assertEquals(j.outputUnit?.identifier, k.getOutputUnit()?.getIdentifier(), label)
        assertEquals(j.nounClass.name, k.getNounClass().name, label)
        assertEquals(j.gender, k.getGender(), label)
        for (operand in PluralRules.Operand.entries) {
            assertEquals(j.fixedDecimal.getPluralOperand(JRules.Operand.valueOf(operand.name)), k.getFixedDecimal()!!.getPluralOperand(operand), "$label/$operand")
        }
        for (constraint in listOf("all", "integer", "currency", "number")) {
            val jp = JPosition(); val kp = ConstrainedFieldPosition()
            when (constraint) {
                "integer" -> { jp.constrainField(JField.INTEGER); kp.constrainField(NumberFormatField.INTEGER) }
                "currency" -> { jp.constrainField(JField.CURRENCY); kp.constrainField(NumberFormatField.CURRENCY) }
                "number" -> { jp.constrainClass(JField::class.java); kp.constrainClass(NumberFormatField::class) }
            }
            var count = 0
            while (true) {
                check(count++ < 100)
                val found = j.nextPosition(jp)
                assertEquals(found, k.nextPosition(kp), "$label/$constraint")
                assertEquals(listOf(key(jp.field), key(jp.fieldValue), jp.start, jp.limit), listOf(key(kp.getField()), key(kp.getFieldValue()), kp.getStart(), kp.getLimit()), label)
                if (!found) break
            }
        }
        val ji = j.toCharacterIterator(); val ki = k.toCharacterIterator()
        for (i in 0..k.length) {
            assertEquals(ji.setIndex(i), ki.setIndex(i), label)
            assertEquals(ji.attributes.entries.associate { key(it.key) to key(it.value) }, ki.attributes.entries.associate { key(it.key) to key(it.value) }, label)
            assertEquals(ji.runStart, ki.runStart, label); assertEquals(ji.runLimit, ki.runLimit, label)
        }
    }
    @Test fun formattedResultsFromAllPipelineModes() {
        for (locale in listOf("en", "de", "fr", "ar@numbers=arab")) for (notation in listOf("simple", "scientific", "compact")) for (unit in listOf("none", "USD", "meter", "foot-and-inch")) {
            val macros = MacroProps().also { it.loc = ULocale(locale); it.unitWidth = UnitWidth.FULL_NAME }
            var jf = JFormatter.withLocale(JLocale(locale)).unitWidth(JFormatter.UnitWidth.FULL_NAME)
            when (notation) {
                "scientific" -> { jf = jf.notation(JNotation.scientific()); macros.notation = Notation.scientific() }
                "compact" -> { jf = jf.notation(JNotation.compactShort()); macros.notation = Notation.compactShort() }
            }
            if (unit == "USD") { jf = jf.unit(JCurrency.getInstance(unit)); macros.unit = Currency.getInstance(unit) }
            else if (unit != "none") { jf = jf.unit(JUnit.forIdentifier(unit)); macros.unit = MeasureUnit.forIdentifier(unit) }
            val pipeline = NumberFormatterImpl(macros)
            val values = listOf(-12345.6789, -0.0, 0.0, 1.5, 2.0) +
                if (unit == "foot-and-inch") emptyList() else listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
            for (value in values) {
                val output = FormattedStringBuilder(); val quantity = DecimalQuantity_DualStorageBCD(value)
                val micros = pipeline.format(quantity, output)
                compare(jf.format(value), FormattedNumber(output, quantity, micros.outputUnit, micros.gender), "$locale/$notation/$unit/$value")
            }
        }
    }
    @Test fun constructorAliasesNullableMetadataAndAppendFailure() {
        val constructor = JResult::class.java.getDeclaredConstructor(JBuffer::class.java, JQuantity::class.java, JUnit::class.java, String::class.java).also { it.isAccessible = true }
        for (gender in listOf(null, "", "feminine", "masculine", "common", "other", "FEMININE", "unknown")) {
            val jb = JBuffer(); val kb = FormattedStringBuilder(); jb.append("12", JField.INTEGER); kb.append("12", NumberFormatField.INTEGER)
            val jq = JBCD(BigDecimal("12")); val kq = DecimalQuantity_DualStorageBCD(BigDecimal("12"))
            val j = constructor.newInstance(jb, jq, JUnit.METER, gender) as JResult
            val k = FormattedNumber(kb, kq, MeasureUnit.METER, gender)
            compare(j, k, "$gender")
            assertSame(jq, j.fixedDecimal); assertSame(kq, k.getFixedDecimal()); assertSame(MeasureUnit.METER, k.getOutputUnit())
            jb.append(".5", JField.FRACTION); kb.append(".5", NumberFormatField.FRACTION)
            jq.setToInt(20); kq.setToInt(20)
            assertEquals(j.toString(), k.toString()); assertEquals(j.toBigDecimal(), k.toBigDecimal())
            val failure = java.io.IOException("append failure")
            val appendable = object : Appendable {
                override fun append(csq: CharSequence?): Appendable = throw failure
                override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = throw failure
                override fun append(c: Char): Appendable = throw failure
            }
            assertSame(failure, assertFailsWith<com.ibm.icu.util.ICUUncheckedIOException> { j.appendTo(appendable) }.cause)
            assertSame(failure, assertFailsWith<ICUUncheckedIOException> { k.appendTo(appendable) }.cause)
        }
        val j = constructor.newInstance(null, null, null, null) as JResult
        val k = FormattedNumber(null, null, null, null)
        assertEquals(j.gender, k.getGender()); assertEquals(j.nounClass.name, k.getNounClass().name)
        assertNull(k.getFixedDecimal()); assertNull(k.getOutputUnit())
        assertEquals(outcome { j.toBigDecimal() }, outcome { k.toBigDecimal() })
        assertEquals(outcome { j.toString() }, outcome { k.toString() })
    }
}
