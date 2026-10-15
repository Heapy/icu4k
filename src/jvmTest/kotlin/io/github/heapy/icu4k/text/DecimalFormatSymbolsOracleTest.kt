@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.NumberingSystem as JSystem
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.util.Currency as JCurrency
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class DecimalFormatSymbolsOracleTest {
    private val getters = listOf("ZeroDigit", "Digits", "DigitStrings", "DigitStringsLocal", "CodePointZero", "SignificantDigit", "GroupingSeparator", "GroupingSeparatorString", "DecimalSeparator", "DecimalSeparatorString", "PerMill", "PerMillString", "Percent", "PercentString", "Digit", "PatternSeparator", "Infinity", "NaN", "MinusSign", "MinusSignString", "PlusSign", "PlusSignString", "ApproximatelySignString", "CurrencySymbol", "InternationalCurrencySymbol", "Currency", "MonetaryDecimalSeparator", "MonetaryDecimalSeparatorString", "MonetaryGroupingSeparator", "MonetaryGroupingSeparatorString", "CurrencyPattern", "ExponentMultiplicationSign", "ExponentSeparator", "PadEscape", "ULocale")
    private fun normalize(value: Any?): Any? = when (value) {
        is Char -> value.code
        is CharArray -> value.map { it.code }
        is Array<*> -> value.map(::normalize)
        is JLocale -> value.name
        is ULocale -> value.getName()
        is JCurrency -> value.currencyCode
        is Currency -> value.getCurrencyCode()
        else -> value
    }
    private fun snapshot(value: Any): List<Any?> = getters.map { normalize(value.javaClass.getMethod("get$it").invoke(value)) } + value.hashCode()
    private fun check(j: JSymbols, k: DecimalFormatSymbols, label: String = "") {
        assertEquals(snapshot(j), snapshot(k), label)
        for (type in listOf(JLocale.VALID_LOCALE, JLocale.ACTUAL_LOCALE, null)) {
            val kt = if (type == JLocale.ACTUAL_LOCALE) ULocale.ACTUAL_LOCALE else if (type == null) null else ULocale.VALID_LOCALE
            assertEquals(j.getLocale(type)?.name, k.getLocale(kt)?.getName(), label)
        }
        assertEquals(j.getLocale(), k.getLocale(), label)
    }
    private fun invoke(value: Any, name: String, argument: Any?) {
        try { value.javaClass.methods.first { it.name == name && it.parameterCount == 1 }.invoke(value, argument) }
        catch (e: InvocationTargetException) { throw e.cause!! }
    }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) { e.javaClass.simpleName }
    @Test fun localeAndNumberingSystemInitializationMatchSource() {
        assertEquals(JSymbols.getAvailableULocales().map { it.name }, DecimalFormatSymbols.getAvailableULocales().map { it.getName() })
        assertEquals(JSymbols.getAvailableLocales().toList(), DecimalFormatSymbols.getAvailableLocales().toList())
        check(JSymbols(), DecimalFormatSymbols())
        for (locale in JSymbols.getAvailableULocales()) check(JSymbols(locale), DecimalFormatSymbols(ULocale(locale.name)), locale.name)
        for (locale in listOf("", "en_US", "ar_EG", "fa_IR", "bn", "zz_ZZ", "en_US@currency=chf;numbers=arab", "de_CH")) {
            val jl = JLocale(locale); val kl = ULocale(locale)
            for (name in JSystem.getAvailableNames()) {
                check(JSymbols.forNumberingSystem(jl, JSystem.getInstanceByName(name)), DecimalFormatSymbols.forNumberingSystem(kl, NumberingSystem.getInstanceByName(name)), "$locale/$name")
            }
            check(JSymbols.forNumberingSystem(jl, null), DecimalFormatSymbols.forNumberingSystem(kl, null))
            check(JSymbols.getInstance(jl.toLocale()), DecimalFormatSymbols.getInstance(jl.toLocale()))
            check(JSymbols(jl.toLocale()), DecimalFormatSymbols(jl.toLocale()))
        }
        assertEquals(outcome { JSymbols(null as JLocale?) }, outcome { DecimalFormatSymbols(null as ULocale?) })
        val jns = JSystem.getInstance(10, false, "abcdefghij"); val kns = NumberingSystem.getInstance(10, false, "abcdefghij")
        check(JSymbols.forNumberingSystem(JLocale("ar"), jns), DecimalFormatSymbols.forNumberingSystem(ULocale("ar"), kns))
    }
    @Test fun scalarMutationsFailuresAndEqualityMatchSource() {
        val j = JSymbols(JLocale("en_US")); val k = DecimalFormatSymbols(ULocale("en_US"))
        val charNames = listOf("ZeroDigit", "SignificantDigit", "GroupingSeparator", "DecimalSeparator", "PerMill", "Percent", "Digit", "PatternSeparator", "MinusSign", "PlusSign", "MonetaryDecimalSeparator", "MonetaryGroupingSeparator", "PadEscape")
        val stringNames = listOf("GroupingSeparatorString", "DecimalSeparatorString", "PerMillString", "PercentString", "MinusSignString", "PlusSignString", "ApproximatelySignString", "MonetaryDecimalSeparatorString", "MonetaryGroupingSeparatorString", "Infinity", "NaN", "CurrencySymbol", "InternationalCurrencySymbol", "ExponentMultiplicationSign", "ExponentSeparator")
        for (name in charNames) for (value in listOf('A', '\uFFFF', '\u0000')) {
            val jc = j.clone(); val kc = k.clone()
            invoke(jc, "set$name", value); invoke(kc, "set$name", value); check(jc, kc, "$name/$value")
            assertEquals(outcome { jc == j }, outcome { kc == k })
        }
        for (name in stringNames) for (value in listOf(null, "", "!", "ab", "😀")) {
            val jc = j.clone(); val kc = k.clone()
            assertEquals(outcome { invoke(jc, "set$name", value) }, outcome { invoke(kc, "set$name", value) }, "$name/$value")
            check(jc, kc, "$name/$value")
            assertEquals(outcome { jc == j }, outcome { kc == k }, "$name/$value")
            assertEquals(outcome { j == jc }, outcome { k == kc }, "$name/$value/reverse")
        }
        for (name in stringNames.take(9)) {
            val je = assertFailsWith<NullPointerException> { invoke(j, "set$name", null) }
            val ke = assertFailsWith<NullPointerException> { invoke(k, "set$name", null) }
            assertEquals(je.message, ke.message, name)
        }
        for (before in listOf(false, true)) for (index in -1..3) for (value in listOf(null, "custom")) {
            val jc = j.clone(); val kc = k.clone()
            assertEquals(outcome { jc.setPatternForCurrencySpacing(index, before, value) }, outcome { kc.setPatternForCurrencySpacing(index, before, value) })
            assertEquals(outcome { jc.getPatternForCurrencySpacing(index, before) }, outcome { kc.getPatternForCurrencySpacing(index, before) })
            assertEquals(outcome { jc == j }, outcome { kc == k })
            for (i in 0..2) assertEquals(jc.getPatternForCurrencySpacing(i, before), kc.getPatternForCurrencySpacing(i, before))
        }
    }
    @Test fun digitArraysAliasingAndShallowCloneMatchSource() {
        val inputs = listOf(null, emptyArray(), Array<String?>(10) { if (it == 3) null else it.toString() }, Array(10) { "" },
            Array(10) { "digit$it" }, Array(10) { (0x660 + it).toChar().toString() }, Array(10) { String(Character.toChars(0x1d7ce + it)) },
            Array(10) { (0xd800 + it).toChar().toString() }, Array(10) { (9 - it).toString() })
        val j = JSymbols(JLocale("en")); val k = DecimalFormatSymbols(ULocale("en"))
        for (input in inputs) {
            assertEquals(outcome { j.setDigitStrings(input) }, outcome { k.setDigitStrings(input) })
            check(j, k)
            val jc = j.clone(); val kc = k.clone()
            assertSame(k.getDigitStringsLocal(), kc.getDigitStringsLocal())
            j.getDigitStringsLocal()[2] = "changed"; k.getDigitStringsLocal()[2] = "changed"
            check(jc, kc)
            j.setZeroDigit('0'); k.setZeroDigit('0')
            assertNotSame(k.getDigitStringsLocal(), kc.getDigitStringsLocal())
            check(jc, kc); check(j, k)
            val jd = j.digits; val kd = k.getDigits(); jd[0] = 'x'; kd[0] = 'x'
            check(j, k)
            val js = j.digitStrings; val ks = k.getDigitStrings(); js[0] = "x"; ks[0] = "x"
            check(j, k)
        }
        val js = object : JSymbols(JLocale("en")) { val extra = Any() }
        val ks = object : DecimalFormatSymbols(ULocale("en")) { val extra = Any() }
        assertEquals<Class<*>>(js.javaClass, js.clone().javaClass); assertEquals<Class<*>>(ks.javaClass, ks.clone().javaClass)
        assertSame(ks.extra, ks.clone().javaClass.getDeclaredField("extra").also { it.isAccessible = true }.get(ks.clone()))
    }
    @Test fun currencyOverridesAndEqualCurrencyEarlyReturnMatchSource() {
        for (locale in listOf("en_US", "de_CH", "en_CH", "ar", "ru", "fr_CA", "zz_ZZ")) {
            val j = JSymbols(JLocale(locale)); val k = DecimalFormatSymbols(ULocale(locale))
            for (code in listOf("CHF", "USD", "JPY", "EUR", "ZZZ")) {
                j.currency = JCurrency.getInstance(code); k.setCurrency(Currency.getInstance(code)); check(j, k, "$locale/$code")
                j.currencySymbol = "custom"; k.setCurrencySymbol("custom")
                j.currency = JCurrency.getInstance(code); k.setCurrency(Currency.getInstance(code)); check(j, k, "$locale/$code/unchanged")
            }
            assertEquals(outcome { j.currency = null }, outcome { k.setCurrency(null) }); check(j, k)
        }
    }
    private fun roundTrip(value: Any): Any = ByteArrayOutputStream().let { bytes ->
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    }
    private fun setField(value: Any, name: String, fieldValue: Any?) { value.javaClass.getDeclaredField(name).also { it.isAccessible = true }.set(value, fieldValue) }
    @Test fun streamVersionMigrationsAndCurrencyRestorationMatchSource() {
        for (version in 0..10) {
            val j = JSymbols(JLocale("ar_EG")); val k = DecimalFormatSymbols(ULocale("ar_EG"))
            j.internationalCurrencySymbol = "EUR"; k.setInternationalCurrencySymbol("EUR")
            j.currencySymbol = "custom"; k.setCurrencySymbol("custom")
            for (value in listOf(j, k)) {
                setField(value, "serialVersionOnStream", version)
                if (version < 3) setField(value, "requestedLocale", null)
                if (version < 4) setField(value, "ulocale", null)
                if (version < 6) for (field in listOf("currencySpcBeforeSym", "currencySpcAfterSym")) setField(value, field, null)
                if (version < 7) for (field in listOf("minusString", "plusString")) setField(value, field, null)
                if (version < 8) setField(value, "exponentMultiplicationSign", null)
                if (version < 9) {
                    setField(value, "digitStrings", null)
                    if (version % 2 == 0) setField(value, "digits", null)
                    for (field in listOf("decimalSeparatorString", "groupingSeparatorString", "percentString", "perMillString", "monetarySeparatorString", "monetaryGroupingSeparatorString")) setField(value, field, null)
                }
                if (version < 10) setField(value, "approximatelyString", null)
            }
            check(roundTrip(j) as JSymbols, roundTrip(k) as DecimalFormatSymbols, "stream/$version")
        }
        for (code in listOf(null, "not-a-code", "usd")) {
            val j = JSymbols(JLocale("en")); val k = DecimalFormatSymbols(ULocale("en"))
            j.internationalCurrencySymbol = code; k.setInternationalCurrencySymbol(code)
            assertEquals(outcome { (roundTrip(j) as JSymbols).currency?.currencyCode }, outcome { (roundTrip(k) as DecimalFormatSymbols).getCurrency()?.getCurrencyCode() })
        }
    }
}
