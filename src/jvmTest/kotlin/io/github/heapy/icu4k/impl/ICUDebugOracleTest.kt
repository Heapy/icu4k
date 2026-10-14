package io.github.heapy.icu4k.impl

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import kotlin.test.*

class ICUDebugOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (failure: java.lang.reflect.InvocationTargetException) { failure.cause!!::class.simpleName } catch (failure: NullPointerException) { failure::class.simpleName }
    @Test fun flagsValuesAndOutputMatchOriginalSnapshots() {
        val saved = System.getProperty("ICUDebug"); val stdout = System.out
        val source = com.ibm.icu.impl.ICUDebug::class.java.protectionDomain.codeSource.location
        try {
            for (parameters in listOf(null, "", "help", "foo=bar,baz=,flag", "helpful,foo=one,foo=two,longname", "foo=a=b,bar=true")) {
                if (parameters == null) System.clearProperty("ICUDebug") else System.setProperty("ICUDebug", parameters)
                val originalOutput = ByteArrayOutputStream(); System.setOut(PrintStream(originalOutput))
                URLClassLoader(arrayOf(source), null).use { loader ->
                    val original = loader.loadClass("com.ibm.icu.impl.ICUDebug")
                    val output = mutableListOf<String>(); val port = DebugSettings(parameters, output::add)
                    assertEquals(original.getMethod("enabled").invoke(null), port.enabled())
                    for (name in listOf(null, "", "foo", "baz", "flag", "name", "help", "absent", "FOO")) {
                        assertEquals(outcome { original.getMethod("enabled", String::class.java).invoke(null, name) }, outcome { port.enabled(name) })
                        assertEquals(outcome { original.getMethod("value", String::class.java).invoke(null, name) }, outcome { port.value(name) })
                    }
                    assertEquals(originalOutput.toString(), output.joinToString("") { it + System.lineSeparator() })
                }
            }
        } finally { System.setOut(stdout); if (saved == null) System.clearProperty("ICUDebug") else System.setProperty("ICUDebug", saved) }
    }
}
