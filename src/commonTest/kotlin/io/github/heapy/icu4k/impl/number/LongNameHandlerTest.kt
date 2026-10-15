package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class LongNameHandlerTest {
    @Test fun compoundAndCurrencyPipeline() {
        val locale = ULocale.ENGLISH
        val parent = MicroProps(true).also { it.rounder = Precision.integer() }
        val handler = LongNameHandler.forMeasureUnit(locale, MeasureUnit.forIdentifier("meter-second"), UnitWidth.FULL_NAME, "", PluralRules.forLocale(locale), parent)
        val micros = handler.processQuantity(DecimalQuantity_DualStorageBCD(2))
        val buffer = FormattedStringBuilder(); buffer.append("2", null)
        micros.modOuter!!.apply(buffer, 0, 1)
        assertEquals("2 meter-seconds", buffer.toString())
        assertSame(handler, micros.modOuter!!.getParameters()!!.obj)
        assertEquals(StandardPlural.OTHER, micros.modOuter!!.getParameters()!!.plural)
        val currency = LongNameHandler.forCurrencyLongNames(locale, Currency.getInstance("USD"), PluralRules.forLocale(locale), parent)
        val out = currency.processQuantity(DecimalQuantity_DualStorageBCD(1))
        val amount = FormattedStringBuilder(); amount.append("1", null); out.modOuter!!.apply(amount, 0, 1)
        assertEquals("1 US dollar", amount.toString())
    }
    @Test fun grammaticalGenderAndParentlessPreservation() {
        val locale = ULocale("de")
        val handler = LongNameHandler.forMeasureUnit(locale, MeasureUnit.METER, UnitWidth.FULL_NAME, "dative", PluralRules.forLocale(locale), MicroProps(true).also { it.rounder = Precision.unlimited() })
        assertEquals("masculine", handler.processQuantity(DecimalQuantity_DualStorageBCD(1)).gender)
        val micros = MicroProps(true).also { it.rounder = Precision.unlimited(); it.gender = "existing" }
        assertSame(micros, handler.processQuantityWithMicros(DecimalQuantity_DualStorageBCD(1), micros))
        assertEquals("existing", micros.gender)
        assertFailsWith<UnsupportedOperationException> { LongNameHandler.forMeasureUnit(ULocale.ENGLISH, MeasureUnit.forIdentifier("pow4-meter"), UnitWidth.FULL_NAME, "", null, null) }
    }
}
