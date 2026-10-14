package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.MissingResourceException
import java.io.InputStream
import java.io.IOException
import java.util.ListResourceBundle
import java.util.Locale
import kotlin.test.*

class WrapperJavaParent : ListResourceBundle() { override fun getContents(): Array<Array<Any>> = arrayOf(arrayOf("underlying", "java-parent")) }
class WrapperJavaClass : ListResourceBundle() {
    init { setParent(WrapperJavaParent()) }
    override fun getContents(): Array<Array<Any>> = arrayOf(arrayOf("word", "class"), arrayOf("arr", arrayOf("a", "b")), arrayOf("number", 37))
}
class WrapperThrowingClass : ListResourceBundle() {
    init { throw IllegalStateException("constructor failure") }
    override fun getContents(): Array<Array<Any>> = emptyArray()
}

class ResourceBundleWrapperOracleTest {
    private class Loader(private val base: String, private val kind: String, private val files: Map<String, ByteArray>) : ClassLoader(WrapperJavaClass::class.java.classLoader) {
        val classLoads = mutableListOf<String>(); val propertyReads = mutableListOf<String>(); var closes = 0
        override fun loadClass(name: String): Class<*> {
            if (!name.startsWith(base)) return super.loadClass(name)
            classLoads.add(name)
            if (name == "${base}_en") when (kind) {
                "class" -> return WrapperJavaClass::class.java
                "wrong" -> return String::class.java
                "throw" -> return WrapperThrowingClass::class.java
                "linkage" -> throw NoClassDefFoundError("dependency")
                "security" -> throw SecurityException("class denied")
            }
            throw ClassNotFoundException(name)
        }
        override fun getResourceAsStream(name: String): InputStream? {
            if (!name.startsWith(base.replace('.', '/'))) return super.getResourceAsStream(name)
            propertyReads.add(name)
            if (name.endsWith("_en.properties") && kind == "open") throw SecurityException("stream denied")
            val bytes = files[name] ?: return null
            return object : InputStream() {
                private var position = 0
                override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 255 else if (kind == "read" && name.endsWith("_en.properties")) throw IOException("read failure") else -1
                override fun read(target: ByteArray, offset: Int, length: Int): Int {
                    if (position == bytes.size) return read()
                    val count = minOf(length, bytes.size - position); bytes.copyInto(target, offset, position, position + count); position += count
                    return count
                }
                override fun close() { ++closes; if (kind == "close") throw IOException("close failure") }
            }
        }
    }
    private fun outcome(block: () -> Any?): Any? = try {
        when (val value = block()) { is Array<*> -> value.toList(); else -> value }
    } catch (failure: Exception) {
        when (failure) {
            is java.util.MissingResourceException -> listOf("missing", normalize(failure.message), normalize(failure.className), failure.key)
            is MissingResourceException -> listOf("missing", normalize(failure.message), normalize(failure.getClassName()), failure.getKey())
            else -> failure::class.simpleName
        }
    }
    private fun normalize(value: String?): String? = value?.replace("io.github.heapy.icu4k.impl.ResourceBundleWrapper", "com.ibm.icu.impl.ResourceBundleWrapper")
    @Test fun classesPropertiesFallbackAndFailureBoundariesMatchOriginal() {
        val saved = Locale.getDefault()
        try {
            var case = 0
            for (default in listOf(Locale.US, Locale.FRANCE, Locale.ROOT)) for (kind in listOf("properties", "class", "wrong", "throw", "linkage", "security", "open", "read", "close", "bad-escape")) {
                Locale.setDefault(default)
                for (requested in listOf("", "en", "en_US", "en_US_POSIX", "fr", "fr_CA", "zz", "zz_ZZ")) for (direct in listOf(false, true)) {
                    val base = "icu4k.test.wrapper.oracle${case++}"
                    val path = base.replace('.', '/')
                    val files = mapOf("$path.properties" to "word=root\nrootOnly=fallback\nunderlying=root\n".toByteArray(),
                        "${path}_en.properties" to (if (kind == "bad-escape") "word=\\uZZZZ\n" else "word=english\nchildOnly=child\n").toByteArray(),
                        "${path}_en_US.properties" to "word=US\nregion=country\n".toByteArray(), "${path}_fr.properties" to "word=French\n".toByteArray())
                    val jLoader = Loader(base, kind, files); val kLoader = Loader(base, kind, files)
                    var j: com.ibm.icu.impl.ResourceBundleWrapper? = null; var k: ResourceBundleWrapper? = null
                    assertEquals(outcome { j = com.ibm.icu.impl.ResourceBundleWrapper.getBundleInstance(base, requested, jLoader, direct); j!!.uLocale.name },
                        outcome { k = ResourceBundleWrapper.getBundleInstance(base, requested, ICUClassLoaderResourceLoader(kLoader), direct); k!!.getULocale().getName() }, "$default/$kind/$requested/$direct")
                    assertEquals(jLoader.classLoads, kLoader.classLoads); assertEquals(jLoader.propertyReads, kLoader.propertyReads); assertEquals(jLoader.closes, kLoader.closes)
                    if (j != null && k != null) {
                        for (key in listOf("word", "rootOnly", "childOnly", "underlying", "region", "arr", "number", "missing")) {
                            assertEquals(outcome { j!!.getObject(key) }, outcome { k!!.getObject(key) }, key)
                            assertEquals(j!!.containsKey(key), k!!.containsKey(key), key)
                            assertEquals(outcome { j!!.get(key).type }, outcome { k!![key].getType() }, "typed $key")
                        }
                        assertEquals(j!!.keys.toList(), k!!.getKeys().let { keys -> buildList { while (keys.hasMoreElements()) add(keys.nextElement()) } })
                        assertEquals(j!!.keySet(), k!!.keySet()); assertEquals(j!!.size, k!!.getSize()); assertEquals(j!!.type, k!!.getType())
                        fun javaParents(): List<String> = buildList { var item = j; while (item != null) { add(item.uLocale.name); item = item.parent as com.ibm.icu.impl.ResourceBundleWrapper? } }
                        fun commonParents(): List<String> = buildList { var item = k; while (item != null) { add(item.getULocale().getName()); item = item.getParent() as ResourceBundleWrapper? } }
                        assertEquals(javaParents(), commonParents())
                    }
                }
            }
        } finally { Locale.setDefault(saved) }
    }
    @Test fun defaultContextLoaderAndCacheLoaderOmissionMatchOriginal() {
        val base = "icu4k.test.wrapper.context"
        val saved = Thread.currentThread().contextClassLoader
        val bytes = mapOf("${base.replace('.', '/')}.properties" to "word=context".toByteArray())
        val loader = Loader(base, "properties", bytes)
        try {
            Thread.currentThread().contextClassLoader = loader
            assertEquals(com.ibm.icu.impl.ResourceBundleWrapper.getBundleInstance(base, "", null, true).getString("word"),
                ResourceBundleWrapper.getBundleInstance(base, "", null, true).getString("word"))
            assertEquals("context", ResourceBundleWrapper.getBundleInstance(base, "", ICUResourceLoader { error("cached loader") }, true).getString("word"))
        } finally { Thread.currentThread().contextClassLoader = saved }
    }
}
