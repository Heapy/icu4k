package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.number.Precision
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class MutablePatternModifierTest {
    private fun modifier(pattern: String, width: UnitWidth = UnitWidth.SHORT): MutablePatternModifier = MutablePatternModifier(false).apply {
        setPatternInfo(PropertiesAffixPatternProvider.forProperties(PatternStringParser.parseToProperties(pattern)), null)
        setPatternAttributes(SignDisplay.ALWAYS, false, false)
        setSymbols(DecimalFormatSymbols(ULocale("en")), Currency.getInstance("USD"), width,
            if (needsPlurals()) PluralRules.forLocale(ULocale("en")) else null)
    }
    @Test fun mutableAndImmutableIntegrateWithRoundingPipeline() {
        val mutable = modifier("0%;(0%)")
        val immutable = mutable.createImmutable()
        for (stage in listOf(mutable.addToChain(MicroProps(true).apply { rounder = Precision.fixedFraction(1) }),
            immutable.addToChain(MicroProps(true).apply { rounder = Precision.fixedFraction(1) }))) {
            val q = DecimalQuantity_DualStorageBCD(-1.25)
            val micros = stage.processQuantity(q)!!
            assertEquals("-1.2", q.toPlainString())
            val result = FormattedStringBuilder().apply { append("1.2", NumberFormatField.INTEGER) }
            assertEquals(3, micros.modMiddle!!.apply(result, 0, 3))
            assertEquals("(1.2%)", result.toString())
        }
    }
    @Test fun pluralCurrencyAndSpacingUsePublicData() {
        val mutable = modifier("0 ¤¤¤")
        val stage = mutable.createImmutable().addToChain(MicroProps(true).apply { rounder = Precision.integer() })
        val q = DecimalQuantity_DualStorageBCD(1.04)
        val micros = stage.processQuantity(q)
        val output = FormattedStringBuilder().apply { append("1", NumberFormatField.INTEGER) }
        micros.modMiddle!!.apply(output, 0, 1)
        assertEquals("+1 US dollar", output.toString())
        val currency = modifier("¤0", UnitWidth.ISO_CODE)
        currency.setNumberProperties(Modifier.Signum.POS, null)
        val result = FormattedStringBuilder().apply { append("1", NumberFormatField.INTEGER) }
        assertEquals(4, currency.apply(result, 0, 1)) // Sign and currency only: spacing is excluded.
        assertEquals("+USD 1", result.toString())
        assertNull(result.fieldAt(4))
    }
}
