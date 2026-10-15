package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.CurrencyPluralInfo
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class PropertiesAffixPatternProviderTest {
    @Test fun independentLiteralOverrideAndSnapshot() {
        val properties = DecimalFormatProperties().setPositivePrefixPattern("¤")
            .setPositiveSuffixPattern("%").setPositivePrefix("+literal")
        val provider = PropertiesAffixPatternProvider.forProperties(properties)
        assertEquals("'+'literal", provider.getString(0x100))
        assertEquals("-¤", provider.getString(0x300))
        assertTrue(provider.hasCurrencySign())
        assertFalse(provider.positiveHasPlusSign())
        assertTrue(provider.negativeHasMinusSign())
        properties.clear()
        assertEquals("%", provider.getString(0))
    }
    @Test fun pluralDispatchAndOtherMetadata() {
        val info = CurrencyPluralInfo(ULocale("en"))
        info.setCurrencyPluralPattern("one", "+0%")
        info.setCurrencyPluralPattern("other", "0¤¤¤")
        val provider = PropertiesAffixPatternProvider.forProperties(DecimalFormatProperties().setCurrencyPluralInfo(info))
        assertIs<CurrencyPluralInfoAffixProvider>(provider)
        assertEquals("+", provider.getString(0x101))
        assertEquals("%", provider.getString(1))
        assertEquals("¤¤¤", provider.getString(5))
        assertFalse(provider.positiveHasPlusSign())
        assertTrue(provider.hasCurrencySign())
    }
}
