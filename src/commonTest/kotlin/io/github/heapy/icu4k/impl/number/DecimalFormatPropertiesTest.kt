package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.text.CurrencyPluralInfo
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.Currency
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class DecimalFormatPropertiesTest {
    @Test fun rawSettingsAndShallowCopiesRetainReferencesWithoutValidation() {
        val props = DecimalFormatProperties()
        assertEquals("<Properties>", props.toString())
        assertTrue(props.getGroupingUsed()); assertEquals(-1, props.getMaximumFractionDigits())
        assertSame(props, props.setMinimumFractionDigits(Int.MIN_VALUE))
        assertSame(props, props.setMaximumFractionDigits(-999))
        assertEquals(Int.MIN_VALUE, props.getMinimumFractionDigits())
        val map = mutableMapOf("1000" to mutableMapOf("other" to "0K"))
        props.setCompactCustomData(map)
        val copy = props.clone()
        assertSame(map, copy.getCompactCustomData())
        map["1000"]!!["other"] = "0 thousand"
        assertEquals("0 thousand", copy.getCompactCustomData()!!["1000"]!!["other"])
        assertEquals(props, copy)
        val brokenRules = CurrencyPluralInfo(ULocale("en")).also { it.setPluralRules("invalid") }
        props.setCurrencyPluralInfo(brokenRules)
        assertEquals(props, props.clone())
        assertSame(props, props.copyFrom(props))
        assertSame(props, props.clear())
        assertEquals(DecimalFormatProperties(), props)
    }
    @Test fun patternProjectionPreservesUnrelatedFieldsAndClearsEmptyPattern() {
        val currency = Currency.getInstance("USD")
        val props = DecimalFormatProperties().setCurrency(currency).setParseIntegerOnly(true).setPositivePrefix("literal")
        PatternStringParserCore.parseToExistingProperties("#,##0.05%", props)
        assertSame(currency, props.getCurrency()); assertTrue(props.getParseIntegerOnly())
        assertEquals("literal", props.getPositivePrefix())
        assertEquals(BigDecimal("0.05"), props.getRoundingIncrement())
        assertEquals(2, props.getMagnitudeMultiplier())
        PatternStringParserCore.parseToExistingProperties("@@##", props)
        assertEquals(2, props.getMinimumSignificantDigits()); assertEquals(4, props.getMaximumSignificantDigits())
        assertNull(props.getRoundingIncrement())
        PatternStringParserCore.parseToExistingProperties(null, props)
        assertNull(props.getCurrency()); assertFalse(props.getParseIntegerOnly()); assertNull(props.getPositivePrefix())
        assertEquals(DecimalFormatProperties(), props)
        props.toStringBare(null)
    }
}
