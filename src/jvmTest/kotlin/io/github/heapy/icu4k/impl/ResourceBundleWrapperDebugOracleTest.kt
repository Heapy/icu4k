package io.github.heapy.icu4k.impl

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.util.Locale
import kotlin.test.*

class ResourceBundleWrapperDebugOracleTest {
    private class Loader(private val base: String, private val failure: Boolean) : URLClassLoader(arrayOf(
        ResourceBundleWrapper::class.java.protectionDomain.codeSource.location,
        com.ibm.icu.impl.ResourceBundleWrapper::class.java.protectionDomain.codeSource.location,
        Unit::class.java.protectionDomain.codeSource.location), null) {
        override fun loadClass(name: String): Class<*> {
            if (failure && name == "${base}_en") throw IllegalStateException("class failure")
            return super.loadClass(name)
        }
        override fun getResourceAsStream(name: String): InputStream? = if (name == base.replace('.', '/') + ".properties")
            "word=root".byteInputStream() else super.getResourceAsStream(name)
    }
    @Test fun missingAndRejectedClassesRetainExactDiagnosticOutput() {
        val saved = System.getProperty("ICUDebug"); val savedLocale = Locale.getDefault(); val stdout = System.out
        try {
            Locale.setDefault(Locale.US); System.setProperty("ICUDebug", "help,resourceBundleWrapper")
            for (reject in listOf(false, true)) {
                val traces = mutableListOf<String>()
                val outcomes = mutableListOf<Any?>()
                for (port in listOf(false, true)) {
                    val output = ByteArrayOutputStream(); System.setOut(PrintStream(output))
                    val base = "icu4k.test.wrapper.debug$reject"
                    Loader(base, reject).use { loader ->
                        try {
                            if (port) {
                                val type = loader.loadClass("io.github.heapy.icu4k.impl.ICUResourceLoader")
                                val provider = loader.loadClass("io.github.heapy.icu4k.impl.ICUClassLoaderResourceLoader").getConstructor(ClassLoader::class.java).newInstance(loader)
                                val companion = loader.loadClass("io.github.heapy.icu4k.impl.ResourceBundleWrapper").getField("Companion").get(null)
                                val result = companion.javaClass.getMethod("getBundleInstance", String::class.java, String::class.java, type, Boolean::class.javaPrimitiveType)
                                    .invoke(companion, base, "en", provider, true)
                                outcomes.add(result.javaClass.getMethod("getString", String::class.java).invoke(result, "word"))
                            } else {
                                val result = loader.loadClass("com.ibm.icu.impl.ResourceBundleWrapper").getMethod("getBundleInstance", String::class.java, String::class.java, ClassLoader::class.java, Boolean::class.javaPrimitiveType)
                                    .invoke(null, base, "en", loader, true)
                                outcomes.add(result.javaClass.getMethod("getString", String::class.java).invoke(result, "word"))
                            }
                        } catch (problem: InvocationTargetException) { outcomes.add(problem.cause!!::class.simpleName) }
                    }
                    traces.add(output.toString())
                }
                assertEquals(outcomes[0], outcomes[1])
                assertEquals(traces[0], traces[1])
            }
        } finally {
            System.setOut(stdout); Locale.setDefault(savedLocale)
            if (saved == null) System.clearProperty("ICUDebug") else System.setProperty("ICUDebug", saved)
        }
    }
}
