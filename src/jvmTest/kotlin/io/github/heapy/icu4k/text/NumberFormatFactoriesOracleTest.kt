// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import com.ibm.icu.text.NumberFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale

class NumberFormatFactoriesOracleTest {
    @Test fun publicStylesAndNumberingSystems() {
        for (id in listOf("en_US", "fr_FR", "de_CH@currency=CHF", "ar_EG", "en@numbers=roman", "zh@numbers=hans")) {
            for (style in 0..9) {
                val expected = Source.getInstance(SourceLocale(id), style)
                val actual = NumberFormat.getInstance(ULocale(id), style)
                assertEquals(expected.javaClass.simpleName, actual.javaClass.simpleName, "$id/$style")
                for (number in listOf(-1234.56, 0.0, 1.0, 2.5, 12345.67))
                    assertEquals(expected.format(number), actual.format(number), "$id/$style/$number")
                assertEquals(expected.getLocale(SourceLocale.ACTUAL_LOCALE)?.name, actual.getLocale(ULocale.ACTUAL_LOCALE)?.getName(), "$id/$style")
                assertEquals(expected.currency?.currencyCode, actual.getCurrency()?.getCurrencyCode(), "$id/$style")
            }
        }
        assertEquals(Source.getNumberInstance(java.util.Locale.US).format(1234L), NumberFormat.getNumberInstance(java.util.Locale.US).format(1234L))
        assertTrue(NumberFormat.getAvailableLocales().isNotEmpty())
    }
}
