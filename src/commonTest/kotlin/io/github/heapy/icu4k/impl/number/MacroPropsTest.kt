package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.number.*
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.MeasureUnit
import io.github.heapy.icu4k.util.RoundingMode
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class MacroPropsTest {
    private fun filled() = MacroProps().apply {
        notation = Notation.compactShort(); unit = MeasureUnit.METER; perUnit = MeasureUnit.SECOND
        precision = Precision.fixedFraction(2); roundingMode = RoundingMode.CEILING; grouping = Any()
        padder = Padder.NONE; integerWidth = IntegerWidth.zeroFillTo(3); symbols = DecimalFormatSymbols(ULocale("en"))
        unitWidth = UnitWidth.FULL_NAME; unitDisplayCase = "genitive"; sign = SignDisplay.ALWAYS
        approximately = false; decimal = DecimalSeparatorDisplay.ALWAYS; scale = Scale.powerOfTen(2)
        usage = "person-height"; affixProvider = PatternStringParser.parseToPatternInfo("0")
        rules = PluralRules.forLocale(ULocale("en")); threshold = 5; loc = ULocale("en")
    }
    @Test fun fallbackAndCloneRetainReferencesAndExcludeThreshold() {
        val source = filled()
        val target = MacroProps().apply { usage = "retained" }
        target.fallback(source)
        assertEquals("retained", target.usage); assertNull(target.threshold)
        target.usage = source.usage
        assertEquals(source, target); assertEquals(source.hashCode(), target.hashCode())
        val copy = source.clone()
        assertNotSame(source, copy); assertEquals(source, copy); assertEquals(5, copy.threshold)
        assertSame(source.notation, copy.notation); assertSame(source.unit, copy.unit); assertSame(source.perUnit, copy.perUnit)
        assertSame(source.precision, copy.precision); assertSame(source.grouping, copy.grouping)
        assertSame(source.padder, copy.padder); assertSame(source.integerWidth, copy.integerWidth); assertSame(source.symbols, copy.symbols)
        assertSame(source.affixProvider, copy.affixProvider); assertSame(source.rules, copy.rules); assertSame(source.loc, copy.loc)
        assertSame(source.scale, copy.scale)
        copy.threshold = 999; assertEquals(source, copy)
        copy.usage = "changed"; assertNotEquals(source, copy); assertEquals("person-height", source.usage)
        // The fallback receiver is fully populated: no dereference of null is required.
        source.fallback(null)
        assertFailsWith<NullPointerException> { MacroProps().fallback(null) }
    }
    @Test fun objectsEqualsSkipsIdenticalReferencesAndHashSnapshotsValues() {
        val bomb = object { override fun equals(other: Any?): Boolean = error("must not compare identical references") }
        val a = MacroProps().apply { grouping = bomb }; val b = MacroProps().apply { grouping = bomb }
        assertEquals(a, b)
        val trace = mutableListOf<String>()
        val props = MacroProps()
        props.symbols = object { override fun hashCode(): Int { trace.add("original symbols"); return 2 } }
        props.grouping = object { override fun hashCode(): Int { trace.add("grouping"); props.symbols = Any(); return 1 } }
        props.hashCode()
        assertEquals(listOf("grouping", "original symbols"), trace)
    }
}
