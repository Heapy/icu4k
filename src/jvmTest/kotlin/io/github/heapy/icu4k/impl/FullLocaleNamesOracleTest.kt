package io.github.heapy.icu4k.impl

import java.io.InputStream
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.*

class FullLocaleNamesOracleTest {
    private val config = "com.ibm.icu.impl.ICUResourceBundle.skipRuntimeLocaleResourceScan"
    private val parent = com.ibm.icu.impl.ICUResourceBundle::class.java.classLoader
    private fun compare(base: String, loader: ClassLoader): Set<String> {
        val expected = com.ibm.icu.impl.ICUResourceBundle.getFullLocaleNameSet(base, loader)
        val actual = ICUResourceBundle.getFullLocaleNameSet(base, ICUClassLoaderResourceLoader(loader))
        assertEquals(expected, actual, base)
        assertFailsWith<UnsupportedOperationException> { (actual as MutableSet<String>).removeIf { error("must not run") } }
        return actual
    }
    @Test fun directoryJarListAndIndexSourcesMatchOriginal() {
        val saved = System.getProperty(config)
        val temp = Files.createTempDirectory("icu4k-full-names")
        try {
            for ((i, skip) in listOf("false", "TRUE", " true ").withIndex()) {
                System.setProperty(config, skip)
                val base = "icu4k-test/full-oracle-dir-$i"
                val dir = temp.resolve(base); Files.createDirectories(dir.resolve("nested"))
                for (name in listOf("en", "fr", "root", "res_index", "pool", "a", "abcd", "ab", "abc", "x_private", "model_heavy")) Files.write(dir.resolve("$name.res"), byteArrayOf())
                Files.write(dir.resolve("nested/de.res"), byteArrayOf())
                Files.writeString(dir.resolve("fullLocaleNames.lst"), "#comment\r\nen_US\rroot\n\n #literal\na\nlongname\n")
                URLClassLoader(arrayOf(temp.toUri().toURL()), parent).use { compare(base, it) }
                val jarBase = "icu4k-test/full-oracle-jar-$i"
                val jar = temp.resolve("case-$i.jar")
                JarOutputStream(Files.newOutputStream(jar)).use { out ->
                    out.putNextEntry(JarEntry("$jarBase/")); out.closeEntry()
                    for (name in listOf("en.res", "root.res", "res_index.res", "pool.res", "a.res", "abcd.res", "abc.res", "x_private.res", "model_heavy.res", "nested/de.res", "fullLocaleNames.lst")) {
                        out.putNextEntry(JarEntry("$jarBase/$name"))
                        if (name.endsWith(".lst")) out.write("root\r\nfr_CA\n".toByteArray())
                        out.closeEntry()
                    }
                }
                URLClassLoader(arrayOf(jar.toUri().toURL()), parent).use { compare(jarBase, it) }
            }
            System.setProperty(config, "false")
            val base = "icu4k-test/full-oracle-index"
            val bytes = BundleFixture.write(mapOf("InstalledLocales" to mapOf("en" to "", "root" to "", "longname" to "")))
            val loader = object : ClassLoader(parent) {
                override fun getResourceAsStream(name: String): InputStream? = if (name == "$base/res_index.res") bytes.inputStream() else null
            }
            assertEquals(setOf("", "en", "longname"), compare(base, loader))
            assertEquals(setOf(""), compare("icu4k-test/full-oracle-missing", object : ClassLoader(parent) {}))
        } finally {
            if (saved == null) System.clearProperty(config) else System.setProperty(config, saved)
            temp.toFile().deleteRecursively()
        }
    }
    @Test fun partialListReadAndCloseIOFailuresRetainPublishedLines() {
        for (closeFails in listOf(false, true)) {
            val base = "icu4k-test/full-oracle-partial-$closeFails"
            val loader = object : ClassLoader(parent) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name != "$base/fullLocaleNames.lst") return null
                    return object : InputStream() {
                        private val data = "en\nfr\n".toByteArray(); private var position = 0
                        override fun read(): Int = if (position < data.size) data[position++].toInt() else if (closeFails) -1 else throw IOException("read")
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (position == data.size) return read()
                            val count = minOf(len, data.size - position); data.copyInto(b, off, position, position + count); position += count
                            return count
                        }
                        override fun close() { if (closeFails) throw IOException("close") }
                    }
                }
            }
            assertEquals(setOf("", "en", "fr"), compare(base, loader))
        }
    }
}
