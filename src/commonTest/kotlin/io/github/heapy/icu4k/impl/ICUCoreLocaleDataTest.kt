package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class ICUCoreLocaleDataTest {
    @Test fun completeMainCatalogAndPersianPatternsExistWithoutClasspathOverlay() {
        val names = HashSet<String>()
        ICUResourceBundleReader.embeddedLoader.visitResources(ICUData.ICU_BASE_NAME + "/") { if (it.endsWith(".res")) names.add(it) }
        assertEquals(982,names.size)
        for (name in listOf("root.res","pool.res","res_index.res","fa.res","en_US.res","zh_Hant_TW.res","sr_Latn.res","ff_Adlm.res")) {
            assertTrue(name in names,name); assertNotNull(ICUData.getBytes(name),name)
        }
        val bundle = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME,"fa_IR",ICUResourceBundleReader.embeddedLoader,false)
        val patterns = bundle.getWithFallback("calendar/gregorian/DateTimePatterns")
        assertEquals("fa",patterns.getULocale().getLanguage())
        assertEquals("H:mm:ss (zzzz)",patterns.getString(0))
        val first = ICUData.getBytes("fa.res")!!
        val original = first[0]; first[0] = (original.toInt() xor 0xff).toByte()
        assertEquals(original,ICUData.getBytes("fa.res")!![0])
        assertEquals("fa_Arab_IR",ULocale.addLikelySubtags(ULocale("fa")).toString())
    }
}
