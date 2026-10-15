// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import com.ibm.icu.impl.ICUData as JData
import com.ibm.icu.impl.ICUResourceBundle as JBundle
import com.ibm.icu.util.ULocale as JLocale
import com.ibm.icu.util.UResourceBundle as JResource
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.impl.ICUResourceLoader
import io.github.heapy.icu4k.util.ULocale
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

class RbnfRuleDataOracleTest {
    private val embedded = ICUResourceBundleReader.embeddedLoader
    private val ruleNames = listOf("SpelloutRules", "OrdinalRules", "DurationRules", "NumberingSystemRules")
    private val localizationNames = listOf("SpelloutLocalizations", "OrdinalLocalizations", "DurationLocalizations", "NumberingSystemLocalizations")
    @Test fun pinnedResourceBytesAndLocaleDiscoveryMatchPublishedIcu() {
        val names = arrayListOf<String>()
        ICUData.visitResources(ICUData.ICU_RBNF_BASE_NAME + "/") { names.add(it) }
        assertEquals(112, names.size)
        val manifest = File("data/manifest.json").readText()
        for (name in names) {
            val source = JData::class.java.getResourceAsStream("/" + JData.ICU_RBNF_BASE_NAME + "/" + name)!!.use { it.readBytes() }
            val target = ICUData.getBytes(ICUData.ICU_RBNF_BASE_NAME + "/" + name)!!
            assertContentEquals(source, target, name)
            val disk = File("data/icu78/rbnf/$name").readBytes()
            assertContentEquals(source, disk, name)
            val hash = MessageDigest.getInstance("SHA-256").digest(target).joinToString("") { "%02x".format(it) }
            assertTrue(manifest.contains(hash), name)
        }
        assertEquals(JBundle.getFullLocaleNameSet(JData.ICU_RBNF_BASE_NAME, JData::class.java.classLoader),
            ICUResourceBundle.getFullLocaleNameSet(ICUData.ICU_RBNF_BASE_NAME, embedded))
        assertEquals(JBundle.getAvailableLocaleNameSet(JData.ICU_RBNF_BASE_NAME, JData::class.java.classLoader),
            ICUResourceBundle.getAvailableLocaleNameSet(ICUData.ICU_RBNF_BASE_NAME, embedded))
    }
    @Test fun everyRuleGroupDisplayTableAliasAndFallbackMatchesSource() {
        val ids = JBundle.getFullLocaleNameSet(JData.ICU_RBNF_BASE_NAME, JData::class.java.classLoader).toList() +
            listOf("en_US", "en_AU", "zh_TW", "iw_IL", "in_ID", "sh_RS", "zz_ZZ", "en_US@numbers=roman", "zh_Hant_TW@numbers=hant")
        for (id in ids) for (format in 1..4) {
            val source = JResource.getBundleInstance(JData.ICU_RBNF_BASE_NAME, JLocale(id)) as JBundle
            val seen = arrayListOf<String>()
            val target = RbnfRuleData.load(ULocale(id), format, embedded) { seen.add(it.getName()) }
            assertEquals(source.uLocale.name, target.actualLocale.getName(), "$id/$format locale")
            assertEquals(listOf(source.uLocale.name), seen)
            val parts = try {
                val rules = source.getWithFallback("RBNFRules/" + ruleNames[format - 1])
                (0..<rules.size).map { rules.getString(it) }
            } catch (_: java.util.MissingResourceException) { emptyList() }
            assertEquals(parts.joinToString(""), target.description, "$id/$format description")
            val table = source.findTopLevel(localizationNames[format - 1])
            val expected = table?.let { (0..<it.size).map { index -> it[index].stringArray.toList() } }
            assertEquals(expected, target.localizations?.map { it.toList() }, "$id/$format localizations")
            val second = RbnfRuleData.load(ULocale(id), format, embedded)
            if (target.localizations != null && target.localizations.isNotEmpty()) {
                assertNotSame(target.localizations, second.localizations)
                assertNotSame(target.localizations[0], second.localizations!![0])
            }
        }
    }
    @Test fun invalidFormatBoundsMatchSourceConstructor() {
        for (format in listOf(Int.MIN_VALUE, -1, 0, 5, Int.MAX_VALUE)) {
            assertFailsWith<IndexOutOfBoundsException> { com.ibm.icu.text.RuleBasedNumberFormat(JLocale.ENGLISH, format) }
            assertFailsWith<IndexOutOfBoundsException> { RbnfRuleData.load(ULocale.ENGLISH, format, embedded) }
        }
    }
    @Test fun discoveryWithoutRuntimeResourceScanningUsesSourceIndexFallback() {
        val property = "com.ibm.icu.impl.ICUResourceBundle.skipRuntimeLocaleResourceScan"
        val previous = System.getProperty(property)
        System.setProperty(property, "true")
        try {
            val sourceLoader = object : ClassLoader(JData::class.java.classLoader) {}
            val targetLoader = object : ICUResourceLoader {
                override fun getData(fullName: String) = embedded.getData(fullName)
                override fun visitResources(directory: String, visitor: (String) -> Unit) = error("Runtime scan must be disabled")
            }
            assertEquals(JBundle.getFullLocaleNameSet(JData.ICU_RBNF_BASE_NAME, sourceLoader),
                ICUResourceBundle.getFullLocaleNameSet(ICUData.ICU_RBNF_BASE_NAME, targetLoader))
        } finally {
            if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
        }
    }
}
