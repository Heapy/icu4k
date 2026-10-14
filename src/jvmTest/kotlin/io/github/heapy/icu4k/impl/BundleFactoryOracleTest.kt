package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.UResourceBundle
import java.io.InputStream
import java.util.Locale
import kotlin.test.*

class BundleFactoryOracleTest {
    private class Loader(private val files: Map<String, ByteArray>) : ClassLoader(com.ibm.icu.impl.ICUResourceBundle::class.java.classLoader) {
        override fun getResourceAsStream(name: String): InputStream? = files[name]?.inputStream() ?: super.getResourceAsStream(name)
    }
    private fun result(block: () -> Any?): Any? = try { block() } catch (ex: RuntimeException) {
        listOf("error", ex.javaClass.simpleName, ex.message?.replace("com.ibm.icu", "io.github.heapy.icu4k"))
    }
    private fun value(j: com.ibm.icu.util.UResourceBundle): Any? = when (j.type) {
        0 -> j.string
        1 -> j.getBinary(null).toList()
        7 -> listOf(j.getInt(), j.getUInt())
        14 -> j.intVector.toList()
        8 -> (0 until j.size).map { value(j[it]) }
        2 -> j.keySet().toList()
        else -> error("type")
    }
    private fun value(k: UResourceBundle): Any? = when (k.getType()) {
        0 -> k.getString()
        1 -> k.getBinary(null).toList()
        7 -> listOf(k.getInt(), k.getUInt())
        14 -> k.getIntVector().toList()
        8 -> (0 until k.getSize()).map { value(k[it]) }
        2 -> k.keySet().toList()
        else -> error("type")
    }
    @Test fun factoryModesChainsPathsAndAliasesMatchOriginal() {
        val base = "icu4k-test/factory-oracle"
        val fixtures = BundleFixture.data()
        val jLoader = Loader(fixtures.mapKeys { "$base/${it.key}.res" })
        val kLoader = BundleFixture.loader(fixtures)
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            for (mode in ICUResourceBundle.OpenType.entries) for (id in listOf("root", "en_US", "en_GB", "en_AU", "fr", "es_AR", "zz_ZZ")) {
                val jMode = com.ibm.icu.impl.ICUResourceBundle.OpenType.valueOf(mode.name)
                val jo = result { com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(base, id, jLoader, jMode) }
                val ko = result { ICUResourceBundle.getBundleInstance(base, id, kLoader, mode) }
                if (jo !is com.ibm.icu.impl.ICUResourceBundle || ko !is ICUResourceBundle) {
                    assertEquals(jo, ko, "$id/$mode"); continue
                }
                assertEquals(jo.uLocale.name, ko.getULocale().getName(), "$id/$mode")
                assertEquals(jo.keySet().toList(), ko.keySet().toList())
                var jp: com.ibm.icu.impl.ICUResourceBundle? = jo
                var kp: ICUResourceBundle? = ko
                while (jp != null || kp != null) {
                    assertEquals(jp?.uLocale?.name, kp?.getULocale()?.getName(), "$id/$mode")
                    jp = jp?.parent; kp = kp?.getParent()
                }
                for (path in listOf("target", "inherit", "dynamic", "nested/x", "nested/inherited", "aliasGroup/inherited", "absolute",
                    "strings", "mixed", "integer", "vector", "binary", "marker", "missing/path", "", "nested/")) {
                    assertEquals(result { value(jo.getWithFallback(path)) }, result { value(ko.getWithFallback(path)) }, "$id/$mode/$path")
                    assertEquals(result { jo.findWithFallback(path)?.let { value(it) } }, result { ko.findWithFallback(path)?.let { value(it) } }, "$id/$mode/find/$path")
                }
                for (key in listOf("target", "inherit", "dynamic", "absolute", "cycleA", "cycleB", "missing"))
                    assertEquals(result { value(jo[key]) }, result { value(ko[key]) }, "$id/$mode/get/$key")
            }
        } finally { Locale.setDefault(saved) }
    }
    @Test fun nestedKeySetsRemainSortedAndMutableAndArrayIndexesPreserveTheirKey() {
        val base = "icu4k-test/factory-collections"
        val fixtures = BundleFixture.data()
        val j = com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(base, "root", Loader(fixtures.mapKeys { "$base/${it.key}.res" }), true)
        val k = ICUResourceBundle.getBundleInstance(base, "root", BundleFixture.loader(fixtures), true)
        val jKeys = j["nested"].keySet()
        val kKeys = k["nested"].keySet() as MutableSet<String>
        for (key in listOf("aaa", "zzz", "bbb")) { assertEquals(jKeys.add(key), kKeys.add(key)); assertEquals(jKeys.toList(), kKeys.toList()) }
        val ja = j["mixed"]; val ka = k["mixed"]
        for (index in listOf("0", "01", "+1", "١", "１", "-1", "2", "2147483648", "x")) {
            // NumberFormatException wording differs by the Common platform, so compare type and successful metadata.
            fun outcome(block: () -> List<Any?>): Any = try { block() } catch (ex: RuntimeException) { ex.javaClass.simpleName }
            assertEquals(outcome { val v = ja[index]; listOf(v.key, value(v)) }, outcome { val v = ka[index]; listOf(v.getKey(), value(v)) }, index)
        }
    }
}
