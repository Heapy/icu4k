@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.impl.number.MacroProps as JMacros
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.impl.StandardPlural as JPlural
import com.ibm.icu.number.NumberFormatter as JFormatter
import com.ibm.icu.number.Notation as JNotation
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.number.Scale as JScale
import com.ibm.icu.number.IntegerWidth as JWidth
import com.ibm.icu.text.NumberingSystem as JNumbering
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.*
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class NumberFormatterPipelineOracleTest {
    private val klass = Class.forName("com.ibm.icu.number.NumberFormatterImpl")
    private fun invoke(name: String, instance: Any?, vararg args: Any?): Any? = try {
        klass.declaredMethods.single { it.name == name }.also { it.isAccessible = true }.invoke(instance, *args)
    } catch (e: InvocationTargetException) { throw e.cause!! }
    private fun construct(macros: JMacros): Any = try {
        klass.getDeclaredConstructor(JMacros::class.java).also { it.isAccessible = true }.newInstance(macros)
    } catch (e: InvocationTargetException) { throw e.cause!! }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "null"
            is java.util.MissingResourceException, is MissingResourceException -> "missing"
            is IndexOutOfBoundsException -> "bounds"
            else -> e.javaClass.simpleName + ":" + e.message
        }
    }
    private fun compare(j: JBuffer, k: FormattedStringBuilder, label: String) {
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, k.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, label)
    }
    private fun state(j: JMicro, k: MicroProps, label: String) {
        assertEquals(j.nsName, k.nsName, label)
        assertEquals(j.gender, k.gender, label)
        assertEquals(j.useCurrency, k.useCurrency, label)
        assertEquals(j.currencyAsDecimal, k.currencyAsDecimal, label)
        assertEquals(j.outputUnit?.identifier, k.outputUnit?.getIdentifier(), label)
        assertEquals(j.indexOfQuantity, k.indexOfQuantity, label)
        assertEquals(j.mixedMeasures?.map { it.toString() }, k.mixedMeasures?.map { it.toString() }, label)
        assertEquals(j.rounder == null, k.rounder == null, label)
    }
    private fun run(j: JMacros, k: MacroProps, label: String, values: List<Double> = listOf(-12345.6789, -1.0, -0.0, 0.0, 0.99999, 1.5, 2.0, 999.99, 1234567.89, Double.NaN, Double.POSITIVE_INFINITY)) {
        var jp: Any? = null; var kp: NumberFormatterImpl? = null
        assertEquals(outcome { jp = construct(j); "ok" }, outcome { kp = NumberFormatterImpl(k); "ok" }, "$label construction")
        if (jp == null || kp == null) return
        for (safe in listOf(false, true)) for (value in values) {
            val jq = JBCD(value); val kq = DecimalQuantity_DualStorageBCD(value)
            val jb = JBuffer(); val kb = FormattedStringBuilder()
            var jm: JMicro? = null; var km: MicroProps? = null
            val jo = outcome { jm = (if (safe) invoke("format", jp, jq, jb) else invoke("formatStatic", null, j, jq, jb)) as JMicro; "ok" }
            val ko = outcome { km = if (safe) kp!!.format(kq, kb) else NumberFormatterImpl.formatStatic(k, kq, kb); "ok" }
            assertEquals(jo, ko, "$label/$safe/$value result")
            compare(jb, kb, "$label/$safe/$value output")
            if (jm != null && km != null) state(jm!!, km!!, "$label/$safe/$value state")
            jq.roundToInfinity(); kq.roundToInfinity()
            assertEquals(jq.toPlainString(), kq.toPlainString(), "$label/$safe/$value quantity")
        }
    }
    @Test fun defaultsNotationsSymbolsAndCurrencyWidths() {
        for (locale in listOf("en", "fr", "de", "ru", "ar@numbers=arab", "ja")) for (notation in listOf("simple", "scientific", "engineering", "compactShort", "compactLong")) {
            for (unit in listOf("none", "percent", "permille", "USD", "JPY", "meter", "meter-second")) {
                for (width in listOf(UnitWidth.SHORT, UnitWidth.FULL_NAME)) {
                    val j = JMacros(); val k = MacroProps()
                    j.loc = JLocale(locale); k.loc = ULocale(locale)
                    when (notation) {
                        "scientific" -> { j.notation = JNotation.scientific(); k.notation = Notation.scientific() }
                        "engineering" -> { j.notation = JNotation.engineering(); k.notation = Notation.engineering() }
                        "compactShort" -> { j.notation = JNotation.compactShort(); k.notation = Notation.compactShort() }
                        "compactLong" -> { j.notation = JNotation.compactLong(); k.notation = Notation.compactLong() }
                    }
                    if (unit != "none") {
                        if (unit == "USD" || unit == "JPY") { j.unit = JCurrency.getInstance(unit); k.unit = Currency.getInstance(unit) }
                        else { j.unit = JUnit.forIdentifier(unit); k.unit = MeasureUnit.forIdentifier(unit) }
                    }
                    j.unitWidth = JFormatter.UnitWidth.valueOf(width.name); k.unitWidth = width
                    run(j, k, "$locale/$notation/$unit/$width")
                }
            }
        }
    }
    @Test fun settingsPoliciesAndPrefixSuffix() {
        for (sign in SignDisplay.entries) for (group in GroupingStrategy.entries) for (numbering in listOf("latn", "arab", "mathbold", "hanidec")) {
            val j = JMacros(); val k = MacroProps()
            j.loc = JLocale.ENGLISH; k.loc = ULocale.ENGLISH
            j.unit = JCurrency.getInstance("USD"); k.unit = Currency.getInstance("USD")
            j.sign = JFormatter.SignDisplay.valueOf(sign.name); k.sign = sign
            j.grouping = JFormatter.GroupingStrategy.valueOf(group.name); k.grouping = group
            j.symbols = JNumbering.getInstanceByName(numbering); k.symbols = NumberingSystem.getInstanceByName(numbering)
            j.precision = JPrecision.fixedFraction(2); k.precision = Precision.fixedFraction(2)
            j.integerWidth = JWidth.zeroFillTo(2).truncateAt(5); k.integerWidth = IntegerWidth.zeroFillTo(2).truncateAt(5)
            j.decimal = JFormatter.DecimalSeparatorDisplay.ALWAYS; k.decimal = DecimalSeparatorDisplay.ALWAYS
            j.scale = JScale.powerOfTen(1); k.scale = Scale.powerOfTen(1)
            run(j, k, "$sign/$group/$numbering", listOf(-123456.789, -0.0, 0.0, 2.0))
            val jp = construct(j); val kp = NumberFormatterImpl(k)
            for (negative in listOf((-1).toByte(), 1.toByte())) {
                val jb = JBuffer(); val kb = FormattedStringBuilder()
                assertEquals(invoke("getPrefixSuffix", jp, negative, JPlural.FEW, jb), kp.getPrefixSuffix(negative, StandardPlural.FEW, kb))
                compare(jb, kb, "prefix suffix")
                val js = JBuffer(); val ks = FormattedStringBuilder()
                assertEquals(invoke("getPrefixSuffixStatic", null, j, negative, null, js), NumberFormatterImpl.getPrefixSuffixStatic(k, negative, null, ks))
                compare(js, ks, "static prefix suffix")
            }
        }
    }
    @Test fun conversionsMixedUnitsUsageAndPerUnitRestrictions() {
        for (locale in listOf("en_US", "en_GB", "fr", "de")) for (unit in listOf("meter", "meter-and-centimeter", "foot-and-inch", "yard-and-foot-and-inch")) for (usage in listOf(null, "default", "person-height", "road")) {
            val j = JMacros(); val k = MacroProps()
            j.loc = JLocale(locale); k.loc = ULocale(locale)
            j.unit = JUnit.forIdentifier(unit); k.unit = MeasureUnit.forIdentifier(unit)
            j.usage = usage; k.usage = usage
            j.unitWidth = JFormatter.UnitWidth.FULL_NAME; k.unitWidth = UnitWidth.FULL_NAME
            run(j, k, "$locale/$unit/$usage", listOf(-123.456, 0.0, 1.5, 123456.0))
        }
        for (id in listOf("meter", "meter-second")) for (per in listOf("second", "meter-second")) {
            val j = JMacros(); val k = MacroProps(); j.loc = JLocale.ENGLISH; k.loc = ULocale.ENGLISH
            j.unit = JUnit.forIdentifier(id); k.unit = MeasureUnit.forIdentifier(id)
            j.perUnit = JUnit.forIdentifier(per); k.perUnit = MeasureUnit.forIdentifier(per)
            run(j, k, "$id/$per", listOf(1.0, 2.0))
        }
        for (unit in listOf("none", "USD", "percent")) {
            val j = JMacros(); val k = MacroProps(); j.loc = JLocale.ENGLISH; k.loc = ULocale.ENGLISH
            if (unit == "USD") { j.unit = JCurrency.getInstance("USD"); k.unit = Currency.getInstance("USD") }
            if (unit == "percent") { j.unit = JUnit.PERCENT; k.unit = MeasureUnit.PERCENT }
            j.usage = "default"; k.usage = "default"
            run(j, k, "unsupported usage $unit")
        }
    }
    @Test fun suppliedSymbolsAndOldAffixes() {
        for (compact in listOf(false, true)) for (pattern in listOf("+0.0;-0.0", "¤0.00", "0¤00")) {
            val j = JMacros(); val k = MacroProps(); j.loc = JLocale.ENGLISH; k.loc = ULocale.ENGLISH
            val js = JSymbols(JLocale.ENGLISH); val ks = DecimalFormatSymbols(ULocale.ENGLISH)
            js.decimalSeparatorString = "::"; ks.setDecimalSeparatorString("::")
            j.symbols = js; k.symbols = ks
            j.affixProvider = com.ibm.icu.impl.number.PatternStringParser.parseToPatternInfo(pattern)
            k.affixProvider = PatternStringParser.parseToPatternInfo(pattern)
            if (compact) { j.notation = JNotation.compactShort(); k.notation = Notation.compactShort() }
            j.approximately = true; k.approximately = true
            run(j, k, "symbols/$pattern/$compact")
            assertSame(ks, NumberFormatterImpl(k).getRawMicroProps().symbols)
        }
    }
}
