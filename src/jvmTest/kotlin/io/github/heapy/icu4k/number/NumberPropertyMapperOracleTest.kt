@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.impl.number.DecimalFormatProperties as JProps
import com.ibm.icu.impl.number.MacroProps as JMacros
import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JBCD
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.text.CompactDecimalFormat.CompactStyle as JStyle
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.util.Currency as JCurrency
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.CompactStyle
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.Currency
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

class NumberPropertyMapperOracleTest {
    private val method = Class.forName("com.ibm.icu.number.NumberPropertyMapper").getDeclaredMethod("oldToNew",
        JProps::class.java, JSymbols::class.java, JProps::class.java).apply { isAccessible = true }
    private fun source(p: JProps?, s: JSymbols?, e: JProps?): JMacros = method.invoke(null, p, s, e) as JMacros
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) {
        var cause = e
        while (cause is InvocationTargetException) cause = cause.targetException
        when (cause) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            is AssertionError -> "assert:${cause.message}"
            else -> cause.javaClass.simpleName + ":" + cause.message
        }
    }
    private fun shape(value: Any?): Any? {
        if (value == null) return null
        val names = listOf("minFrac", "maxFrac", "minSig", "maxSig", "usage", "mathContext", "trailingZeroDisplay", "increment",
            "minInt", "maxInt", "engineeringInterval", "requireMinInt", "minExponentDigits", "exponentSignDisplay",
            "grouping1", "grouping2", "minGrouping", "paddingString", "targetWidth", "position",
            "magnitude", "arbitrary", "reciprocal", "mc", "compactStyle", "compactCustomData")
        val result = linkedMapOf<String, Any?>("class" to value.javaClass.simpleName)
        for (name in names) {
            var type: Class<*>? = value.javaClass
            while (type != null) {
                val field = type.declaredFields.firstOrNull { it.name == name }
                if (field != null) { field.isAccessible = true; result[name] = field.get(value)?.toString(); break }
                type = type.superclass
            }
        }
        return result
    }
    private fun compare(jp: JProps, kp: DecimalFormatProperties, locale: String = "en", customSymbols: Boolean = false) {
        val js = JSymbols(JLocale(locale)); val ks = DecimalFormatSymbols(ULocale(locale))
        if (customSymbols) { js.currencySymbol = "custom"; ks.setCurrencySymbol("custom") }
        val je = JProps().setFormatWidth(77); val ke = DecimalFormatProperties().setFormatWidth(77)
        var jm: JMacros? = null; var km: MacroProps? = null
        assertEquals(outcome { jm = source(jp, js, je); "ok" }, outcome { km = NumberPropertyMapper.oldToNew(kp, ks, ke); "ok" })
        assertEquals(je.toString(), ke.toString(), "exports")
        if (jm == null) return
        val j = jm!!; val k = km!!
        assertSame(js, j.symbols); assertSame(ks, k.symbols)
        for ((a, b) in listOf(j.precision to k.precision, j.integerWidth to k.integerWidth, j.grouping to k.grouping,
            j.notation to k.notation, j.padder to k.padder, j.scale to k.scale)) assertEquals(shape(a), shape(b))
        assertEquals(j.unit?.toString(), k.unit?.toString())
        assertEquals(j.sign?.name, k.sign?.name); assertEquals(j.decimal?.name, k.decimal?.name)
        assertEquals(j.rules?.toString(), k.rules?.toString())
        assertEquals(j.loc?.toString(), k.loc?.toString()); assertEquals(j.threshold, k.threshold)
        for (flags in listOf(0, AffixPatternProvider.Flags.PREFIX, AffixPatternProvider.Flags.NEGATIVE_SUBPATTERN,
            AffixPatternProvider.Flags.PREFIX or AffixPatternProvider.Flags.NEGATIVE_SUBPATTERN)) {
            assertEquals(j.affixProvider.getString(flags), k.affixProvider!!.getString(flags))
        }
        if (j.precision != null) {
            val jc = if (j.precision is com.ibm.icu.number.CurrencyPrecision)
                (j.precision as com.ibm.icu.number.CurrencyPrecision).withCurrency(je.currency) else j.precision
            val kc = k.precision!!.withLocaleData(ke.getCurrency())
            for (input in listOf("0", "1.2345", "9.999", "-12345.6789")) {
                val jq = JBCD(BigDecimal(input)); val kq = DecimalQuantity_DualStorageBCD(BigDecimal(input))
                assertEquals(outcome { jc.apply(jq); listOf(jq.lowerDisplayMagnitude, jq.upperDisplayMagnitude,
                    if (jq.upperDisplayMagnitude.toLong() - jq.lowerDisplayMagnitude > 2000) "large display" else jq.toPlainString()) }, outcome { kc.apply(kq); listOf(kq.getLowerDisplayMagnitude(), kq.getUpperDisplayMagnitude(),
                    if (kq.getUpperDisplayMagnitude().toLong() - kq.getLowerDisplayMagnitude() > 2000) "large display" else kq.toPlainString()) }, input)
            }
        }
    }

    @Test fun parsedPatternsAndCurrencyLocaleOverrides() {
        val patterns = listOf("0", "#.##", "0.00", "#,##0.00;(#,##0.00)", "¤#,##0.00", "¤¤0", "¤¤¤0", "0%",
            "0.00E0", "00.0E+00", "##0.00E0", "#.##E0", "#E0", "##00.0E0", "000000000.00E0", "@@@##", "0.05")
        for (pattern in patterns) for (locale in listOf("en_US", "fr_FR", "ja_JP")) {
            compare(JParser.parseToProperties(pattern), PatternStringParser.parseToProperties(pattern), locale, true)
        }
    }
    @Test fun rawValidationRoundingAndNotationCombinations() {
        val random = java.util.Random(7813)
        val values = listOf(-5, -1, 0, 1, 2, 8, 9, 999, 1000, Int.MAX_VALUE, Int.MIN_VALUE)
        repeat(240) { index ->
            val j = JProps(); val k = DecimalFormatProperties()
            val nums = IntArray(6) { values[random.nextInt(values.size)] }
            j.setMinimumIntegerDigits(nums[0]).setMaximumIntegerDigits(nums[1]).setMinimumFractionDigits(nums[2])
                .setMaximumFractionDigits(nums[3]).setMinimumSignificantDigits(nums[4]).setMaximumSignificantDigits(nums[5])
            k.setMinimumIntegerDigits(nums[0]).setMaximumIntegerDigits(nums[1]).setMinimumFractionDigits(nums[2])
                .setMaximumFractionDigits(nums[3]).setMinimumSignificantDigits(nums[4]).setMaximumSignificantDigits(nums[5])
            if (index % 3 == 0) { j.setMinimumExponentDigits(2); k.setMinimumExponentDigits(2) }
            if (index % 5 == 0) { j.setCompactStyle(JStyle.LONG); k.setCompactStyle(CompactStyle.LONG) }
            if (index % 7 == 0) { j.setCurrency(JCurrency.getInstance("CHF")); k.setCurrency(Currency.getInstance("CHF")) }
            if (index % 11 == 0) { j.setCurrencyUsage(JCurrency.CurrencyUsage.CASH); k.setCurrencyUsage(Currency.CurrencyUsage.CASH) }
            val increment = when (index % 9) { 0 -> BigDecimal("0.05"); 1 -> BigDecimal("0.00000001"); 2 -> BigDecimal.ZERO; else -> null }
            j.setRoundingIncrement(increment); k.setRoundingIncrement(increment)
            j.setMathContext(MathContext(7, RoundingMode.HALF_UP)); k.setMathContext(MathContext(7, RoundingMode.HALF_UP))
            compare(j, k)
        }
    }
    @Test fun groupingPaddingScaleCompactCustomAndExportAliasing() {
        val data = mapOf("1000" to mapOf("other" to "0 K"))
        for (grouping in listOf(-1, 0, 1, 3, 32768)) for (multiplier in listOf<BigDecimal?>(null, BigDecimal("2.5"), BigDecimal("100"))) {
            val j = JParser.parseToProperties("#,##0.00"); val k = PatternStringParser.parseToProperties("#,##0.00")
            j.setGroupingSize(grouping).setSecondaryGroupingSize(2).setMinimumGroupingDigits(2).setSignAlwaysShown(true)
                .setDecimalSeparatorAlwaysShown(true).setFormatWidth(10).setPadString("xy").setMultiplier(multiplier)
                .setCompactStyle(JStyle.SHORT).setCompactCustomData(data)
            k.setGroupingSize(grouping).setSecondaryGroupingSize(2).setMinimumGroupingDigits(2).setSignAlwaysShown(true)
                .setDecimalSeparatorAlwaysShown(true).setFormatWidth(10).setPadString("xy").setMultiplier(multiplier)
                .setCompactStyle(CompactStyle.SHORT).setCompactCustomData(data)
            compare(j, k)
            source(j, JSymbols(JLocale.US), j)
            NumberPropertyMapper.oldToNew(k, DecimalFormatSymbols(ULocale("en_US")), k)
            assertEquals(j.toString(), k.toString())
        }
        val js = JSymbols(JLocale.US); val ks = DecimalFormatSymbols(ULocale("en_US"))
        assertEquals(outcome { source(null, js, null) }, outcome { NumberPropertyMapper.oldToNew(null, ks, null) })
        assertEquals(outcome { source(JProps(), null, null) }, outcome { NumberPropertyMapper.oldToNew(DecimalFormatProperties(), null, null) })
    }
    private class RecordingSource(val trace: MutableList<String>) : JProps() {
        override fun getMaximumIntegerDigits(): Int { trace.add("MaximumIntegerDigits"); return super.getMaximumIntegerDigits() }
        override fun getMinimumIntegerDigits(): Int { trace.add("MinimumIntegerDigits"); return super.getMinimumIntegerDigits() }
        override fun getMaximumFractionDigits(): Int { trace.add("MaximumFractionDigits"); return super.getMaximumFractionDigits() }
        override fun getMinimumFractionDigits(): Int { trace.add("MinimumFractionDigits"); return super.getMinimumFractionDigits() }
        override fun getMinimumSignificantDigits(): Int { trace.add("MinimumSignificantDigits"); return super.getMinimumSignificantDigits() }
        override fun getMaximumSignificantDigits(): Int { trace.add("MaximumSignificantDigits"); return super.getMaximumSignificantDigits() }
        override fun getFormatWidth(): Int { trace.add("FormatWidth"); return super.getFormatWidth() }
        override fun getMinimumExponentDigits(): Int { trace.add("MinimumExponentDigits"); return super.getMinimumExponentDigits() }
        override fun getMagnitudeMultiplier(): Int { trace.add("MagnitudeMultiplier"); return super.getMagnitudeMultiplier() }
        override fun getRoundingIncrement(): BigDecimal? { trace.add("RoundingIncrement"); return super.getRoundingIncrement() }
        override fun getMathContext(): MathContext? { trace.add("MathContext"); return super.getMathContext() }
        override fun getRoundingMode(): RoundingMode? { trace.add("RoundingMode"); return super.getRoundingMode() }
        override fun getMultiplier(): BigDecimal? { trace.add("Multiplier"); return super.getMultiplier() }
    }
    private class RecordingPort(val trace: MutableList<String>) : DecimalFormatProperties() {
        override fun getMaximumIntegerDigits(): Int { trace.add("MaximumIntegerDigits"); return super.getMaximumIntegerDigits() }
        override fun getMinimumIntegerDigits(): Int { trace.add("MinimumIntegerDigits"); return super.getMinimumIntegerDigits() }
        override fun getMaximumFractionDigits(): Int { trace.add("MaximumFractionDigits"); return super.getMaximumFractionDigits() }
        override fun getMinimumFractionDigits(): Int { trace.add("MinimumFractionDigits"); return super.getMinimumFractionDigits() }
        override fun getMinimumSignificantDigits(): Int { trace.add("MinimumSignificantDigits"); return super.getMinimumSignificantDigits() }
        override fun getMaximumSignificantDigits(): Int { trace.add("MaximumSignificantDigits"); return super.getMaximumSignificantDigits() }
        override fun getFormatWidth(): Int { trace.add("FormatWidth"); return super.getFormatWidth() }
        override fun getMinimumExponentDigits(): Int { trace.add("MinimumExponentDigits"); return super.getMinimumExponentDigits() }
        override fun getMagnitudeMultiplier(): Int { trace.add("MagnitudeMultiplier"); return super.getMagnitudeMultiplier() }
        override fun getRoundingIncrement(): BigDecimal? { trace.add("RoundingIncrement"); return super.getRoundingIncrement() }
        override fun getMathContext(): MathContext? { trace.add("MathContext"); return super.getMathContext() }
        override fun getRoundingMode(): RoundingMode? { trace.add("RoundingMode"); return super.getRoundingMode() }
        override fun getMultiplier(): BigDecimal? { trace.add("Multiplier"); return super.getMultiplier() }
    }
    @Test fun virtualPropertyReadAndExportSetterOrder() {
        val jt = mutableListOf<String>(); val kt = mutableListOf<String>()
        val j = RecordingSource(jt).apply { setMinimumFractionDigits(2); setMaximumFractionDigits(4); setMinimumExponentDigits(1); setMultiplier(BigDecimal("2.5")) }
        val k = RecordingPort(kt).apply { setMinimumFractionDigits(2); setMaximumFractionDigits(4); setMinimumExponentDigits(1); setMultiplier(BigDecimal("2.5")) }
        val je = object : JProps() {
            override fun setCurrency(value: JCurrency?): JProps { jt.add("setCurrency"); return super.setCurrency(value) }
            override fun setMathContext(value: MathContext?): JProps { jt.add("setMathContext"); return super.setMathContext(value) }
            override fun setRoundingMode(value: RoundingMode?): JProps { jt.add("setRoundingMode"); return super.setRoundingMode(value) }
            override fun setMinimumIntegerDigits(value: Int): JProps { jt.add("setMinimumIntegerDigits"); return super.setMinimumIntegerDigits(value) }
            override fun setMaximumIntegerDigits(value: Int): JProps { jt.add("setMaximumIntegerDigits"); return super.setMaximumIntegerDigits(value) }
            override fun setMinimumFractionDigits(value: Int): JProps { jt.add("setMinimumFractionDigits"); return super.setMinimumFractionDigits(value) }
            override fun setMaximumFractionDigits(value: Int): JProps { jt.add("setMaximumFractionDigits"); return super.setMaximumFractionDigits(value) }
            override fun setMinimumSignificantDigits(value: Int): JProps { jt.add("setMinimumSignificantDigits"); return super.setMinimumSignificantDigits(value) }
            override fun setMaximumSignificantDigits(value: Int): JProps { jt.add("setMaximumSignificantDigits"); return super.setMaximumSignificantDigits(value) }
            override fun setRoundingIncrement(value: BigDecimal?): JProps { jt.add("setRoundingIncrement"); return super.setRoundingIncrement(value) }
        }
        val ke = object : DecimalFormatProperties() {
            override fun setCurrency(value: Currency?): DecimalFormatProperties { kt.add("setCurrency"); return super.setCurrency(value) }
            override fun setMathContext(value: MathContext?): DecimalFormatProperties { kt.add("setMathContext"); return super.setMathContext(value) }
            override fun setRoundingMode(value: RoundingMode?): DecimalFormatProperties { kt.add("setRoundingMode"); return super.setRoundingMode(value) }
            override fun setMinimumIntegerDigits(value: Int): DecimalFormatProperties { kt.add("setMinimumIntegerDigits"); return super.setMinimumIntegerDigits(value) }
            override fun setMaximumIntegerDigits(value: Int): DecimalFormatProperties { kt.add("setMaximumIntegerDigits"); return super.setMaximumIntegerDigits(value) }
            override fun setMinimumFractionDigits(value: Int): DecimalFormatProperties { kt.add("setMinimumFractionDigits"); return super.setMinimumFractionDigits(value) }
            override fun setMaximumFractionDigits(value: Int): DecimalFormatProperties { kt.add("setMaximumFractionDigits"); return super.setMaximumFractionDigits(value) }
            override fun setMinimumSignificantDigits(value: Int): DecimalFormatProperties { kt.add("setMinimumSignificantDigits"); return super.setMinimumSignificantDigits(value) }
            override fun setMaximumSignificantDigits(value: Int): DecimalFormatProperties { kt.add("setMaximumSignificantDigits"); return super.setMaximumSignificantDigits(value) }
            override fun setRoundingIncrement(value: BigDecimal?): DecimalFormatProperties { kt.add("setRoundingIncrement"); return super.setRoundingIncrement(value) }
        }
        source(j, JSymbols(JLocale.US), je)
        NumberPropertyMapper.oldToNew(k, DecimalFormatSymbols(ULocale("en_US")), ke)
        assertEquals(jt, kt)
    }

    @Test fun currencyPluralRulesExplicitUsageAndNullExports() {
        for (code in listOf("USD", "CHF", "JPY")) for (usage in JCurrency.CurrencyUsage.entries) {
            val j = JProps().setCurrency(JCurrency.getInstance(code)).setCurrencyUsage(usage)
                .setCurrencyPluralInfo(com.ibm.icu.text.CurrencyPluralInfo(JLocale("ru")))
            val k = DecimalFormatProperties().setCurrency(Currency.getInstance(code))
                .setCurrencyUsage(Currency.CurrencyUsage.valueOf(usage.name))
                .setCurrencyPluralInfo(io.github.heapy.icu4k.text.CurrencyPluralInfo(ULocale("ru")))
            compare(j, k, "ru", true)
            val jm = source(j, JSymbols(JLocale("ru")), null)
            val km = NumberPropertyMapper.oldToNew(k, DecimalFormatSymbols(ULocale("ru")), null)
            assertEquals(shape(jm.precision), shape(km.precision))
            assertEquals(jm.rules.toString(), km.rules.toString())
        }
    }

}
