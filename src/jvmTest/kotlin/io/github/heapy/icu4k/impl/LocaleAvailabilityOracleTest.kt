package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import java.io.InputStream
import java.net.URLClassLoader
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class LocaleAvailabilityOracleTest {
    private class Loader(private val files: Map<String, ByteArray>, parent: ClassLoader = com.ibm.icu.impl.ICUResourceBundle::class.java.classLoader) : ClassLoader(parent) {
        override fun getResourceAsStream(name: String): InputStream? = files[name]?.inputStream() ?: super.getResourceAsStream(name)
    }
    @Test fun originalCoreAndBreakIndexesAndCustomKeywordsMatch() {
        for (base in listOf(ICUData.ICU_BASE_NAME, ICUData.ICU_BRKITR_BASE_NAME)) {
            for (type in listOf(ULocale.AvailableType.DEFAULT, ULocale.AvailableType.ONLY_LEGACY_ALIASES)) {
                val jtype = com.ibm.icu.util.ULocale.AvailableType.valueOf(type.name)
                assertEquals(com.ibm.icu.impl.ICUResourceBundle.getAvailableULocales(base, com.ibm.icu.impl.ICUResourceBundle.ICU_DATA_CLASS_LOADER, jtype)?.map { it.name },
                    ICUResourceBundle.getAvailableULocales(base, type = type)?.map { it.getName() })
            }
            assertEquals(com.ibm.icu.impl.ICUResourceBundle.getAvailableLocaleNameSet(base, com.ibm.icu.impl.ICUResourceBundle.ICU_DATA_CLASS_LOADER),
                ICUResourceBundle.getAvailableLocaleNameSet(base))
            val names = ICUResourceBundle.getAvailableLocaleNameSet(base) as MutableSet<String>
            assertFailsWith<UnsupportedOperationException> { names.removeIf { error("must not run") } }
        }
        val base = "icu4k-test/availability-oracle"
        val files = LocaleAvailabilityTest.keywordFiles()
        val jLoader = Loader(files.mapKeys { "$base/${it.key}.res" }); val kLoader = BundleFixture.loader(files)
        val saved = Locale.getDefault(); Locale.setDefault(Locale.US)
        try {
            // Both upstream caches intentionally ignore loader identity. Prime the original reader cache for its fixed-loader keyword API.
            for (id in files.keys) com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(base, id, jLoader, true)
            com.ibm.icu.impl.ICUResourceBundle.getAvailableULocales(base, jLoader)
            assertEquals(com.ibm.icu.impl.ICUResourceBundle.getKeywordValues(base, "collations").toSet(),
                ICUResourceBundle.getKeywordValues(base, "collations", kLoader).toSet())
            assertEquals(com.ibm.icu.impl.ICUResourceBundle.getKeywordValues(base, null).toSet(),
                ICUResourceBundle.getKeywordValues(base, null, kLoader).toSet())
            assertEquals(com.ibm.icu.impl.ICUResourceBundle.getKeywordValues(base, "missing").toSet(),
                ICUResourceBundle.getKeywordValues(base, "missing", kLoader).toSet())
        } finally { Locale.setDefault(saved) }
    }
    @Test fun releaseWithAliasesSelectionRetainsOriginalNullResult() {
        val source = com.ibm.icu.impl.ICUResourceBundle::class.java.protectionDomain.codeSource.location
        URLClassLoader(arrayOf(source), null).use { isolated ->
            isolated.setDefaultAssertionStatus(false)
            val cls = isolated.loadClass("com.ibm.icu.impl.ICUResourceBundle")
            val type = isolated.loadClass("com.ibm.icu.util.ULocale\$AvailableType")
            val mode = type.enumConstants.single { (it as Enum<*>).name == "WITH_LEGACY_ALIASES" }
            val base = "icu4k-test/availability-release"
            val files = LocaleAvailabilityTest.keywordFiles()
            val loader = Loader(files.mapKeys { "$base/${it.key}.res" }, isolated)
            val method = cls.getMethod("getAvailableULocales", String::class.java, ClassLoader::class.java, type)
            assertNull(method.invoke(null, base, loader, mode))
            assertNull(ICUResourceBundle.getAvailableULocales(base, BundleFixture.loader(files), ULocale.AvailableType.WITH_LEGACY_ALIASES))
        }
    }
    @Test fun concurrentIndexInitializationRunsOnceAndPublishesOneArray() {
        val base = "icu4k-test/availability-concurrent"
        val bytes = BundleFixture.write(mapOf("InstalledLocales" to mapOf("en" to "", "root" to "")))
        val calls = AtomicInteger()
        val loader = ICUResourceLoader { calls.incrementAndGet(); BinaryBuffer(bytes.copyOf()) }
        val executor = Executors.newFixedThreadPool(8); val start = CountDownLatch(1)
        try {
            val jobs = (0 until 8).map { executor.submit<Array<ULocale>> { start.await(); ICUResourceBundle.getAvailableULocales(base, loader)!! } }
            start.countDown()
            val arrays = jobs.map { it.get() }
            assertEquals(1, calls.get()); arrays.forEach { assertSame(arrays[0], it) }
        } finally { executor.shutdownNow() }
    }
}
