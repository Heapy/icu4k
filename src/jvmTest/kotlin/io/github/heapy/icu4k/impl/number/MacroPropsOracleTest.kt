package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.MacroProps as JProps
import com.ibm.icu.number.Notation as JNotation
import com.ibm.icu.number.Precision as JPrecision
import com.ibm.icu.number.Scale as JScale
import com.ibm.icu.number.IntegerWidth as JWidth
import com.ibm.icu.number.NumberFormatter as JFormatter
import com.ibm.icu.impl.number.Padder as JPadder
import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.text.PluralRules as JRules
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.number.*
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.ULocale
import java.lang.reflect.Modifier
import kotlin.test.*

class MacroPropsOracleTest {
    private fun pair(): Pair<JProps, MacroProps> {
        val j = JProps().apply {
            notation = JNotation.compactLong(); unit = JUnit.METER; perUnit = JUnit.SECOND
            precision = JPrecision.fixedFraction(2); roundingMode = java.math.RoundingMode.CEILING
            grouping = "group"; padder = JPadder.none(); integerWidth = JWidth.zeroFillTo(3)
            symbols = JSymbols(JLocale.ENGLISH); unitWidth = JFormatter.UnitWidth.FULL_NAME; unitDisplayCase = "genitive"
            sign = JFormatter.SignDisplay.ALWAYS; approximately = false; decimal = JFormatter.DecimalSeparatorDisplay.ALWAYS
            scale = JScale.powerOfTen(2); usage = "person-height"; affixProvider = JParser.parseToPatternInfo("0")
            rules = JRules.forLocale(JLocale.ENGLISH); threshold = 5L; loc = JLocale.ENGLISH
        }
        val k = MacroProps().apply {
            notation = Notation.compactLong(); unit = MeasureUnit.METER; perUnit = MeasureUnit.SECOND
            precision = Precision.fixedFraction(2); roundingMode = java.math.RoundingMode.CEILING
            grouping = "group"; padder = Padder.none(); integerWidth = IntegerWidth.zeroFillTo(3)
            symbols = DecimalFormatSymbols(ULocale("en")); unitWidth = UnitWidth.FULL_NAME; unitDisplayCase = "genitive"
            sign = SignDisplay.ALWAYS; approximately = false; decimal = DecimalSeparatorDisplay.ALWAYS
            scale = Scale.powerOfTen(2); usage = "person-height"; affixProvider = PatternStringParser.parseToPatternInfo("0")
            rules = PluralRules.forLocale(ULocale("en")); threshold = 5L; loc = ULocale("en")
        }
        return j to k
    }
    private val fields = JProps::class.java.fields.filter { !Modifier.isStatic(it.modifiers) }
    @Test fun everyFieldFallbackEqualityAndShallowClone() {
        assertEquals(fields.map { it.name }.toSet(), MacroProps::class.java.fields.filter { !Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
        assertEquals(JProps().hashCode(), MacroProps().hashCode())
        assertEquals(20, fields.size)
        val (j, k) = pair()
        val jc = j.clone(); val kc = k.clone()
        for (field in fields) {
            val kf = MacroProps::class.java.getField(field.name)
            assertSame(field.get(j), field.get(jc), field.name)
            assertSame(kf.get(k), kf.get(kc), field.name)
            val jd = j.clone(); val kd = k.clone()
            field.set(jd, null); kf.set(kd, null)
            assertEquals(j == jd, k == kd, field.name)
            jd.fallback(j); kd.fallback(k)
            assertEquals(j == jd, k == kd, "fallback ${field.name}")
            assertEquals(field.get(jd) == null, kf.get(kd) == null)
        }
        assertEquals(j, jc); assertEquals(k, kc)
        j.fallback(null); k.fallback(null)
        assertFailsWith<NullPointerException> { JProps().fallback(null) }
        assertFailsWith<NullPointerException> { MacroProps().fallback(null) }
        assertFalse(java.io.Serializable::class.java.isAssignableFrom(MacroProps::class.java))
    }

    @Test fun hashSnapshotsAndEqualsReadsLaterFieldsAfterCallback() {
        fun scenario(source: Boolean): Pair<Int, List<String>> {
            val trace = mutableListOf<String>()
            val j = JProps(); val k = MacroProps()
            val symbols = object { override fun hashCode(): Int { trace.add("symbols"); return 7 } }
            val group = object { override fun hashCode(): Int {
                trace.add("grouping"); if (source) j.symbols = "changed" else k.symbols = "changed"; return 11
            } }
            j.grouping = group; j.symbols = symbols; k.grouping = group; k.symbols = symbols
            return (if (source) j.hashCode() else k.hashCode()) to trace
        }
        assertEquals(scenario(true), scenario(false))
        fun equalsScenario(source: Boolean): Pair<Boolean, List<String>> {
            val trace = mutableListOf<String>()
            val j = JProps(); val jo = JProps(); val k = MacroProps(); val ko = MacroProps()
            val lhs = object {
                override fun equals(other: Any?): Boolean {
                    trace.add("group equals")
                    if (source) j.symbols = "changed" else k.symbols = "changed"
                    return true
                }
            }
            j.grouping = lhs; jo.grouping = Any(); k.grouping = lhs; ko.grouping = Any()
            return (if (source) j == jo else k == ko) to trace
        }
        assertEquals(equalsScenario(true), equalsScenario(false))
    }

    @Test fun clonePreservesRuntimeSubtypeAndAdditionalFields() {
        class Derived : MacroProps() { val extra = Any(); var count = 8 }
        class JDerived : JProps() { val extra = Any(); var count = 8 }
        val j = JDerived(); val k = Derived()
        val jc = j.clone() as JDerived; val kc = k.clone() as Derived
        assertSame(j.extra, jc.extra); assertSame(k.extra, kc.extra)
        jc.count = 99; kc.count = 99
        assertEquals(j.count, k.count)
        assertNotSame(j, jc); assertNotSame(k, kc)
    }
}
