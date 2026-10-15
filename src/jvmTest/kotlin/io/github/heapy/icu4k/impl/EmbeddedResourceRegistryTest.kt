package io.github.heapy.icu4k.impl

import java.io.File
import kotlin.test.*

class EmbeddedResourceRegistryTest {
    @Test fun manifestResourcesHaveBytesAndRegistryEntries() {
        val text = File("data/manifest.json").readText()
        val names = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertTrue(names.isNotEmpty())
        for (name in names) {
            assertTrue(ICUData.exists(name), "$name exists")
            assertNotNull(ICUData.getBytes(name), "$name bytes")
            val folder = name.substringBeforeLast('/', "")
            val files = mutableListOf<String>()
            ICUData.visitResources(ICUData.ICU_BASE_NAME + "/" + if (folder.isEmpty()) "" else "$folder/") { files.add(it) }
            assertTrue(name.substringAfterLast('/') in files, "$name enumeration")
        }
    }
}
