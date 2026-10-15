package io.github.heapy.icu4k.number

import io.github.heapy.icu4k.impl.number.DecimalQuantity_DualStorageBCD
import io.github.heapy.icu4k.impl.number.RoundingUtils
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.Currency
import kotlin.test.*

class PrecisionTest {
    private fun render(policy: Precision, value: String): String = DecimalQuantity_DualStorageBCD(BigDecimal(value)).also { policy.apply(it) }.toPlainString()
    @Test fun fractionSignificantAndIncrementPolicies() {
        assertEquals("1.24", render(Precision.fixedFraction(2), "1.235"))
        assertEquals("1.20", render(Precision.fixedSignificantDigits(3), "1.2"))
        assertEquals("1.050", render(Precision.increment(BigDecimal("0.050")), "1.03"))
        assertEquals("1.25", render(Precision.increment(BigDecimal("0.25")), "1.13"))
        assertEquals("1.23", render(Precision.maxFraction(1).withSignificantDigits(1, 3, RoundingPriority.RELAXED), "1.234"))
        assertEquals("1.2", render(Precision.maxFraction(1).withSignificantDigits(1, 3, RoundingPriority.STRICT), "1.234"))
        assertEquals("1", render(Precision.fixedFraction(2).trailingZeroDisplay(TrailingZeroDisplay.HIDE_IF_WHOLE), "1"))
        assertEquals("1.20", render(Precision.fixedFraction(2).trailingZeroDisplay(TrailingZeroDisplay.HIDE_IF_WHOLE), "1.2"))
    }
    @Test fun currencyAndSafePluralDoNotMutateQuantity() {
        val policy = Precision.currency(Currency.CurrencyUsage.CASH).withCurrency(Currency.getInstance("CHF"))
        assertEquals("1.05", render(policy, "1.03"))
        val q = DecimalQuantity_DualStorageBCD(BigDecimal("1.04"))
        val rules = PluralRules.parseDescription("one: n = 1")
        assertEquals("ONE", RoundingUtils.getPluralSafe(Precision.integer(), rules, q).name)
        assertEquals("1.04", q.toPlainString())
        assertEquals("OTHER", RoundingUtils.getPluralSafe(null, rules, q).name)
        assertSame(Precision.integer(), Precision.fixedFraction(0))
        assertNotSame(Precision.integer(), Precision.integer().trailingZeroDisplay(null))
        assertFailsWith<IllegalArgumentException> { Precision.increment(null) }
        assertFailsWith<AssertionError> { Precision.BOGUS_PRECISION.apply(q) }
    }
}
