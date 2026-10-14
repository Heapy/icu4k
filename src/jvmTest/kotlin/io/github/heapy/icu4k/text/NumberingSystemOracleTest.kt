package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*
import com.ibm.icu.text.NumberingSystem as Original
import com.ibm.icu.util.ULocale as OriginalLocale

class NumberingSystemOracleTest {
    private fun state(value: Original?): Any? = value?.let { listOf(it.name, it.radix, it.description, it.isAlgorithmic) }
    private fun state(value: NumberingSystem?): Any? = value?.let { listOf(it.getName(), it.getRadix(), it.getDescription(), it.isAlgorithmic()) }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Exception) { listOf(failure.javaClass.name, if (failure is IllegalArgumentException) failure.message else null) }
    @Test fun everyResourceNameAndLocaleKeywordMatches() {
        assertContentEquals(Original.getAvailableNames(), NumberingSystem.getAvailableNames())
        assertEquals(state(Original.LATIN), state(NumberingSystem.LATIN))
        assertEquals(state(Original()), state(NumberingSystem()))
        for (name in Original.getAvailableNames().toList() + listOf("bogus", "LATN", "", null)) {
            assertEquals(outcome { state(Original.getInstanceByName(name)) }, outcome { state(NumberingSystem.getInstanceByName(name)) })
        }
        for (locale in listOf("root", "en", "fr", "ar", "ar_EG", "ar_MA", "ja", "es", "he", "th", "fa", "zh", "zh_TW", "bn", "my", "und", "zz")) {
            for (keyword in listOf(null, "default", "native", "traditional", "finance", "bogus", "latn", "arab", "roman", "mathbold", "LATN")) {
                val id = locale + (keyword?.let { "@numbers=$it" } ?: "")
                assertEquals(state(Original.getInstance(OriginalLocale(id))), state(NumberingSystem.getInstance(ULocale(id))), id)
            }
        }
        assertEquals(state(Original.getInstance(java.util.Locale.forLanguageTag("en-u-nu-arab"))), state(NumberingSystem.getInstance(java.util.Locale.forLanguageTag("en-u-nu-arab"))))
        assertEquals(outcome { state(Original.getInstance(null as OriginalLocale?)) }, outcome { state(NumberingSystem.getInstance(null as ULocale?)) })
    }
    @Test fun factoriesPreserveValidationAndNullAlgorithmicDescriptions() {
        val descriptions = listOf(null, "", "01", "0123456789", "aaaaaaaaaa", "abcdefghij", "😀".repeat(10), "\ud800".repeat(10), "\udc00".repeat(10), "\ud800\udc00".repeat(9))
        for (description in descriptions) {
            assertEquals(outcome { Original.isValidDigitString(description) }, outcome { NumberingSystem.isValidDigitString(description) })
            for (radix in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 9, 10, 11, Int.MAX_VALUE)) for (algorithmic in listOf(false, true)) {
                assertEquals(outcome { state(Original.getInstance(radix, algorithmic, description)) }, outcome { state(NumberingSystem.getInstance(radix, algorithmic, description)) })
            }
        }
    }
}
