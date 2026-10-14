package io.github.heapy.icu4k.impl

import java.io.InputStream
import java.util.Locale
import kotlin.test.*

class BundleStringSinkOracleTest {
    private class Loader(private val files: Map<String, ByteArray>) : ClassLoader(com.ibm.icu.impl.ICUResourceBundle::class.java.classLoader) {
        override fun getResourceAsStream(name: String): InputStream? = files[name]?.inputStream() ?: super.getResourceAsStream(name)
    }
    private fun result(block: () -> Any?): Any? = try { block() } catch (ex: RuntimeException) {
        listOf("error", ex.javaClass.simpleName, ex.message?.replace("com.ibm.icu", "io.github.heapy.icu4k"))
    }
    private fun snapshot(value: com.ibm.icu.impl.UResource.Value, key: com.ibm.icu.impl.UResource.Key): Any? = when (value.getType()) {
        0 -> value.getString()
        3 -> listOf("alias", value.getAliasString())
        7 -> value.getInt()
        14 -> value.getIntVector().toList()
        1 -> value.getBinary().let { b -> ByteArray(b.remaining()).also { b.get(it) }.toList() }
        8 -> value.getArray().let { array -> List(array.getSize()) { array.getValue(it, value); snapshot(value, key) } }
        2 -> value.getTable().let { table -> List(table.getSize()) { table.getKeyAndValue(it, key, value); key.toString() to snapshot(value, key) } }
        else -> error("type")
    }
    private fun snapshot(value: UResource.Value, key: UResource.Key): Any? = when (value.getType()) {
        0 -> value.getString()
        3 -> listOf("alias", value.getAliasString())
        7 -> value.getInt()
        14 -> value.getIntVector().toList()
        1 -> value.getBinary().let { it.readBytes(it.remaining).toList() }
        8 -> value.getArray().let { array -> List(array.getSize()) { array.getValue(it, value); snapshot(value, key) } }
        2 -> value.getTable().let { table -> List(table.getSize()) { table.getKeyAndValue(it, key, value); key.toString() to snapshot(value, key) } }
        else -> error("type")
    }
    @Test fun optimizedStringsValuesAndMutableSinksMatchOriginalAcrossModes() {
        val base = "icu4k-test/string-sink-oracle"
        val data = BundleFixture.data()
        val jl = Loader(data.mapKeys { "$base/${it.key}.res" })
        val kl = BundleFixture.loader(data)
        val saved = Locale.getDefault(); Locale.setDefault(Locale.US)
        try {
            for (mode in ICUResourceBundle.OpenType.entries) for (id in listOf("root", "en_US", "en_GB", "fr")) {
                val j = com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(base, id, jl, com.ibm.icu.impl.ICUResourceBundle.OpenType.valueOf(mode.name))
                val k = ICUResourceBundle.getBundleInstance(base, id, kl, mode)
                for (path in listOf("", "target", "nested/x", "nested/inherited", "nested/sameName", "aliasGroup/inherited", "dynamic", "inherit",
                    "strings/1", "mixed/1", "integer", "marker", "missing/path", "nested/")) {
                    assertEquals(result { j.findStringWithFallback(path) }, result { k.findStringWithFallback(path) }, "$id/$mode/find/$path")
                    assertEquals(result { j.getStringWithFallback(path) }, result { k.getStringWithFallback(path) }, "$id/$mode/get/$path")
                    assertEquals(result { snapshot(j.getValueWithFallback(path), com.ibm.icu.impl.UResource.Key()) },
                        result { snapshot(k.getValueWithFallback(path), UResource.Key()) }, "$id/$mode/value/$path")
                }
                for (path in listOf("", "nested", "aliasGroup", "target", "marker", "strings", "missing")) {
                    val original = mutableListOf<Any?>(); val port = mutableListOf<Any?>()
                    val je = result { j.getAllItemsWithFallback(path, object : com.ibm.icu.impl.UResource.Sink() {
                        override fun put(key: com.ibm.icu.impl.UResource.Key, value: com.ibm.icu.impl.UResource.Value, noFallback: Boolean) {
                            original.add(listOf(key.toString(), snapshot(value, key), noFallback))
                        }
                    }) }
                    val ke = result { k.getAllItemsWithFallback(path, object : UResource.Sink() {
                        override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                            port.add(listOf(key.toString(), snapshot(value, key), noFallback))
                        }
                    }) }
                    assertEquals(je, ke, "$id/$mode/items/$path"); assertEquals(original, port, "$id/$mode/items/$path")
                }
            }
        } finally { Locale.setDefault(saved) }
    }
}
