package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.LocaleDisplayNamesImpl
import io.github.heapy.icu4k.util.ULocale
import java.util.Locale
import kotlin.test.*

class LocaleDisplayNamesFactoryOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Exception) { e.javaClass.simpleName }
    @Test fun publicFactoryResultsCacheIdentitiesAndFallbackMatch() {
        var previousJ: com.ibm.icu.text.LocaleDisplayNames? = null
        var previousK: LocaleDisplayNames? = null
        val steps = listOf("en" to emptyList(), "en" to emptyList(), "en" to listOf("LENGTH_SHORT", "LENGTH_FULL"), "fr" to listOf("DIALECT_NAMES"), "en" to emptyList(), null to listOf("CAPITALIZATION_FOR_STANDALONE"), "en" to listOf(null), "en" to listOf("NO_SUBSTITUTE"))
        for ((id, options) in steps) {
            val jo = options.map { it?.let(com.ibm.icu.text.DisplayContext::valueOf) }.toTypedArray()
            val ko = options.map { it?.let(DisplayContext::valueOf) }.toTypedArray()
            val j = com.ibm.icu.text.LocaleDisplayNames.getInstance(id?.let { com.ibm.icu.util.ULocale(it) }, *jo)
            val k = LocaleDisplayNames.getInstance(id?.let(::ULocale), *ko)
            assertEquals(previousJ === j, previousK === k, "$id/$options/identity")
            assertEquals(j.locale?.name, k.getLocale()?.getName())
            assertEquals(outcome { j.dialectHandling?.name }, outcome { k.getDialectHandling()?.name })
            for (type in DisplayContext.Type.entries) assertEquals(outcome { j.getContext(com.ibm.icu.text.DisplayContext.Type.valueOf(type.name))?.name }, outcome { k.getContext(type)?.name })
            for (input in listOf("en_US", "zh_Hant_TW", "en@foo=bar")) assertEquals(j.localeDisplayName(input), k.localeDisplayName(input))
            assertEquals(outcome { j.languageDisplayName(null) }, outcome { k.languageDisplayName(null) })
            previousJ = j; previousK = k
        }
    }
    @Test fun javaLocaleApiAndVirtualUiContextMatch() {
        assertEquals(com.ibm.icu.text.LocaleDisplayNames.getInstance(Locale.FRENCH).localeDisplayName(Locale.US), LocaleDisplayNames.getInstance(Locale.FRENCH).localeDisplayName(Locale.US))
        val j = object : com.ibm.icu.impl.LocaleDisplayNamesImpl(com.ibm.icu.util.ULocale("fr"), com.ibm.icu.text.LocaleDisplayNames.DialectHandling.STANDARD_NAMES) {
            override fun getContext(type: com.ibm.icu.text.DisplayContext.Type?): com.ibm.icu.text.DisplayContext? = if (type == com.ibm.icu.text.DisplayContext.Type.CAPITALIZATION) com.ibm.icu.text.DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU else super.getContext(type)
        }
        val k = object : LocaleDisplayNamesImpl(ULocale("fr"), LocaleDisplayNames.DialectHandling.STANDARD_NAMES) {
            override fun getContext(type: DisplayContext.Type?): DisplayContext? = if (type == DisplayContext.Type.CAPITALIZATION) DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU else super.getContext(type)
        }
        val cmp = Comparator<Any?> { a, b -> (a as String).compareTo(b as String) }
        assertEquals(j.getUiList(setOf(com.ibm.icu.util.ULocale("en")), false, cmp).map { it.nameInDisplayLocale }, k.getUiList(setOf(ULocale("en")), false, cmp).map { it.nameInDisplayLocale })
    }
    @Test fun jvmProviderMethodResolutionAndDataTableFallback() {
        val provider = createLocaleDisplayNamesFactory(DialectOnlyDisplayNamesProvider::class.java.name)
        assertNotNull(provider.get(ULocale("en"), LocaleDisplayNames.DialectHandling.STANDARD_NAMES))
        assertNull(provider.get(ULocale("en"), emptyArray()))
        assertNull(createLocaleDisplayNamesFactory(ThrowingDisplayNamesProvider::class.java.name).get(ULocale("en"), LocaleDisplayNames.DialectHandling.STANDARD_NAMES))
        assertNull(createLocaleDisplayNamesFactory("missing.Provider").get(ULocale("en"), emptyArray()))
        val fallback = LocaleDisplayNamesImpl.DataTables.load("missing.TableProvider")
        assertEquals(ULocale.ROOT, fallback.get(ULocale("fr"), false).getLocale())
        assertEquals("xx", fallback.get(ULocale("fr"), false).get("Languages", "xx"))
        assertNull(fallback.get(ULocale("fr"), true).get("Languages", "xx"))
    }
}
class DialectOnlyDisplayNamesProvider {
    companion object { @JvmStatic fun getInstance(locale: ULocale?, dialect: LocaleDisplayNames.DialectHandling?): LocaleDisplayNames = LocaleDisplayNames.getInstance(locale, dialect) }
}
class ThrowingDisplayNamesProvider {
    companion object { @JvmStatic fun getInstance(locale: ULocale?, dialect: LocaleDisplayNames.DialectHandling?): LocaleDisplayNames = throw IllegalStateException("provider") }
}
