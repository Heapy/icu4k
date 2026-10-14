package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.*
import kotlin.test.*

class UResourceBundleFactoryTest {
    private class Loader(val files: MutableMap<String, ByteArray>) : ICUResourceLoader {
        val requests = mutableListOf<String>()
        override fun getData(fullName: String): BinaryBuffer? { requests.add(fullName); return files[fullName]?.let { BinaryBuffer(it.copyOf()) } }
    }
    @Test fun binaryRootsWinOverPropertiesAndJavaRootsWinOverBinaryChildren() {
        val base = "icu4k-test/factory/binary-common"
        val binary = Loader(mutableMapOf("$base/root.res" to BundleFixture.write(mapOf("word" to "binary-root")),
            "$base/en.res" to BundleFixture.write(mapOf("word" to "binary-en")), "${base}_root.properties" to "word=java-root".encodeToByteArray()))
        val b = UResourceBundle.getBundleInstance(base, "en", binary, true)
        assertTrue(b is ICUResourceBundle); assertEquals("binary-en", b.getString("word")); assertFalse(binary.requests.any { it.endsWith(".properties") })
        val javaBase = "icu4k.test.factory.javaCommon"
        val path = javaBase.replace('.', '/')
        val java = Loader(mutableMapOf("$path.properties" to "word=java-root".encodeToByteArray(), "${path}_en.properties" to "word=java-en".encodeToByteArray(),
            "${path}_en.res" to BundleFixture.write(mapOf("word" to "binary-en"))))
        val j = UResourceBundle.getBundleInstance(javaBase, "en", java, true)
        assertTrue(j is ResourceBundleWrapper); assertEquals("java-en", j.getString("word")); assertFalse(java.requests.contains("${path}_en.res"))
        assertEquals("java-en", UResourceBundle.getBundleInstance(javaBase, ULocale("en@calendar=japanese"), java).getString("word"))
    }
    @Test fun missingRootsRecoverThroughLocaleAndThenRetainTheSelectedType() {
        val base = "icu4k-test/factory/missing-common"
        val loader = Loader(mutableMapOf())
        assertFailsWith<MissingResourceException> { UResourceBundle.getBundleInstance(base, "xx", loader, true) }
        loader.files["$base/en.res"] = BundleFixture.write(mapOf("word" to "binary"))
        loader.files["${base}_en.properties"] = "word=java".encodeToByteArray()
        assertEquals("binary", UResourceBundle.getBundleInstance(base, "en", loader, true).getString("word"))
        loader.files["${base}_fr.properties"] = "word=java-fr".encodeToByteArray()
        assertFailsWith<MissingResourceException> { UResourceBundle.getBundleInstance(base, "fr", loader, true) }
        val javaBase = "icu4k.test.factory.missingJavaCommon"
        val path = javaBase.replace('.', '/')
        val java = Loader(mutableMapOf("${path}_en.properties" to "word=java".encodeToByteArray(), "${path}_fr.res" to BundleFixture.write(mapOf("word" to "binary-fr"))))
        assertTrue(UResourceBundle.getBundleInstance(javaBase, "en", java, true) is ResourceBundleWrapper)
        assertFailsWith<MissingResourceException> { UResourceBundle.getBundleInstance(javaBase, "fr", java, true) }
    }
    @Test fun rootCacheOmitsLoaderAndStringNullsKeepOriginalFailures() {
        val base = "icu4k-test/factory/loader-common"
        val first = Loader(mutableMapOf("$base/root.res" to BundleFixture.write(mapOf("word" to "first")), "$base/en.res" to BundleFixture.write(mapOf("word" to "en"))))
        assertTrue(UResourceBundle.getBundleInstance(base, "en", first, true) is ICUResourceBundle)
        val second = Loader(mutableMapOf("${base}_fr.properties" to "word=java-fr".encodeToByteArray()))
        assertFailsWith<MissingResourceException> { UResourceBundle.getBundleInstance(base, "fr", second, true) }
        assertFalse(second.requests.any { it.endsWith(".properties") })
        assertFailsWith<NullPointerException> { UResourceBundle.getBundleInstance(null as String?, "en", first) }
        assertFailsWith<NullPointerException> { UResourceBundle.getBundleInstance(base, null as String?, first) }
    }
    @Test fun embeddedPooledRootSupportsDefaultAndULocaleFactoryFamilies() {
        val root = UResourceBundle.getBundleInstance(null as String?, ULocale.ROOT)
        assertTrue(root is ICUResourceBundle); assertEquals("", root.getULocale().getName())
        assertTrue(root.keySet().isNotEmpty())
        assertTrue(UResourceBundle.getBundleInstance(ULocale.ROOT) is ICUResourceBundle)
        assertTrue(UResourceBundle.getBundleInstance(null as String?) is ICUResourceBundle)
        assertTrue(UResourceBundle.getBundleInstance(null as String?, null as ULocale?) is ICUResourceBundle)
    }
}
