package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class LongNameDataTest {
    @Test fun patternsAndPluralFallback() {
        val data = arrayOfNulls<String>(LongNameData.ARRAY_LENGTH)
        LongNameData.getMeasureData(ULocale.ENGLISH, MeasureUnit.METER, UnitWidth.FULL_NAME, "", data)
        assertEquals("{0} meters", LongNameData.getWithPlural(data, StandardPlural.OTHER))
        assertEquals("meters", data[LongNameData.DNAM_INDEX])
        assertEquals("{0} meters", LongNameData.getWithPlural(data, StandardPlural.FEW))
        assertFailsWith<ICUException> { LongNameData.getWithPlural(arrayOfNulls(LongNameData.ARRAY_LENGTH), StandardPlural.ONE) }
    }
    @Test fun grammarAndPatternExtraction() {
        val rules = LongNameData.DerivedComponents(ULocale("de_CH"), "case", "per")
        assertEquals("accusative", rules.value0("accusative"))
        assertEquals("accusative", rules.value1("accusative"))
        val extracted = LongNameData.extractCorePattern("{0}\u00a0m")
        assertEquals("m", extracted.coreUnit)
        assertEquals('\u00a0', extracted.joinerChar)
        assertEquals(LongNameData.PlaceholderPosition.BEGINNING, extracted.placeholderPosition)
        assertEquals("\tm", LongNameData.extractCorePattern("{0}\tm").coreUnit)
    }
}
