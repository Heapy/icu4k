package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import java.io.RandomAccessFile
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.*

class BinaryDataFilesOracleTest {
    @Test fun dataPathSnapshotPriorityEnumerationAndMappedUpdatesMatchOriginal() {
        val property = "com.ibm.icu.impl.ICUBinary.dataPath"
        val saved = System.getProperty(property)
        val root = Files.createTempDirectory("icu4k-dataPath")
        try {
            val first = root.resolve("first"); val second = root.resolve("second")
            Files.createDirectories(first.resolve("coll/nested")); Files.createDirectories(first.resolve("ignored.txt")); Files.createDirectories(second)
            Files.write(first.resolve("raw.bin"), byteArrayOf(10, 11, 12))
            Files.write(second.resolve("raw.bin"), byteArrayOf(90, 91))
            Files.write(first.resolve("single.res"), byteArrayOf(21))
            Files.write(first.resolve("coll/en.res"), byteArrayOf(31))
            Files.write(first.resolve("coll/nested/de.res"), byteArrayOf(41))
            Files.write(first.resolve("ignored.txt/fr.res"), byteArrayOf(51))
            Files.write(first.resolve("bad.dat"), ByteArray(32))
            Files.write(first.resolve("package.dat"), DatPackageFixture.write(mapOf(
                "package.bin" to ByteArray(16) { (60 + it).toByte() }, "root.res" to ByteArray(16) { 71 },
                "coll/fr.res" to ByteArray(16) { 72 },
                "external-test/root.res" to BundleFixture.write(mapOf("word" to "root", "onlyRoot" to "fallback")),
                "external-test/en.res" to BundleFixture.write(mapOf("word" to "english"))), little = true))
            System.setProperty(property, " $first/ : : $second/ ")
            URLClassLoader(arrayOf(ICUBinary::class.java.protectionDomain.codeSource.location,
                com.ibm.icu.impl.ICUBinary::class.java.protectionDomain.codeSource.location,
                Unit::class.java.protectionDomain.codeSource.location), null).use { isolated ->
                val j = isolated.loadClass("com.ibm.icu.impl.ICUBinary")
                val k = isolated.loadClass("io.github.heapy.icu4k.impl.ICUBinary")
                val instance = k.getField("INSTANCE").get(null)
                val get = j.getMethod("getData", String::class.java)
                val kget = k.getMethod("getData", String::class.java)
                fun buffers(key: String): Pair<ByteBuffer?, Any?> = (get.invoke(null, key) as ByteBuffer?) to kget.invoke(instance, key)
                fun commonBytes(buffer: Any): ByteArray {
                    val type = buffer.javaClass
                    val remaining = type.getMethod("getRemaining").invoke(buffer) as Int
                    return type.getMethod("readBytes", Int::class.javaPrimitiveType).invoke(buffer, remaining) as ByteArray
                }
                for (key in listOf("raw.bin", "single.res", "coll/en.res", "coll/nested/de.res", "package.bin", "ignored.txt/fr.res", "absent.bin", "bad.dat")) {
                    val (expected, actual) = buffers(key)
                    if (expected == null) assertNull(actual, key) else {
                        assertNotNull(actual)
                        assertEquals(expected.isReadOnly, actual.javaClass.getMethod("isReadOnly").invoke(actual))
                        val bytes = ByteArray(expected.remaining()); expected.get(bytes)
                        assertContentEquals(bytes, commonBytes(actual), key)
                    }
                }
                for (folder in listOf("", "coll", "coll/nested", "missing")) {
                    val expected = linkedSetOf<String>(); val actual = linkedSetOf<String>()
                    j.getMethod("addBaseNamesInFileFolder", String::class.java, String::class.java, Set::class.java).invoke(null, folder, ".res", expected)
                    k.getMethod("addBaseNamesInFileFolder", String::class.java, String::class.java, Set::class.java).invoke(instance, folder, ".res", actual)
                    assertEquals(expected, actual, folder)
                }
                val (mapped, commonMapped) = buffers("raw.bin")
                RandomAccessFile(first.resolve("raw.bin").toFile(), "rw").use { it.writeByte(88) }
                assertEquals(88, mapped!!.get(0).toInt())
                assertEquals(88.toByte(), commonMapped!!.javaClass.getMethod("getByte", Int::class.javaPrimitiveType).invoke(commonMapped, 0))
                val (packageView, commonPackageView) = buffers("package.bin")
                // Package payload offset is exposed by the Common array/view coordinate.
                val offset = commonPackageView!!.javaClass.getMethod("getArrayOffset").invoke(commonPackageView) as Int
                RandomAccessFile(first.resolve("package.dat").toFile(), "rw").use { it.seek(offset.toLong()); it.writeByte(99) }
                assertEquals(99, packageView!!.get(0).toInt())
                assertEquals(99.toByte(), commonPackageView.javaClass.getMethod("getByte", Int::class.javaPrimitiveType).invoke(commonPackageView, 0))
                val base = "${ICUData.ICU_BASE_NAME}/external-test"
                val jbundle = isolated.loadClass("com.ibm.icu.impl.ICUResourceBundle")
                val kbundle = isolated.loadClass("io.github.heapy.icu4k.impl.ICUResourceBundle")
                val loaderType = isolated.loadClass("io.github.heapy.icu4k.impl.ICUResourceLoader")
                var fallbackCalls = 0
                val emptyLoader = java.lang.reflect.Proxy.newProxyInstance(isolated, arrayOf(loaderType)) { _, method, _ ->
                    if (method.name == "getData") fallbackCalls++
                    null
                }
                val expectedBundle = jbundle.getMethod("getBundleInstance", String::class.java, String::class.java, ClassLoader::class.java, Boolean::class.javaPrimitiveType)
                    .invoke(null, base, "en", isolated, false)
                val companion = kbundle.getField("Companion").get(null)
                val actualBundle = companion.javaClass.getMethod("getBundleInstance", String::class.java, String::class.java, loaderType, Boolean::class.javaPrimitiveType)
                    .invoke(companion, base, "en", emptyLoader, false)
                for (key in listOf("word", "onlyRoot")) assertEquals(jbundle.getMethod("getStringWithFallback", String::class.java).invoke(expectedBundle, key),
                    kbundle.getMethod("getStringWithFallback", String::class.java).invoke(actualBundle, key))
                assertEquals(0, fallbackCalls)
                assertEquals(jbundle.getMethod("getFullLocaleNameSet", String::class.java, ClassLoader::class.java).invoke(null, base, isolated),
                    companion.javaClass.getMethod("getFullLocaleNameSet", String::class.java, loaderType).invoke(companion, base, emptyLoader))
                System.setProperty(property, second.toString())
                Files.write(first.resolve("new.bin"), byteArrayOf(100))
                assertNull(buffers("new.bin").first); assertNull(buffers("new.bin").second)
                assertContentEquals(byteArrayOf(88, 11, 12), commonBytes(buffers("raw.bin").second!!))
            }
        } finally {
            if (saved == null) System.clearProperty(property) else System.setProperty(property, saved)
            root.toFile().deleteRecursively()
        }
    }
    @Test fun actualMappingsKeepSlicesAliveAndAreReadOnly() {
        val path = Files.createTempFile("icu4k-mapping", ".bin")
        try {
            Files.write(path, byteArrayOf(1, 2, 3, 4))
            val buffer = binaryFileSystem()!!.mapFile(path.toString())!!
            val slice = buffer.slice(1, 2)
            assertTrue(slice.isReadOnly)
            assertFailsWith<UnsupportedOperationException> { slice.writeByte(0) }
            val snapshot = buffer.bytes; snapshot[0] = 44
            assertEquals(1, buffer.readUnsignedByte())
            RandomAccessFile(path.toFile(), "rw").use { it.seek(1); it.writeByte(55) }
            assertEquals(55, slice.readUnsignedByte())
            Files.delete(path)
            assertEquals(3, slice.readUnsignedByte())
        } finally { Files.deleteIfExists(path) }
    }
}
