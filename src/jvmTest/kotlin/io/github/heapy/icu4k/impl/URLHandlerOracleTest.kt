package io.github.heapy.icu4k.impl

import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.*

class URLHandlerOracleTest {
    private fun trace(url: URL, recurse: Boolean, strip: Boolean): List<String>? {
        val expected = com.ibm.icu.impl.URLHandler.get(url)
        val actual = URLHandler.get(url)
        if (expected == null) { assertNull(actual); return null }
        assertNotNull(actual)
        val j = mutableListOf<String>(); val k = mutableListOf<String>()
        expected.guide(com.ibm.icu.impl.URLHandler.URLVisitor(j::add), recurse, strip)
        actual.guide(URLHandler.URLVisitor(k::add), recurse, strip)
        assertEquals(j, k, "$url/$recurse/$strip")
        return k
    }
    @Test fun filesJarWsjarAndDefaultFailuresMatchOriginal() {
        val temp = Files.createTempDirectory("icu4k-url space")
        try {
            Files.createDirectories(temp.resolve("nested/deep"))
            Files.write(temp.resolve("a.res"), byteArrayOf()); Files.write(temp.resolve("nested/b.res"), byteArrayOf())
            Files.write(temp.resolve("nested/deep/c.res"), byteArrayOf())
            val jar = temp.resolve("test.jar")
            JarOutputStream(Files.newOutputStream(jar)).use { output ->
                for (name in listOf("base", "base/", "base/a.res", "base/nested/", "base/nested/b.res", "baseball.res", "other.res")) {
                    output.putNextEntry(JarEntry(name)); if (!name.endsWith('/')) output.write(1); output.closeEntry()
                }
            }
            val handler = object : URLStreamHandler() { override fun openConnection(url: URL): URLConnection = error("must not open") }
            val urls = listOf(temp.toUri().toURL(), temp.resolve("a.res").toUri().toURL(),
                URL("jar:${jar.toUri()}!/base/"), URL("jar:${jar.toUri()}!/base"), URL("jar:${jar.toUri()}!/"),
                URL(null, "wsjar:${jar.toUri()}!/base/", handler), temp.resolve("missing").toUri().toURL(),
                URL("file:/invalid%zz"), URL("file:${temp}?query"), URL(null, "mock:/unsupported", handler))
            for (url in urls) for (recurse in listOf(false, true)) for (strip in listOf(false, true)) trace(url, recurse, strip)
            val file = URLHandler.get(temp.resolve("a.res").toUri().toURL())!!
            assertFailsWith<IllegalStateException> { file.guide(URLHandler.URLVisitor { error("visitor") }, true) }
            val archived = URLHandler.get(URL("jar:${jar.toUri()}!/base/"))!!
            archived.guide(URLHandler.URLVisitor { error("visitor") }, true)
            assertFailsWith<AssertionError> { archived.guide(URLHandler.URLVisitor { throw AssertionError("visitor") }, true) }
            assertNull(URLHandler.get(null as URL?))
        } finally { temp.toFile().deleteRecursively() }
    }
    @Test fun classLoaderFallbackAndNullClassMatchOriginal() {
        val thread = Thread.currentThread(); val saved = thread.contextClassLoader
        try {
            for (loader in listOf(saved, null, object : ClassLoader(null) {})) {
                thread.contextClassLoader = loader
                assertSame(com.ibm.icu.impl.ClassLoaderUtil.getClassLoader(), ClassLoaderUtil.getClassLoader())
                assertSame(com.ibm.icu.impl.ClassLoaderUtil.getClassLoader(Any::class.java), ClassLoaderUtil.getClassLoader(Any::class.java))
                assertSame(URLHandler::class.java.classLoader, ClassLoaderUtil.getClassLoader(URLHandler::class.java))
            }
            assertFailsWith<NullPointerException> { ClassLoaderUtil.getClassLoader(null) }
        } finally { thread.contextClassLoader = saved }
    }
}
