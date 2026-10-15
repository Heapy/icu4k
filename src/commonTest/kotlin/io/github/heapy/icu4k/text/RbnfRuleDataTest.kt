// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RbnfRuleDataTest {
    @Test fun embeddedDirectoryAndEveryAvailableLocaleLoadWithoutHostData() {
        val files = arrayListOf<String>()
        ICUData.visitResources(ICUData.ICU_RBNF_BASE_NAME + "/") { files.add(it) }
        assertEquals(112, files.size)
        assertTrue("root.res" in files); assertTrue("res_index.res" in files)
        for (file in files) {
            val full = ICUData.ICU_RBNF_BASE_NAME + "/" + file
            val first = ICUData.getBytes(full)!!
            val original = first[0]
            first[0] = (first[0].toInt() xor 255).toByte()
            assertEquals(original, ICUData.getBytes(full)!![0], file)
        }
        val available = ICUResourceBundle.getFullLocaleNameSet(ICUData.ICU_RBNF_BASE_NAME, ICUResourceBundleReader.embeddedLoader)
        assertEquals(111, available.size) // Every resource except res_index; root is represented by the empty ID.
        assertTrue("" in available); assertTrue("en" in available); assertTrue("zh_Hant" in available)
        for (id in available) for (format in 1..4) {
            val events = arrayListOf<ULocale>()
            val data = RbnfRuleData.load(ULocale(id), format, ICUResourceBundleReader.embeddedLoader) { events.add(it) }
            assertEquals(listOf(data.actualLocale), events, "$id/$format")
        }
    }
    @Test fun predefinedDescriptionsAndFallback() {
        val english = RbnfRuleData.load(ULocale.ENGLISH, 1)
        assertTrue(english.description.contains("%spellout-numbering:"))
        assertTrue(english.description.contains("twenty"))
        val ordinal = RbnfRuleData.load(ULocale.ENGLISH, 2)
        assertTrue(ordinal.description.contains("%digits-ordinal:"))
        val numbering = RbnfRuleData.load(ULocale.ROOT, 4)
        assertTrue(numbering.description.contains("%roman-upper:"))
        assertTrue(numbering.description.contains("%hebrew:"))
        val traditional = RbnfRuleData.load(ULocale("zh_TW"), 1)
        val canonical = RbnfRuleData.load(ULocale("zh_Hant_TW"), 1)
        assertEquals(canonical.description, traditional.description)
        val enUS = RbnfRuleData.load(ULocale("en_US"), 1)
        assertEquals("en", enUS.actualLocale.getName())
        assertEquals(english.description, enUS.description)
        // Root supplies numeric fallback rules, rather than English spelled-out words.
        val root = RbnfRuleData.load(ULocale.ROOT, 1)
        assertTrue(root.description.contains("%spellout-cardinal:"))
        assertTrue(root.description.contains("0: =#,##0.#=;"))
        assertFalse(root.description.contains("twenty"))
    }
    @Test fun callbackPrecedesInvalidFormatFailure() {
        val seen = arrayListOf<String>()
        for (format in listOf(Int.MIN_VALUE, -1, 0, 5, Int.MAX_VALUE)) {
            seen.clear()
            assertFailsWith<IndexOutOfBoundsException> { RbnfRuleData.load(ULocale.ENGLISH, format) { seen.add(it.getName()) } }
            assertEquals(listOf("en"), seen)
        }
        assertEquals("stop", assertFailsWith<IllegalStateException> {
            RbnfRuleData.load(ULocale.ENGLISH, 0) { throw IllegalStateException("stop") }
        }.message)
    }
}
