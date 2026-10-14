package io.github.heapy.icu4k.impl

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.URL
import java.net.URLClassLoader
import java.net.URLConnection
import java.net.URLStreamHandler
import kotlin.test.*

class OriginalURLFactory {
    companion object {
        @JvmStatic fun get(url: URL): Any? = when (url.path) {
            "/null" -> null
            "/throw" -> throw IllegalArgumentException("factory failure")
            "/wrong" -> "wrong type"
            else -> object : com.ibm.icu.impl.URLHandler() {
                override fun guide(visitor: URLVisitor, recurse: Boolean, strip: Boolean) { if (url.path == "/locales") listOf("en_US.res", "root.res", "res_index.res").forEach(visitor::visit) else visitor.visit("custom:$url") }
            }
        }
    }
}
class PortURLFactory {
    companion object {
        @JvmStatic fun get(url: URL): Any? = when (url.path) {
            "/null" -> null
            "/throw" -> throw IllegalArgumentException("factory failure")
            "/wrong" -> "wrong type"
            else -> object : URLHandler() {
                override fun guide(visitor: URLVisitor, recurse: Boolean, strip: Boolean) { if (url.path == "/locales") listOf("en_US.res", "root.res", "res_index.res").forEach(visitor::visit) else visitor.visit("custom:$url") }
            }
        }
    }
}
class OriginalPrivateURLFactory { companion object { @JvmStatic private fun get(url: URL): Any? = null } }
class PortPrivateURLFactory { companion object { @JvmStatic private fun get(url: URL): Any? = null } }
class OriginalInstanceURLFactory { fun get(url: URL): Any? = null }
class PortInstanceURLFactory { fun get(url: URL): Any? = null }
private fun failURLFactory() { error("initialization failure") }
class OriginalFailingURLFactory { companion object { init { failURLFactory() }; @JvmStatic fun get(url: URL): Any? = null } }
class PortFailingURLFactory { companion object { init { failURLFactory() }; @JvmStatic fun get(url: URL): Any? = null } }

