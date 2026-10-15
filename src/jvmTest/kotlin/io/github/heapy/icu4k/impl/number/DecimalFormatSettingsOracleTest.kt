// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.text.DecimalFormat as JFormat
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.CurrencyPluralInfo as JPluralInfo
import com.ibm.icu.impl.number.DecimalFormatProperties as JProperties
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.math.MathContext as JContext
import com.ibm.icu.math.BigDecimal as JDecimal
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.math.MathContext as IcuContext
import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.*

class DecimalFormatSettingsOracleTest {
    private fun j() = JFormat("#,##0.00", JSymbols(JLocale.US))
    private fun k() = DecimalFormatCore.create("#,##0.00", DecimalFormatSymbols(ULocale("en_US")))
    private val jProps = JFormat::class.java.getDeclaredField("properties").also { it.isAccessible = true }
    private val jExport = JFormat::class.java.getDeclaredField("exportedProperties").also { it.isAccessible = true }
    private val jParser = JFormat::class.java.getDeclaredMethod("getParser").also { it.isAccessible = true }
    private val jCurrencyParser = JFormat::class.java.getDeclaredMethod("getCurrencyParser").also { it.isAccessible = true }
    private val jParserField = JFormat::class.java.getDeclaredField("parser").also { it.isAccessible = true }
    private val kParserField = DecimalFormatCore::class.java.getDeclaredField("parser").also { it.isAccessible = true }
    private val jCurrencyParserField = JFormat::class.java.getDeclaredField("currencyParser").also { it.isAccessible = true }
    private val kCurrencyParserField = DecimalFormatCore::class.java.getDeclaredField("currencyParser").also { it.isAccessible = true }
    private fun type(type: Class<*>): Class<*> = when (type) {
        JContext::class.java -> IcuContext::class.java
        JDecimal::class.java -> IcuDecimal::class.java
        JCurrency::class.java -> Currency::class.java
        JCurrency.CurrencyUsage::class.java -> Currency.CurrencyUsage::class.java
        JPluralInfo::class.java -> CurrencyPluralInfo::class.java
        JProperties.ParseMode::class.java -> DecimalFormatProperties.ParseMode::class.java
        JSymbols::class.java -> DecimalFormatSymbols::class.java
        else -> type
    }
    private fun invoke(method: Method, receiver: Any, vararg args: Any?): Any? = try {
        method.invoke(receiver, *args)
    } catch (e: InvocationTargetException) { throw e.targetException }
    private fun normalized(value: Any?): Any? = when (value) {
        is Enum<*> -> value.name
        is BigDecimal -> "decimal:$value"
        is JCurrency -> "currency:${value.currencyCode}"
        is Currency -> "currency:${value.getCurrencyCode()}"
        is MathContext -> value.toString()
        is JContext -> value.toString()
        is IcuContext -> value.toString()
        is JPluralInfo -> listOf(value.locale.name, value.pluralRules.toString()) + listOf("zero", "one", "two", "few", "many", "other").map { value.getCurrencyPluralPattern(it) }
        is CurrencyPluralInfo -> listOf(value.getLocale()!!.getName(), value.getPluralRules().toString()) + listOf("zero", "one", "two", "few", "many", "other").map { value.getCurrencyPluralPattern(it) }
        is JSymbols -> listOf(value.currencySymbol, value.internationalCurrencySymbol, value.decimalSeparatorString, value.monetaryDecimalSeparatorString)
        is DecimalFormatSymbols -> listOf(value.getCurrencySymbol(), value.getInternationalCurrencySymbol(), value.getDecimalSeparatorString(), value.getMonetaryDecimalSeparatorString())
        else -> value
    }
    private fun outcome(block: () -> Any?): Any? = try { normalized(block()) } catch (e: Throwable) {
        when (e) {
            is NullPointerException -> "NPE"
            is IndexOutOfBoundsException -> "bounds"
            else -> "${e.javaClass.name}:${e.message}"
        }
    }
    private val getters = listOf(
        "getPositivePrefix", "getNegativePrefix", "getPositiveSuffix", "getNegativeSuffix", "isSignAlwaysShown",
        "getMultiplier", "getRoundingIncrement", "getRoundingMode", "getMathContext", "getMathContextICU",
        "getMinimumIntegerDigits", "getMaximumIntegerDigits", "getMinimumFractionDigits", "getMaximumFractionDigits",
        "areSignificantDigitsUsed", "getMinimumSignificantDigits", "getMaximumSignificantDigits", "getFormatWidth",
        "getPadCharacter", "getPadPosition", "isScientificNotation", "getMinimumExponentDigits", "isExponentSignAlwaysShown",
        "isGroupingUsed", "getGroupingSize", "getSecondaryGroupingSize", "getMinimumGroupingDigits", "isDecimalSeparatorAlwaysShown",
        "getCurrency", "getCurrencyUsage", "getCurrencyPluralInfo", "isParseBigDecimal", "getParseMaxDigits", "isParseStrict",
        "isParseIntegerOnly", "isDecimalPatternMatchRequired", "isParseNoExponent", "isParseCaseSensitive", "getDecimalFormatSymbols"
    )
    private fun state(j: JFormat, k: DecimalFormatCore, label: String) {
        for (name in getters) assertEquals(
            outcome { invoke(JFormat::class.java.getMethod(name), j) },
            outcome { invoke(DecimalFormatCore::class.java.getMethod(name), k) }, "$label/$name")
        val propertyGetters = JProperties::class.java.methods.filter { it.name.startsWith("get") && it.name != "getClass" && it.parameterCount == 0 }
        for ((jp, kp) in listOf(jProps.get(j) to k.properties, jExport.get(j) to k.exportedProperties)) {
            for (method in propertyGetters) assertEquals(
                outcome { invoke(method, jp) }, outcome { invoke(DecimalFormatProperties::class.java.getMethod(method.name), kp!!) }, "$label/properties/${method.name}")
        }
    }
    private fun action(j: JFormat, k: DecimalFormatCore, name: String, jArg: Any?, kArg: Any? = jArg, parameter: Class<*>, render: Boolean = true) {
        val jm = JFormat::class.java.getMethod(name, parameter)
        val km = DecimalFormatCore::class.java.getMethod(name, type(parameter))
        val oldJ = j.toNumberFormatter(); val oldK = k.toNumberFormatter()
        val oldParserJ = jParser.invoke(j); val oldParserK = k.getParser()
        val oldCurrencyJ = jCurrencyParser.invoke(j); val oldCurrencyK = k.getCurrencyParser()
        val label = "$name/$jArg"
        assertEquals(outcome { invoke(jm, j, jArg) }, outcome { invoke(km, k, kArg) }, label)
        assertEquals(oldJ === j.toNumberFormatter(), oldK === k.toNumberFormatter(), "$label/formatter publication")
        assertEquals(oldParserJ === jParserField.get(j), oldParserK === kParserField.get(k), "$label/parser publication")
        assertEquals(oldCurrencyJ === jCurrencyParserField.get(j), oldCurrencyK === kCurrencyParserField.get(k), "$label/currency parser publication")
        state(j, k, label)
        if (render) {
            assertEquals(outcome { j.format(1234.567) }, outcome { k.format(1234.567, StringBuilder(), FormatFieldPosition(0)).toString() }, label)
            assertEquals(outcome { j.toPattern() }, outcome { k.toPattern() }, label)
            assertEquals(outcome { j.toLocalizedPattern() }, outcome { k.toLocalizedPattern() }, label)
        }
    }
    @Test fun integerAndBooleanSettingsWithBoundaryInputs() {
        val intNames = listOf("setMultiplier", "setRoundingMode", "setMinimumIntegerDigits", "setMaximumIntegerDigits", "setMinimumFractionDigits", "setMaximumFractionDigits", "setMinimumSignificantDigits", "setMaximumSignificantDigits", "setFormatWidth", "setPadPosition", "setGroupingSize", "setSecondaryGroupingSize", "setMinimumGroupingDigits", "setParseMaxDigits")
        for (name in intNames) for (value in listOf(Int.MIN_VALUE, -999, -3, -2, -1, 0, 1, 2, 3, 6, 8, 9, 10, 999, 1000, Int.MAX_VALUE)) {
            action(j(), k(), name, value, parameter = Int::class.javaPrimitiveType!!, render = value in -10..10)
        }
        val boolNames = listOf("setSignAlwaysShown", "setSignificantDigitsUsed", "setScientificNotation", "setExponentSignAlwaysShown", "setGroupingUsed", "setDecimalSeparatorAlwaysShown", "setParseBigDecimal", "setParseStrict", "setParseIntegerOnly", "setDecimalPatternMatchRequired", "setParseNoExponent", "setParseCaseSensitive")
        for (name in boolNames) for (value in listOf(false, true)) action(j(), k(), name, value, parameter = Boolean::class.javaPrimitiveType!!)
        for (value in listOf<Byte>(Byte.MIN_VALUE, -1, 0, 1, 3, Byte.MAX_VALUE)) action(j(), k(), "setMinimumExponentDigits", value, parameter = Byte::class.javaPrimitiveType!!)
        for (value in listOf(' ', 'x', '\u0000', '\ud83d')) action(j(), k(), "setPadCharacter", value, parameter = Char::class.javaPrimitiveType!!)
        for (name in listOf("setPositivePrefix", "setNegativePrefix", "setPositiveSuffix", "setNegativeSuffix")) for (value in listOf(null, "", "literal+'¤")) action(j(), k(), name, value, parameter = String::class.java)
    }
    @Test fun roundingOverloadsAndContexts() {
        for (value in listOf<BigDecimal?>(null, BigDecimal.ZERO, BigDecimal("0.00"), BigDecimal("-0.05"), BigDecimal("0.05"), BigDecimal("1E+10"), BigDecimal("1E-20"))) action(j(), k(), "setRoundingIncrement", value, parameter = BigDecimal::class.java)
        for (value in listOf(-0.0, 0.1, -0.05, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY)) action(j(), k(), "setRoundingIncrement", value, parameter = Double::class.javaPrimitiveType!!)
        for (text in listOf<String?>(null, "0.00", "0.05", "-0.125", "1E+9")) action(j(), k(), "setRoundingIncrement", text?.let { JDecimal(it) }, text?.let { IcuDecimal(it) }, JDecimal::class.java)
        for (digits in listOf(0, 1, 9, 34)) for (round in RoundingMode.entries) action(j(), k(), "setMathContext", MathContext(digits, round), parameter = MathContext::class.java)
        action(j(), k(), "setMathContext", null, parameter = MathContext::class.java)
        action(j(), k(), "setMathContextICU", null, parameter = JContext::class.java)
        for (digits in listOf(0, 1, 9)) for (form in 0..2) for (lost in listOf(false, true)) for (round in 0..7) {
            val j = j(); val k = k()
            action(j, k, "setMathContextICU", JContext(digits, form, lost, round), IcuContext(digits, form, lost, round), JContext::class.java)
            assertEquals(j.clone().mathContextICU.toString(), k.copy().getMathContextICU().toString())
            action(j, k, "setMathContext", MathContext(6, RoundingMode.DOWN), parameter = MathContext::class.java)
        }
    }
    @Test fun currencyAndParseModeSettings() {
        for (code in listOf(null, "USD", "EUR", "CHF", "JPY", "XXX")) action(j(), k(), "setCurrency", code?.let { JCurrency.getInstance(it) }, code?.let { Currency.getInstance(it) }, JCurrency::class.java)
        for (usage in listOf<JCurrency.CurrencyUsage?>(null) + JCurrency.CurrencyUsage.entries) action(j(), k(), "setCurrencyUsage", usage, usage?.let { Currency.CurrencyUsage.valueOf(it.name) }, JCurrency.CurrencyUsage::class.java)
        for (locale in listOf(null, "en", "fr", "ar")) {
            val ji = locale?.let { JPluralInfo(JLocale(it)) }; val ki = locale?.let { CurrencyPluralInfo(ULocale(it)) }
            val j = j(); val k = k()
            action(j, k, "setCurrencyPluralInfo", ji, ki, JPluralInfo::class.java)
            assertEquals(ji === j.currencyPluralInfo, ki === k.getCurrencyPluralInfo())
            if (ji != null) {
                assertNotSame(ji, j.currencyPluralInfo); assertNotSame(ki, k.getCurrencyPluralInfo())
                ji.setCurrencyPluralPattern("other", "original changed"); ki!!.setCurrencyPluralPattern("other", "original changed")
                state(j, k, "caller mutation")
                assertNotEquals("original changed", k.getCurrencyPluralInfo()!!.getCurrencyPluralPattern("other"))
            }
        }
        for (mode in listOf<JProperties.ParseMode?>(null) + JProperties.ParseMode.entries) action(j(), k(), "setParseStrictMode", mode, mode?.let { DecimalFormatProperties.ParseMode.valueOf(it.name) }, JProperties.ParseMode::class.java)
    }
    @Test fun failedContextRefreshKeepsPublishedRuntimeAndUpdatesRememberedForm() {
        val j = j(); val k = k()
        action(j, k, "setMultiplier", -7, parameter = Int::class.javaPrimitiveType!!)
        action(j, k, "setMathContextICU", JContext(4, 2, true, 0), IcuContext(4, 2, true, 0), JContext::class.java)
        action(j, k, "setMultiplier", 1, parameter = Int::class.javaPrimitiveType!!)
    }
    @Test fun repeatedSettingsRetainCouplingAndNoOpIdentity() {
        val random = Random(69381)
        val j = j(); val k = k()
        val names = listOf("setMinimumIntegerDigits", "setMaximumIntegerDigits", "setMinimumFractionDigits", "setMaximumFractionDigits", "setMinimumSignificantDigits", "setMaximumSignificantDigits", "setMultiplier", "setGroupingSize", "setSecondaryGroupingSize")
        repeat(180) { action(j, k, names[random.nextInt(names.size)], random.nextInt(-3, 12), parameter = Int::class.javaPrimitiveType!!) }
        for (use in listOf(true, true, false, false, true)) action(j, k, "setSignificantDigitsUsed", use, parameter = Boolean::class.javaPrimitiveType!!)
        action(j, k, "setRoundingIncrement", BigDecimal("0.05"), parameter = BigDecimal::class.java)
        action(j, k, "setRoundingIncrement", BigDecimal.ZERO, parameter = BigDecimal::class.java)
        action(j, k, "setRoundingIncrement", -0.0, parameter = Double::class.javaPrimitiveType!!)
    }
}
