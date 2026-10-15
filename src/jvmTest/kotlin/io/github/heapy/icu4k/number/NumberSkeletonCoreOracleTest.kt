@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.number

import com.ibm.icu.impl.number.MacroProps as JMacros
import com.ibm.icu.number.NumberFormatter as JFormatter
import com.ibm.icu.number.Notation as JNotation
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.number.IntegerWidth as JWidth
import com.ibm.icu.number.Scale as JScale
import com.ibm.icu.text.NumberingSystem as JNS
import com.ibm.icu.util.Currency as JCurrency
import com.ibm.icu.util.MeasureUnit as JUnit
import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.number.*
import io.github.heapy.icu4k.text.NumberingSystem
import io.github.heapy.icu4k.util.*
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class NumberSkeletonCoreOracleTest {
    private val klass = Class.forName("com.ibm.icu.number.NumberSkeletonImpl")
    private fun source(name: String, arg: Any?): Any? = try {
        klass.declaredMethods.single { it.name == name }.also { it.isAccessible = true }.invoke(null, arg)
    } catch (e: InvocationTargetException) { throw e.cause!! }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) { is NullPointerException -> "null"; is AssertionError -> "assert"; else -> e.javaClass.simpleName + ":" + e.message + ":cause=" + e.cause?.javaClass?.simpleName + ":" + e.cause?.message }
    }
    private fun compare(skeleton: String?) {
        var j: JMacros? = null; var k: MacroProps? = null
        assertEquals(outcome { j = source("parseSkeleton", skeleton) as JMacros; "ok" }, outcome { k = NumberSkeletonCore.parseSkeleton(skeleton); "ok" }, "$skeleton parse")
        if (j == null || k == null) return
        assertEquals(outcome { source("generate", j) }, outcome { NumberSkeletonCore.generate(k) }, "$skeleton canonical")
        assertEquals(j!!.unit?.identifier, k!!.unit?.getIdentifier(), skeleton)
        assertEquals(j!!.perUnit?.identifier, k!!.perUnit?.getIdentifier(), skeleton)
        assertEquals(j!!.usage, k!!.usage, skeleton)
        if (skeleton == null) return
        // Compare actual formatting as well as normalization; normalized strings can hide bad settings.
        val jf = JFormatter.forSkeleton(skeleton).locale(com.ibm.icu.util.ULocale.ENGLISH)
        k!!.loc = ULocale.ENGLISH
        for (value in listOf(-1234.567, 0.0, 1.0, 2.5)) {
            val buffer = FormattedStringBuilder()
            assertEquals(outcome { jf.format(value).toString() }, outcome { NumberFormatterImpl(k).format(DecimalQuantity_DualStorageBCD(value), buffer); buffer.toString() }, "$skeleton format $value")
        }
    }
    private val stems = listOf("compact-short", "compact-long", "scientific", "engineering", "notation-simple", "base-unit", "percent", "permille", "%x100", "precision-integer", "precision-unlimited", "precision-currency-standard", "precision-currency-cash", "integer-width-trunc", "latin") +
        RoundingMode.entries.map { "rounding-mode-" + it.name.lowercase().replace('_', '-') } +
        GroupingStrategy.entries.map { "group-" + it.name.lowercase().replace('_', '-') } +
        UnitWidth.entries.map { "unit-width-" + it.name.lowercase().replace('_', '-') } +
        SignDisplay.entries.map { "sign-" + it.name.lowercase().replace('_', '-') } +
        DecimalSeparatorDisplay.entries.map { "decimal-" + it.name.lowercase().replace('_', '-') }
    @Test fun literalsAliasesBlueprintsOptionsAndFailures() {
        for (s in stems + listOf("K", "KK", "%", ",_", ",?", ",!", "+!", "+_", "()", "()!", "+?", "()?", "+-", "()-")) { compare(s); compare(" $s  "); compare("$s/$s"); compare("$s $s") }
        val tokens = listOf("", " ", "/", "scientific/", "scientific/*ee/sign-always", "engineering/+eee/+?", ".", ".0", ".00##", ".*", ".+", ".00+", "@", "@@##", "@@*", "@+", ".00/@##", ".00/@@*", ".00/@@##r/w", ".00/@@##s", ".00/@@", "precision-integer/@##/w", "precision-unlimited/w", "precision-currency-cash/w", "precision-increment/0.050/w", "precision-increment/0", "precision-increment/NaN", "precision-increment/1e-2147483649", "integer-width/*000", "integer-width/##00", "integer-width/+", "integer-width/#", "integer-width/0#", "E0", "EE+!000", "E+?00", "E", "EE", "000", "00x", "currency/usd", "currency/AB", "currency/12A", "measure-unit/length-meter", "per-measure-unit/duration-second", "unit/foot-and-inch", "unit/meter-per-100-second", "unit/unknown", "unit/", "usage/person-height", "numbering-system/arab", "numbering-system/unknown", "scale/0", "scale/-1.2300e4", "scale/x", "base-unit base-unit", "base-unit unit/meter", "unit/meter per-measure-unit/duration-second", "currency/USD per-measure-unit/duration-second", "per-measure-unit/duration-second currency/USD", "precision-integer//w", "precision-integer/w/w", "unknown", "null")
        for (s in tokens) compare(s)
        for (space in listOf(' ', '\t', '\n', '\r', '\u0085', '\u200e', '\u200f', '\u2028', '\u2029', '\u00a0', '\u2000')) compare("percent${space}group-off")
        compare(null)
        for (count in listOf(998, 999, 1000)) for (s in listOf("0".repeat(count), "." + "0".repeat(count), "@".repeat(count))) compare(s)
    }
    @Test fun randomizedTokensSeparatorsAndOptionSequences() {
        val random = Random(74081)
        val alphabet = listOf(".", "0", "#", "@", "*", "+", "E", "!", "?", "r", "s", "w", "/", " ", "😀")
        repeat(500) { compare(buildString { repeat(random.nextInt(1, 12)) { append(alphabet.random(random)) } }) }
        val options = listOf("/w", "/@##", "/USD", "/length-meter", "/0.05", "/sign-always", "/+ee", "/arab", "/meter", "/road")
        repeat(300) { compare(stems.random(random) + options.random(random) + if (random.nextBoolean()) " group-off" else "") }
    }
    @Test fun generatorSettingsAndUnsupportedPaths() {
        for (min in 0..3) for (max in min..5) for (trailing in listOf(false, true)) {
            val j = JMacros(); val k = MacroProps()
            j.precision = JPrecision.minMaxFraction(min, max); k.precision = Precision.minMaxFraction(min, max)
            if (trailing) { j.precision = j.precision.trailingZeroDisplay(JFormatter.TrailingZeroDisplay.HIDE_IF_WHOLE); k.precision = k.precision!!.trailingZeroDisplay(TrailingZeroDisplay.HIDE_IF_WHOLE) }
            assertEquals(source("generate", j), NumberSkeletonCore.generate(k))
            for (priority in RoundingPriority.entries) {
                j.precision = JPrecision.minMaxFraction(min, max).withSignificantDigits(2, 4, JFormatter.RoundingPriority.valueOf(priority.name))
                k.precision = Precision.minMaxFraction(min, max).withSignificantDigits(2, 4, priority)
                assertEquals(source("generate", j), NumberSkeletonCore.generate(k))
            }
        }
        for (mode in 0..10) {
            val j = JMacros(); val k = MacroProps()
            when (mode) {
                0 -> { j.notation = JNotation.scientific().withMinExponentDigits(3).withExponentSignDisplay(JFormatter.SignDisplay.EXCEPT_ZERO); k.notation = Notation.scientific().withMinExponentDigits(3).withExponentSignDisplay(SignDisplay.EXCEPT_ZERO) }
                1 -> { j.unit = JUnit.METER; j.perUnit = JUnit.SECOND; k.unit = MeasureUnit.METER; k.perUnit = MeasureUnit.SECOND }
                2 -> { j.unit = JCurrency.getInstance("USD"); j.perUnit = JUnit.SECOND; k.unit = Currency.getInstance("USD"); k.perUnit = MeasureUnit.SECOND }
                3 -> { j.padder = com.ibm.icu.impl.number.Padder.none(); k.padder = Padder.none() }
                4 -> { j.unitDisplayCase = "dative"; k.unitDisplayCase = "dative" }
                5 -> { j.symbols = com.ibm.icu.text.DecimalFormatSymbols(com.ibm.icu.util.ULocale.ENGLISH); k.symbols = io.github.heapy.icu4k.text.DecimalFormatSymbols(ULocale.ENGLISH) }
                6 -> { j.grouping = com.ibm.icu.impl.number.Grouper.forStrategy(JFormatter.GroupingStrategy.AUTO); k.grouping = Grouper.forStrategy(GroupingStrategy.AUTO) }
                7 -> { j.scale = JScale.byBigDecimal(java.math.BigDecimal("-1.2300E5")); k.scale = Scale.byBigDecimal(BigDecimal("-1.2300E5")) }
                8 -> { j.integerWidth = JWidth.zeroFillTo(2).truncateAt(5); k.integerWidth = IntegerWidth.zeroFillTo(2).truncateAt(5) }
                9 -> { j.perUnit = JCurrency.getInstance("USD"); k.perUnit = Currency.getInstance("USD") }
                10 -> { j.symbols = JNS.getInstanceByName("mathbold"); k.symbols = NumberingSystem.getInstanceByName("mathbold") }
            }
            assertEquals(outcome { source("generate", j) }, outcome { NumberSkeletonCore.generate(k) }, "mode $mode")
        }
    }
}
