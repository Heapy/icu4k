package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.ICUData as SourceData
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile
import kotlin.test.*

class ICUCoreLocaleDataOracleTest {
    @Test fun entireMainResourceInventoryMatchesPinnedIcu4jJarBytes() {
        val prefix = SourceData.ICU_BASE_NAME + "/"
        val sourceJar = File(SourceData::class.java.protectionDomain.codeSource.location.toURI())
        val manifest = File("data/manifest.json").readText()
        val targetNames = HashSet<String>()
        ICUResourceBundleReader.embeddedLoader.visitResources(ICUData.ICU_BASE_NAME + "/") { if (it.endsWith(".res")) targetNames.add(it) }
        JarFile(sourceJar).use { jar ->
            val entries = jar.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".res") && '/' !in it.name.removePrefix(prefix) }.toList()
            assertEquals(982,entries.size)
            assertEquals(entries.map { it.name.removePrefix(prefix) }.toSet(),targetNames)
            for (entry in entries) {
                val name = entry.name.removePrefix(prefix)
                val bytes = jar.getInputStream(entry).use { it.readBytes() }
                assertContentEquals(bytes,ICUData.getBytes(name),name)
                assertContentEquals(bytes,File("data/icu78/$name").readBytes(),"disk/$name")
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                assertTrue(manifest.contains(digest),"manifest/$name")
            }
        }
    }
}
