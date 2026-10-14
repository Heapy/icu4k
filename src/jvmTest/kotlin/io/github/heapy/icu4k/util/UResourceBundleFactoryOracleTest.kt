package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.*
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import kotlin.test.*

class UResourceBundleFactoryOracleTest {
    private class Loader(val files: MutableMap<String, ByteArray>) : ClassLoader(com.ibm.icu.util.UResourceBundle::class.java.classLoader) {
        val requests = mutableListOf<String>()
        override fun getResourceAsStream(name: String): InputStream? { requests.add(name); return files[name]?.inputStream() }
    }
    private val factory = com.ibm.icu.util.UResourceBundle::class.java.getDeclaredMethod("getBundleInstance", String::class.java, String::class.java, ClassLoader::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
    private fun source(base: String?, locale: String?, loader: ClassLoader?, direct: Boolean): com.ibm.icu.util.UResourceBundle =
        factory.invoke(null, base, locale, loader, direct) as com.ibm.icu.util.UResourceBundle
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: Throwable) {
        val cause = if (failure is InvocationTargetException) failure.cause!! else failure
        when (cause) {
            is java.util.MissingResourceException -> listOf("missing", cause.message, cause.className, cause.key)
            is MissingResourceException -> listOf("missing", cause.message, cause.getClassName()?.replace("io.github.heapy.icu4k", "com.ibm.icu"), cause.getKey())
            else -> cause::class.simpleName
        }
    }
    @Test fun rootDispatchAndLoadTracesMatchAcrossNamingModesAndFallbacks() {
        val saved = Locale.getDefault(); Locale.setDefault(Locale.ROOT)
        try {
            var index = 0
            for (dotted in listOf(false, true)) for (kind in listOf("binary", "java", "missing-binary", "missing-java", "absent"))
                for (locale in listOf("", "root", "en", "en_US", "fr", "zz", null)) for (direct in listOf(false, true)) {
                    val base = if (dotted) "icu4k.test.factory.oracle${index++}" else "icu4k-test/factory/oracle${index++}"
                    val path = base.replace('.', '/'); val rootLocale = if (dotted) "" else "root"
                    fun binaryName(id: String): String = ICUResourceBundleReader.getFullName(base, id)
                    fun propertiesName(id: String): String = path + (if (id.isEmpty()) "" else "_$id") + ".properties"
                    val files = mutableMapOf<String, ByteArray>()
                    if (kind == "binary") files[binaryName(rootLocale)] = BundleFixture.write(mapOf("word" to "binary-root"))
                    if (kind == "java") files[propertiesName(rootLocale)] = "word=java-root".toByteArray()
                    if (kind != "absent") {
                        if (kind != "missing-java") files[binaryName("en")] = BundleFixture.write(mapOf("word" to "binary-en"))
                        files[propertiesName("en")] = "word=java-en".toByteArray()
                        files[propertiesName("fr")] = "word=java-fr".toByteArray()
                    }
                    val jLoader = Loader(files.toMutableMap()); val kLoader = Loader(files.toMutableMap())
                    assertEquals(outcome { source(base, locale, jLoader, direct).let { listOf(it.javaClass.simpleName, it.uLocale.name, it.getString("word")) } },
                        outcome { UResourceBundle.getBundleInstance(base, locale, ICUClassLoaderResourceLoader(kLoader), direct).let { listOf(it::class.simpleName, it.getULocale().getName(), it.getString("word")) } }, "$base/$locale/$direct")
                    assertEquals(jLoader.requests, kLoader.requests, "$base/$locale/$direct requests")
                }
        } finally { Locale.setDefault(saved) }
    }
    @Test fun missingTypePromotesAndLoaderOmissionMatchesOriginal() {
        val base = "icu4k-test/factory/promotion-oracle"
        val j = Loader(mutableMapOf()); val k = Loader(mutableMapOf())
        fun compare(locale: String) = assertEquals(outcome { source(base, locale, j, true).getString("word") },
            outcome { UResourceBundle.getBundleInstance(base, locale, ICUClassLoaderResourceLoader(k), true).getString("word") })
        compare("xx")
        val bytes = BundleFixture.write(mapOf("word" to "binary")); j.files["$base/en.res"] = bytes; k.files["$base/en.res"] = bytes
        compare("en")
        val j2 = Loader(mutableMapOf("${base}_fr.properties" to "word=java-fr".toByteArray()))
        val k2 = Loader(j2.files.toMutableMap())
        assertEquals(outcome { source(base, "fr", j2, true).getString("word") },
            outcome { UResourceBundle.getBundleInstance(base, "fr", ICUClassLoaderResourceLoader(k2), true).getString("word") })
        assertEquals(j.requests, k.requests); assertEquals(j2.requests, k2.requests)
        assertEquals(outcome { source(null, "en", j, false) }, outcome { UResourceBundle.getBundleInstance(null as String?, "en", ICUClassLoaderResourceLoader(k), false) })
    }
    @Test fun nullableLoaderUsesContextForJavaRootProbes() {
        val base = "icu4k.test.factory.contextOracle"
        val loader = Loader(mutableMapOf("${base.replace('.', '/')}.properties" to "word=context".toByteArray()))
        val saved = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = loader
            assertEquals(outcome { source(base, "", null, false).getString("word") }, outcome { UResourceBundle.getBundleInstance(base, "", null, false).getString("word") })
            assertEquals(source(base, "", loader, false).getString("word"),
                UResourceBundle.getBundleInstance(base, "", ICUClassLoaderResourceLoader(loader), false).getString("word"))
            assertEquals(source(base, "fr", null, false).getString("word"), UResourceBundle.getBundleInstance(base, "fr", null, false).getString("word"))
        } finally { Thread.currentThread().contextClassLoader = saved }
    }
}