class URLHandlerPropertiesOracleTest {
    private class Loader(private val properties: String?, private val readFails: Boolean, private val closeFails: Boolean, private val directory: String? = null, private val directoryURL: URL? = null) : URLClassLoader(arrayOf(
        URLHandler::class.java.protectionDomain.codeSource.location,
        com.ibm.icu.impl.URLHandler::class.java.protectionDomain.codeSource.location,
        URLHandlerPropertiesOracleTest::class.java.protectionDomain.codeSource.location,
        Unit::class.java.protectionDomain.codeSource.location), null) {
        var closes = 0
        override fun getResources(name: String): java.util.Enumeration<URL> = if (name == directory)
            java.util.Collections.enumeration(listOf(directoryURL!!)) else super.getResources(name)
        override fun getResourceAsStream(name: String): InputStream? {
            if (name != "urlhandler.props") return super.getResourceAsStream(name)
            val bytes = properties?.toByteArray() ?: return null
            return object : InputStream() {
                private var position = 0
                override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 255 else if (readFails) throw IOException("read failure") else -1
                override fun read(target: ByteArray, offset: Int, length: Int): Int {
                    if (position == bytes.size) return read()
                    val count = minOf(length, bytes.size - position); bytes.copyInto(target, offset, position, position + count); position += count
                    return count
                }
                override fun close() { closes++; if (closeFails) throw IOException("close failure") }
            }
        }
    }
    private data class Run(val results: List<Any?>, val stdout: String, val stderr: String, val closes: Int)
    private fun run(port: Boolean, properties: String?, readFails: Boolean = false, closeFails: Boolean = false): Run {
        val out = ByteArrayOutputStream(); val err = ByteArrayOutputStream()
        val oldOut = System.out; val oldErr = System.err
        System.setOut(PrintStream(out)); System.setErr(PrintStream(err))
        try {
            Loader(properties, readFails, closeFails).use { loader ->
                val base = loader.loadClass(if (port) "io.github.heapy.icu4k.impl.URLHandler" else "com.ibm.icu.impl.URLHandler")
                val visitorType = loader.loadClass(base.name + "\$URLVisitor")
                val companion = if (port) base.getField("Companion").get(null) else null
                val method = if (port) loader.loadClass("io.github.heapy.icu4k.impl.URLHandlerJvmKt").getMethod("get", companion!!.javaClass, URL::class.java)
                    else base.getMethod("get", URL::class.java)
                val streamHandler = object : URLStreamHandler() { override fun openConnection(url: URL): URLConnection = error("must not open") }
                val results = listOf(null, URL(null, "mock:/emit", streamHandler), URL(null, "mock:/null", streamHandler),
                    URL(null, "mock:/throw", streamHandler), URL(null, "mock:/wrong", streamHandler),
                    URL("file:/invalid%zz"), URL("file:/icu4k-test-url-missing"), URL("jar:file:/icu4k-test-url-missing.jar!/x")).map { url ->
                    try {
                        val handler = if (port) method.invoke(null, companion, url) else method.invoke(null, url)
                        if (handler == null) null else {
                            val names = mutableListOf<String>()
                            val visitor = Proxy.newProxyInstance(loader, arrayOf(visitorType)) { _, call, arguments ->
                                if (call.name == "visit") names.add(arguments!![0] as String)
                                null
                            }
                            base.getMethod("guide", visitorType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).invoke(handler, visitor, true, false)
                            names
                        }
                    } catch (failure: InvocationTargetException) { failure.cause!!::class.simpleName }
                }
                return Run(results, out.toString(), err.toString(), loader.closes)
            }
        } finally { System.setOut(oldOut); System.setErr(oldErr) }
    }
    @Test fun propertyParsingFactoriesAndPartialFailuresMatchOriginal() {
        val property = "ICUDebug"; val saved = System.getProperty(property)
        val prefix = "io.github.heapy.icu4k.impl."
        val templates = listOf<String?>(null, "#empty\n", " #comment\n mock = {Factory} \n",
            "mock={Factory}\nbad line\nmock=java.lang.String\n", "mock=does.not.Exist\nmock={Factory}\n",
            "mock=java.lang.String\nmock={Factory}\n", "mock={PrivateURLFactory}\n", "mock={InstanceURLFactory}\n",
            "mock={Factory}\nother={FailingURLFactory}\nmock=java.lang.String\n", "mock\u00a0={Factory}\n")
        fun props(template: String?, port: Boolean): String? {
            val kind = if (port) "Port" else "Original"
            return template?.replace("{Factory}", prefix + kind + "URLFactory")
                ?.replace("{PrivateURLFactory}", prefix + kind + "PrivateURLFactory")
                ?.replace("{InstanceURLFactory}", prefix + kind + "InstanceURLFactory")
                ?.replace("{FailingURLFactory}", prefix + kind + "FailingURLFactory")
        }
        try {
            System.clearProperty(property)
            for (template in templates) {
                val original = run(false, props(template, false)); val actual = run(true, props(template, true))
                assertEquals(original.results, actual.results, template)
                assertEquals(original.closes, actual.closes)
                assertEquals(original.stdout, actual.stdout); assertEquals(original.stderr, actual.stderr)
            }
            System.setProperty(property, "help,URLHandler")
            for ((template, readFail, closeFail) in listOf(Triple("mock={Factory}\nbad line\n", false, false),
                Triple("mock={Factory}\n", true, false), Triple("mock={Factory}\n", false, true))) {
                val original = run(false, props(template, false), readFail, closeFail)
                val actual = run(true, props(template, true), readFail, closeFail)
                assertEquals(original.results, actual.results)
                assertEquals(original.closes, actual.closes); assertEquals(original.stdout, actual.stdout); assertEquals(original.stderr, actual.stderr)
            }
        } finally { if (saved == null) System.clearProperty(property) else System.setProperty(property, saved) }
    }
    @Test fun configuredProtocolFeedsFullLocaleResourceScanning() {
        val saved = System.getProperty("ICUDebug")
        try {
            System.clearProperty("ICUDebug")
            val results = mutableListOf<Set<String>>()
            val streamHandler = object : URLStreamHandler() { override fun openConnection(url: URL): URLConnection = error("must not open") }
            val url = URL(null, "mock:/locales", streamHandler)
            val baseName = "icu4k-test/custom-protocol"
            for (port in listOf(false, true)) {
                val kind = if (port) "Port" else "Original"
                Loader("mock=io.github.heapy.icu4k.impl.${kind}URLFactory\n", false, false, "$baseName/", url).use { loader ->
                    if (port) {
                        val type = loader.loadClass("io.github.heapy.icu4k.impl.ICUResourceLoader")
                        val resourceLoader = loader.loadClass("io.github.heapy.icu4k.impl.ICUClassLoaderResourceLoader")
                            .getConstructor(ClassLoader::class.java).newInstance(loader)
                        val companion = loader.loadClass("io.github.heapy.icu4k.impl.ICUResourceBundle").getField("Companion").get(null)
                        @Suppress("UNCHECKED_CAST")
                        results.add(companion.javaClass.getMethod("getFullLocaleNameSet", String::class.java, type).invoke(companion, baseName, resourceLoader) as Set<String>)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        results.add(loader.loadClass("com.ibm.icu.impl.ICUResourceBundle").getMethod("getFullLocaleNameSet", String::class.java, ClassLoader::class.java)
                            .invoke(null, baseName, loader) as Set<String>)
                    }
                }
            }
            assertEquals(results[0], results[1]); assertEquals(setOf("", "en_US"), results[1])
        } finally { if (saved == null) System.clearProperty("ICUDebug") else System.setProperty("ICUDebug", saved) }
    }

}
