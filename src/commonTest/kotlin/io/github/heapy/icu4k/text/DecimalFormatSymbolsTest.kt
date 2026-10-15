package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DecimalFormatSymbolsTest {
    @Test fun localeDigitsSymbolsAndCurrency() {
        val latin = DecimalFormatSymbols(ULocale("en_US"))
        assertEquals('.', latin.getDecimalSeparator()); assertEquals(',', latin.getGroupingSeparator())
        assertEquals("USD", latin.getCurrency()?.getCurrencyCode())
        val arabic = DecimalFormatSymbols.forNumberingSystem(ULocale("en_US"), NumberingSystem.getInstanceByName("arab"))
        assertEquals(0x660, arabic.getCodePointZero()); assertEquals('٠', arabic.getZeroDigit())
        assertEquals("٫", arabic.getDecimalSeparatorString())
        val supplementary = DecimalFormatSymbols.forNumberingSystem(ULocale("en"), NumberingSystem.getInstanceByName("mathbold"))
        assertEquals(0x1d7ce, supplementary.getCodePointZero())
        assertEquals('0', supplementary.getZeroDigit())
        assertEquals(2, supplementary.getDigitStrings()[0].length)
        latin.setCurrency(Currency.getInstance("EUR"))
        assertEquals("EUR", latin.getInternationalCurrencySymbol()); assertEquals("€", latin.getCurrencySymbol())
    }
    @Test fun copiesAndSetterFallbacksRetainSourceBehavior() {
        val symbols = DecimalFormatSymbols(ULocale("en_US"))
        val copy = symbols.clone()
        assertSame(symbols.getDigitStringsLocal(), copy.getDigitStringsLocal())
        symbols.getDigitStringsLocal()[2] = "two"
        assertEquals("two", copy.getDigitStringsLocal()[2])
        symbols.setZeroDigit('A')
        assertEquals("two", copy.getDigitStringsLocal()[2])
        assertEquals("C", symbols.getDigitStringsLocal()[2])
        symbols.setGroupingSeparatorString("group")
        assertEquals(',', symbols.getGroupingSeparator()); assertEquals("group", symbols.getGroupingSeparatorString())
        symbols.setPatternForCurrencySpacing(2, true, "insert")
        assertNotEquals("insert", copy.getPatternForCurrencySpacing(2, true))
        assertFailsWith<NullPointerException> { symbols.setPlusSignString(null) }
        assertFailsWith<IllegalArgumentException> { symbols.setDigitStrings(arrayOf("0")) }
        val first = DecimalFormatSymbols(ULocale("en")); val second = first.clone()
        second.setSignificantDigit('!'); second.setGroupingSeparatorString("long")
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
