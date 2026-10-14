package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class DatPackageReaderTest {
    @Test fun packagesAuthenticateBothOrdersAndShareIndependentDataViews() {
        for (little in listOf(false, true)) for (name in listOf("icudt78b", "icudt78l")) {
            val storage = DatPackageFixture.write(mapOf("a.res" to ByteArray(16) { it.toByte() }, "z.res" to ByteArray(16) { (it + 16).toByte() }), little, name)
            val buffer = BinaryBuffer(storage)
            val pkg = assertNotNull(ICUDataPackage.open(buffer))
            assertEquals(32, buffer.position)
            assertEquals(if (little) BinaryBuffer.ByteOrder.LITTLE_ENDIAN else BinaryBuffer.ByteOrder.BIG_ENDIAN, buffer.order)
            val a = assertNotNull(pkg.getData("a.res")); val another = assertNotNull(pkg.getData("a.res"))
            assertEquals(BinaryBuffer.ByteOrder.BIG_ENDIAN, a.order)
            assertContentEquals(ByteArray(16) { it.toByte() }, a.readBytes(a.remaining))
            assertEquals(0, another.position); assertEquals(32, buffer.position)
            another.writeByte(77)
            assertEquals(77, pkg.getData("a.res")!!.readUnsignedByte())
            assertNull(pkg.getData("missing"))
            val readOnly = assertNotNull(ICUDataPackage.open(BinaryBuffer(storage, isReadOnly = true))).getData("a.res")!!
            assertTrue(readOnly.isReadOnly)
            assertFailsWith<UnsupportedOperationException> { readOnly.writeByte(0) }
        }
    }
    @Test fun headersCountAndPackagePrefixesAreAuthenticated() {
        val good = DatPackageFixture.write(mapOf("a" to ByteArray(16)))
        assertNull(ICUDataPackage.open(BinaryBuffer(good.copyOf().also { it[2] = 0 })))
        assertNull(ICUDataPackage.open(BinaryBuffer(good.copyOf().also { it[16] = 2 })))
        assertNull(ICUDataPackage.open(BinaryBuffer(good.copyOf().also { it[35] = 0 })))
        assertNull(ICUDataPackage.open(BinaryBuffer(DatPackageFixture.write(mapOf("a" to ByteArray(16)), packageName = "icudt77b"))))
        assertNull(ICUDataPackage.open(BinaryBuffer(DatPackageFixture.write(mapOf("a" to ByteArray(16)), packageName = "icudt78x"))))
    }
    @Test fun folderEnumerationPreservesSuffixSearchAndSkipsNestedPaths() {
        val pkg = assertNotNull(ICUDataPackage.open(BinaryBuffer(DatPackageFixture.write(mapOf(
            "a.resjunk" to ByteArray(16), "coll/en.res" to ByteArray(16), "coll/en_US.res" to ByteArray(16),
            "coll/nested/fr.res" to ByteArray(16), "coll/pool.res" to ByteArray(16), "de.res" to ByteArray(16))))))
        val roots = linkedSetOf<String>(); pkg.addBaseNamesInFolder("", ".res", roots)
        assertEquals(setOf("a.res", "de"), roots)
        val coll = linkedSetOf<String>(); pkg.addBaseNamesInFolder("coll", ".res", coll)
        assertEquals(setOf("en", "en_US", "pool"), coll)
        pkg.addBaseNamesInFolder("missing", ".res", coll)
        assertEquals(setOf("en", "en_US", "pool"), coll)
    }
    @Test fun realBundlesLoadAndEnumerateThroughPackageBoundary() {
        val base = "icu4k-test/package-bundles"
        val pkg = assertNotNull(ICUDataPackage.open(BinaryBuffer(DatPackageFixture.write(mapOf(
            "root.res" to BundleFixture.write(mapOf("word" to "root", "onlyRoot" to "fallback")),
            "en.res" to BundleFixture.write(mapOf("word" to "english")),
            "res_index.res" to BundleFixture.write(mapOf("InstalledLocales" to mapOf("root" to "", "en" to "")))
        ), little = true))))
        val loader = pkg.asResourceLoader(base)
        val bundle = ICUResourceBundle.getBundleInstance(base, "en", loader, ICUResourceBundle.OpenType.LOCALE_ROOT)
        assertEquals("english", bundle.getStringWithFallback("word"))
        assertEquals("fallback", bundle.getStringWithFallback("onlyRoot"))
        assertEquals(setOf("", "en"), ICUResourceBundle.getFullLocaleNameSet(base, loader))
        assertEquals(setOf("en", "root"), ICUResourceBundle.getAvailableLocaleNameSet(base, loader))
        assertNotNull(pkg.asResourceLoader("$base/").getData("$base/en.res"))
        assertNull(loader.getData("other/en.res"))
    }
}
