package io.github.heapy.icu4k.impl

import java.io.InputStream
import java.util.Locale
import kotlin.test.*

class BundleChildrenSinkOracleTest {
    private class Loader(private val files: Map<String, ByteArray>) : ClassLoader(com.ibm.icu.impl.ICUResourceBundle::class.java.classLoader) {
        override fun getResourceAsStream(name: String): InputStream? = files[name]?.inputStream() ?: super.getResourceAsStream(name)
    }
    @Test fun childSinkTracesMatchAliasRedirectionAndParentEnumeration() {
        val base = "icu4k-test/children-oracle"
        val data = BundleFixture.childData()
        val jl = Loader(data.mapKeys { "$base/${it.key}.res" }); val kl = BundleFixture.loader(data)
        val saved = Locale.getDefault(); Locale.setDefault(Locale.US)
        try {
            for (mode in ICUResourceBundle.OpenType.entries) for (id in listOf("root", "en", "en_US")) {
                val j = com.ibm.icu.impl.ICUResourceBundle.getBundleInstance(base, id, jl, com.ibm.icu.impl.ICUResourceBundle.OpenType.valueOf(mode.name))
                val k = ICUResourceBundle.getBundleInstance(base, id, kl, mode)
                for (path in listOf("groups", "groups/first", "target", "missing")) {
                    val original = mutableListOf<Any?>(); val port = mutableListOf<Any?>()
                    fun result(action: () -> Unit): String? = try { action(); null } catch (ex: RuntimeException) { ex.javaClass.simpleName }
                    val je = result {
                        j.getAllChildrenWithFallback(path, object : com.ibm.icu.impl.UResource.Sink() {
                            override fun put(key: com.ibm.icu.impl.UResource.Key, value: com.ibm.icu.impl.UResource.Value, noFallback: Boolean) {
                                val name = key.toString(); val type = value.getType()
                                if (type == 2) assertTrue(value.getTable().findValue("value", value))
                                original.add(listOf(name, type, value.getString(), noFallback))
                                key.setString("sink-mutated-key")
                            }
                        })
                    }
                    val ke = result {
                        k.getAllChildrenWithFallback(path, object : UResource.Sink() {
                            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                                val name = key.toString(); val type = value.getType()
                                if (type == 2) assertTrue(value.getTable().findValue("value", value))
                                port.add(listOf(name, type, value.getString(), noFallback))
                                key.setString("sink-mutated-key")
                            }
                        })
                    }
                    assertEquals(je, ke, "$id/$mode/$path"); assertEquals(original, port, "$id/$mode/$path")
                }
            }
        } finally { Locale.setDefault(saved) }
    }
}
