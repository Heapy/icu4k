package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class LocaleDisplayNameDataTableTest {
    @Test fun regionAndLanguageLookupAndSubstitution() {
        val lang = ResourceLocaleDisplayNameDataTable(ICUData.ICU_LANG_BASE_NAME, ULocale("en"), false)
        val region = ResourceLocaleDisplayNameDataTable(ICUData.ICU_REGION_BASE_NAME, ULocale("en"), false)
        assertEquals("German", lang.get("Languages", "de"))
        assertEquals("United States", region.get("Countries", "US"))
        assertEquals("missing", lang.get("Languages", "missing"))
        assertNull(ResourceLocaleDisplayNameDataTable(ICUData.ICU_LANG_BASE_NAME, ULocale("en"), true).get("Languages", "missing"))
        assertEquals("code", LocaleDisplayNameDataTable(false).get("table", "code"))
        assertNull(LocaleDisplayNameDataTable(true).get("table", "code"))
        assertEquals(ULocale.ROOT, LocaleDisplayNameDataTable(false).getLocale())
        for (name in listOf("root", "pool", "en")) assertTrue(ICUData.exists("${ICUData.ICU_REGION_BASE_NAME}/$name.res"))
    }
    @Test fun malformedLookupReturnsDefault() {
        assertEquals("fallback", ICUResourceTableAccess.getTableString(null as ICUResourceBundle?, "Languages", null, "en", "fallback"))
        val numeric = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "currencyNumericCodes")
        assertEquals("fallback", ICUResourceTableAccess.getTableString(numeric, "codeMap", null, "USD", "fallback"))
    }
}
