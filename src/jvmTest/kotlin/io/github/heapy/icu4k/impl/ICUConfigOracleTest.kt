package io.github.heapy.icu4k.impl

import java.io.IOException
import java.io.InputStream
import java.net.URLClassLoader
import kotlin.test.*

class ICUConfigOracleTest {
    @Test fun defaultsAndDynamicHostPrecedenceMatchOriginal() {
        for (key in ICUConfigDefaults.properties.keys + "icu4k.test.config.unknown") {
            assertEquals(com.ibm.icu.impl.ICUConfig.get(key), ICUConfig.get(key))
            assertEquals(com.ibm.icu.impl.ICUConfig.get(key, "caller"), ICUConfig.get(key, "caller"))
        }
        val key = "com.ibm.icu.impl.ICUResourceBundle.skipRuntimeLocaleResourceScan"
        val saved = System.getProperty(key)
        try {
            for (value in listOf("true", "", " ")) {
                System.setProperty(key, value)
                assertEquals(com.ibm.icu.impl.ICUConfig.get(key), ICUConfig.get(key))
            }
        } finally { if (saved == null) System.clearProperty(key) else System.setProperty(key, saved) }
    }
    private class Loader(private val bytes: ByteArray?, private val failRead: Boolean, private val failClose: Boolean, private val reenter: Boolean = false) : URLClassLoader(
        arrayOf(ICUConfig::class.java.protectionDomain.codeSource.location,
            com.ibm.icu.impl.ICUConfig::class.java.protectionDomain.codeSource.location,
            Unit::class.java.protectionDomain.codeSource.location), null) {
        var closedStreams = 0
        var loadingClass: String? = null
        val observed = mutableListOf<Any?>()
        override fun getResourceAsStream(name: String): InputStream? {
            if (name != "com/ibm/icu/ICUConfig.properties") return super.getResourceAsStream(name)
            val data = bytes ?: return null
            return object : InputStream() {
                private var position = 0
                override fun read(): Int {
                    if (position == data.size) {
                        if (reenter) observed.add(loadClass(loadingClass!!).getMethod("get", String::class.java, String::class.java).invoke(null, "known", "fallback"))
                        if (failRead) throw IOException("read failure")
                        return -1
                    }
                    return data[position++].toInt() and 255
                }
                override fun read(target: ByteArray, offset: Int, length: Int): Int {
                    if (position == data.size) return read()
                    val count = minOf(length, data.size - position)
                    data.copyInto(target, offset, position, position + count); position += count
                    return count
                }
                override fun close() { closedStreams++; if (failClose) throw IOException("close failure") }
            }
        }
    }
    @Test fun customPropertiesMissingFilesAndPartialIoFailuresMatchOriginal() {
        val normal = "known=file\nempty=\nescaped\\ key:line\\nbreak\nunicode=\\u03b1\ncontinued=left\\\n  right\ndup=old\ndup=new\n"
        val cases = listOf(Triple(normal.toByteArray(), false, false), Triple(null, false, false),
            Triple("known=partial\n".toByteArray(), true, false), Triple("known=kept\nbad=\\uZZZZ\n".toByteArray(), false, true))
        for ((bytes, failRead, failClose) in cases) Loader(bytes, failRead, failClose).use { loader ->
            val j = loader.loadClass("com.ibm.icu.impl.ICUConfig").getMethod("get", String::class.java, String::class.java)
            val k = loader.loadClass("io.github.heapy.icu4k.impl.ICUConfig").getMethod("get", String::class.java, String::class.java)
            for (key in listOf("known", "empty", "escaped key", "unicode", "continued", "dup", "absent"))
                assertEquals(j.invoke(null, key, "fallback"), k.invoke(null, key, "fallback"), key)
            assertEquals(if (bytes == null) 0 else 2, loader.closedStreams)
        }
    }
    @Test fun reentrantInitializationReadsAlreadyLoadedProperties() {
        Loader("known=loaded\n".toByteArray(), false, false, true).use { loader ->
            val traces = mutableListOf<List<Any?>>()
            for (name in listOf("com.ibm.icu.impl.ICUConfig", "io.github.heapy.icu4k.impl.ICUConfig")) {
                val start = loader.observed.size
                loader.loadingClass = name
                assertEquals("loaded", loader.loadClass(name).getMethod("get", String::class.java).invoke(null, "known"))
                traces.add(loader.observed.drop(start))
            }
            assertEquals(traces[0], traces[1])
            assertEquals<List<Any?>>(listOf("fallback", "loaded"), traces[0])
        }
    }

}
