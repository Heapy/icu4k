package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class CustomSymbolCurrencyTest {
    @Test fun customNamesKeepMetadataAndVirtualDispatch() {
        val custom = CustomSymbolCurrency("USD", "dollars!", "ALT")
        assertEquals("USD", custom.toString()); assertEquals("ALT", custom.getCurrencyCode())
        assertEquals(840, custom.getNumericCode()); assertEquals(2, custom.getDefaultFractionDigits())
        assertEquals("dollars!", custom.getName(null, Currency.SYMBOL_NAME, "one", null))
        val choice = booleanArrayOf(true)
        assertEquals("dollars!", custom.getName(null, Currency.SYMBOL_NAME, choice)); assertFalse(choice[0])
        assertFailsWith<IndexOutOfBoundsException> { custom.getName(null, Currency.SYMBOL_NAME, booleanArrayOf()) }
        assertEquals(CustomSymbolCurrency("USD", "dollars!", "ALT"), custom)
    }
    @Test fun resolveSnapshotsAndReturnsUnchangedCurrencyWhenApplicable() {
        val locale = ULocale("en_US")
        val symbols = DecimalFormatSymbols(locale)
        val usd = Currency.getInstance("USD")
        assertSame(usd, CustomSymbolCurrency.resolve(usd, null, symbols))
        val eur = Currency.getInstance("EUR")
        assertSame(eur, CustomSymbolCurrency.resolve(eur, null, symbols))
        symbols.setCurrencySymbol("custom"); symbols.setInternationalCurrencySymbol("ALT")
        val custom = CustomSymbolCurrency.resolve(null, null, symbols)
        assertTrue(custom is CustomSymbolCurrency)
        symbols.setCurrencySymbol("changed")
        assertEquals("custom", custom.getSymbol(locale)); assertEquals("ALT", custom.getCurrencyCode())
    }
}
