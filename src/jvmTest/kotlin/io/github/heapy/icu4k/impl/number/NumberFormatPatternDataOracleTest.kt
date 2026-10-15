@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.text.NumberFormat as JFormat
import com.ibm.icu.text.DecimalFormatSymbols as JSymbols
import com.ibm.icu.util.ULocale as JLocale
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.text.NumberFormat
import kotlin.test.*

class NumberFormatPatternDataOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) { e.javaClass.simpleName }
    @Test fun everyStyleAndLocaleMatchSource() {
        for (locale in JSymbols.getAvailableULocales()) for (style in 0..9) {
            assertEquals(JFormat.getPatternForStyle(locale, style), NumberFormat.getPatternForStyle(ULocale(locale.name), style), "$locale/$style")
        }
        for (locale in listOf("", "en_US", "ar", "fa", "hi", "zz_ZZ", "en_US@cf=account", "en_US@cf=standard", "en_US@numbers=arab;cf=account")) {
            for (style in listOf(-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, Int.MAX_VALUE)) for (ns in listOf(null, "latn", "arab", "roman", "mathbold", "not-a-system")) {
                assertEquals(outcome { JFormat.getPatternForStyleAndNumberingSystem(JLocale(locale), ns, style) },
                    outcome { NumberFormat.getPatternForStyleAndNumberingSystem(ULocale(locale), ns, style) }, "$locale/$ns/$style")
            }
        }
        for (style in listOf(-1, 0, 1, 7, 10)) {
            assertEquals(outcome { JFormat.getPatternForStyle(null, style) }, outcome { NumberFormat.getPatternForStyle(null, style) })
            assertEquals(outcome { JFormat.getPatternForStyleAndNumberingSystem(null, null, style) }, outcome { NumberFormat.getPatternForStyleAndNumberingSystem(null, null, style) })
        }
    }
}
