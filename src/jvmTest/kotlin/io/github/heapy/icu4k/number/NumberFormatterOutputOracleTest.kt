package io.github.heapy.icu4k.number

import com.ibm.icu.impl.FormattedStringBuilder as JBuffer
import com.ibm.icu.impl.number.MicroProps as JMicro
import com.ibm.icu.impl.number.DecimalQuantity as JQuantity
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.impl.number.Grouper as JGrouper
import com.ibm.icu.impl.number.ConstantAffixModifier as JAffix
import com.ibm.icu.impl.number.Padder as JPadder
import com.ibm.icu.number.NumberFormatter.GroupingStrategy as JGrouping
import com.ibm.icu.number.NumberFormatter.DecimalSeparatorDisplay as JDecimal
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.NumberFormat.Field as JField
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField as Field
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import kotlin.test.*

class NumberFormatterOutputOracleTest {
    private val clazz = Class.forName("com.ibm.icu.number.NumberFormatterImpl")
    private val write = clazz.getDeclaredMethod("writeNumber", JMicro::class.java, JQuantity::class.java, JBuffer::class.java, Int::class.javaPrimitiveType).also { it.isAccessible = true }
    private val affixes = clazz.getDeclaredMethod("writeAffixes", JMicro::class.java, JBuffer::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).also { it.isAccessible = true }
    private fun invoke(method: java.lang.reflect.Method, vararg args: Any?): Any? = try { method.invoke(null, *args) } catch (e: InvocationTargetException) { throw e.cause!! }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> "${e.javaClass.simpleName}:${e.message}" }
    }
    private fun compare(j: JBuffer, k: FormattedStringBuilder, label: String) {
        assertEquals(j.toString(), k.toString(), label)
        assertEquals(j.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, k.toFieldArray().map { it?.toString()?.substringAfterLast('(') }, label)
    }
    @Test fun numericOutputSymbolsGroupingFieldsAndInsertionOffsets() {
        for (locale in listOf("en", "ar", "fr", "ja")) for (digits in listOf("normal", "supplementary", "strings")) {
            val js = JSymbols(JLocale(locale)); val ks = DecimalFormatSymbols(ULocale(locale))
            if (digits != "normal") {
                val data = Array(10) { if (digits == "strings") "<$it>" else String(Character.toChars(0x1d7ce + it)) }
                js.digitStrings = data; ks.setDigitStrings(data)
            }
            js.decimalSeparatorString = "::"; ks.setDecimalSeparatorString("::")
            js.groupingSeparatorString = "'"; ks.setGroupingSeparatorString("'")
            js.monetaryDecimalSeparatorString = "¤d"; ks.setMonetaryDecimalSeparatorString("¤d")
            js.monetaryGroupingSeparatorString = "¤g"; ks.setMonetaryGroupingSeparatorString("¤g")
            for (group in GroupingStrategy.entries) for (monetary in listOf(false, true)) for (always in listOf(false, true)) for (currencyDecimal in listOf(null, "USD")) {
                val jm = JMicro(true); val km = MicroProps(true)
                jm.symbols = js; km.symbols = ks
                jm.grouping = JGrouper.forStrategy(JGrouping.valueOf(group.name)); km.grouping = Grouper.forStrategy(group)
                val jp = com.ibm.icu.impl.number.PatternStringParser.parseToPatternInfo("#,##,##0.###")
                val kp = PatternStringParser.parseToPatternInfo("#,##,##0.###")
                jm.grouping = jm.grouping.withLocaleData(JLocale(locale), jp); km.grouping = km.grouping!!.withLocaleData(ULocale(locale), kp)
                jm.useCurrency = monetary; km.useCurrency = monetary
                jm.decimal = if (always) JDecimal.ALWAYS else JDecimal.AUTO; km.decimal = if (always) DecimalSeparatorDisplay.ALWAYS else DecimalSeparatorDisplay.AUTO
                jm.currencyAsDecimal = currencyDecimal; km.currencyAsDecimal = currencyDecimal
                for (value in listOf(0.0, -0.0, 0.25, -12345.625, 12345678.0, Double.NaN, Double.NEGATIVE_INFINITY)) for (minInt in listOf(0, 1, 4)) {
                    val jq = JBCD(value); val kq = DecimalQuantity_DualStorageBCD(value)
                    jq.roundToInfinity(); kq.roundToInfinity()
                    jq.setMinInteger(minInt); kq.setMinInteger(minInt)
                    jq.setMinFraction(2); kq.setMinFraction(2)
                    val jb = JBuffer(); val kb = FormattedStringBuilder()
                    jb.append("LR", JField.SIGN); kb.append("LR", Field.SIGN)
                    val label = "$locale/$digits/$group/$monetary/$always/$currencyDecimal/$value/$minInt"
                    assertEquals(invoke(write, jm, jq, jb, 1), NumberFormatterOutput.writeNumber(km, kq, kb, 1), label)
                    compare(jb, kb, label)
                }
            }
        }
    }
    @Test fun affixesPaddingPositionsAndSourceLengthAccounting() {
        for (position in Padder.PadPosition.entries) for (width in listOf(-1, 3, 15)) for (pad in listOf("_", "ab", "😀")) {
            val jm = JMicro(true); val km = MicroProps(true)
            jm.modInner = JAffix("I", "i", JField.EXPONENT, true); km.modInner = ConstantAffixModifier("I", "i", Field.EXPONENT, true)
            jm.modMiddle = JAffix("M", "m", JField.SIGN, false); km.modMiddle = ConstantAffixModifier("M", "m", Field.SIGN, false)
            jm.modOuter = JAffix("O", "o", JField.MEASURE_UNIT, false); km.modOuter = ConstantAffixModifier("O", "o", Field.MEASURE_UNIT, false)
            jm.padding = JPadder(pad, width, JPadder.PadPosition.valueOf(position.name)); km.padding = Padder(pad, width, position)
            val jb = JBuffer(); val kb = FormattedStringBuilder(); jb.append("12", JField.INTEGER); kb.append("12", Field.INTEGER)
            val label = "$position/$width/$pad"
            assertEquals(invoke(affixes, jm, jb, 0, 2), NumberFormatterOutput.writeAffixes(km, kb, 0, 2), label)
            compare(jb, kb, label)
        }
    }
    @Test fun quantityAndSymbolCallbacksIncludingFailures() {
        fun jq(value: Double, events: MutableList<String>): JQuantity {
            val delegate = JBCD(value)
            delegate.roundToInfinity()
            return Proxy.newProxyInstance(JQuantity::class.java.classLoader, arrayOf(JQuantity::class.java)) { _, method, args ->
                events.add(method.name + (args?.joinToString(prefix = "(", postfix = ")") ?: ""))
                try { method.invoke(delegate, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.cause!! }
            } as JQuantity
        }
        fun kq(value: Double, events: MutableList<String>): DecimalQuantity {
            val delegate = DecimalQuantity_DualStorageBCD(value)
            delegate.roundToInfinity()
            return Proxy.newProxyInstance(DecimalQuantity::class.java.classLoader, arrayOf(DecimalQuantity::class.java)) { _, method, args ->
                events.add(method.name + (args?.joinToString(prefix = "(", postfix = ")") ?: ""))
                try { method.invoke(delegate, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.cause!! }
            } as DecimalQuantity
        }
        for (value in listOf(0.0, 0.25, 12.0, Double.NaN, Double.POSITIVE_INFINITY)) for (nullOutput in listOf(false, true)) for (nullSymbols in listOf(false, true)) {
            val je = mutableListOf<String>(); val ke = mutableListOf<String>()
            val js = object : JSymbols(JLocale.ENGLISH) {
                override fun getCodePointZero(): Int { je.add("zero"); return super.getCodePointZero() }
                override fun getNaN(): String { je.add("nan"); return "NaN" }
                override fun getInfinity(): String { je.add("inf"); return "∞" }
            }
            val ks = object : DecimalFormatSymbols(ULocale.ENGLISH) {
                override fun getCodePointZero(): Int { ke.add("zero"); return super.getCodePointZero() }
                override fun getNaN(): String { ke.add("nan"); return "NaN" }
                override fun getInfinity(): String { ke.add("inf"); return "∞" }
            }
            val jm = JMicro(true); val km = MicroProps(true)
            jm.symbols = if (nullSymbols) null else js; km.symbols = if (nullSymbols) null else ks
            jm.grouping = JGrouper.forStrategy(JGrouping.OFF); km.grouping = Grouper.forStrategy(GroupingStrategy.OFF)
            jm.decimal = JDecimal.AUTO; km.decimal = DecimalSeparatorDisplay.AUTO
            val jv = jq(value, je); val kv = kq(value, ke)
            val jb = if (nullOutput) null else JBuffer(); val kb = if (nullOutput) null else FormattedStringBuilder()
            assertEquals(outcome { invoke(write, jm, jv, jb, 0) }, outcome { NumberFormatterOutput.writeNumber(km, kv, kb, 0) }, "$value/$nullOutput/$nullSymbols")
            assertEquals(je, ke, "$value/$nullOutput/$nullSymbols callbacks")
            if (jb != null && kb != null) compare(jb, kb, "partial output")
        }
    }
}
