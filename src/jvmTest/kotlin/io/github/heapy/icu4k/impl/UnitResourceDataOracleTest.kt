package io.github.heapy.icu4k.impl

import java.io.File
import java.util.jar.JarFile
import kotlin.test.*

class UnitResourceDataOracleTest {
    @Test fun completeEmbeddedUnitDirectoryMatchesThePinnedOracleJarByteForByte() {
        val source=com.ibm.icu.impl.ICUData::class.java
        val prefix=com.ibm.icu.impl.ICUData.ICU_UNIT_BASE_NAME+"/"
        JarFile(File(source.protectionDomain.codeSource.location.toURI())).use { jar ->
            val entries=jar.entries().asSequence().filter { it.name.startsWith(prefix) && it.name.endsWith(".res") }.toList()
            val names=mutableSetOf<String>()
            ICUData.visitResources(ICUData.ICU_UNIT_BASE_NAME+"/") { names.add(it) }
            assertEquals(535,entries.size)
            assertEquals(entries.map { it.name.removePrefix(prefix) }.toSet(),names)
            for(entry in entries) {
                val name=entry.name.removePrefix(prefix)
                val expected=jar.getInputStream(entry).use { it.readBytes() }
                assertContentEquals(expected,ICUData.getBytes("unit/$name"),name)
            }
        }
    }
}
