package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import kotlin.test.*

class FullLocaleNamesTest {
    @Test fun scannedNamesUseOriginalFilterAndStopFallback() {
        var scans = 0
        val loader = object : ICUResourceLoader {
            override fun getData(fullName: String): BinaryBuffer? = error("scan should avoid fallback: $fullName")
            override fun visitResources(directory: String, visitor: (String) -> Unit) {
                assertEquals("icu4k-test/full-names-scan/", directory)
                scans++
                listOf("en.res", "en_US.res", "root.res", "res_index.res", "pool.res", "a.res", "abcd.res", "ab.res", "abc.res", "x_private.res", "data.txt").forEach(visitor)
            }
        }
        val names = ICUResourceBundle.getFullLocaleNameSet("icu4k-test/full-names-scan", loader)
        assertEquals(setOf("", "en", "en_US", "ab", "abc", "x_private"), names)
        assertSame(names, ICUResourceBundle.getFullLocaleNameSet("icu4k-test/full-names-scan", ICUResourceLoader { error("cached") }))
        assertEquals(1, scans)
        assertFailsWith<UnsupportedOperationException> { (names as MutableSet<String>).add("fr") }
    }
    @Test fun listNamesKeepWhitespaceCommentsAndUnfilteredIDs() {
        val text = "#comment\r\nen\rroot\n\n #not-comment\na\nlongname\r\nfr\n"
        val loader = ICUResourceLoader {
            assertEquals("icu4k-test/full-names-list/fullLocaleNames.lst", it)
            BinaryBuffer(text.encodeToByteArray())
        }
        assertEquals(setOf("", "en", "fr", " #not-comment", "a", "longname"),
            ICUResourceBundle.getFullLocaleNameSet("icu4k-test/full-names-list/", loader))
    }
    @Test fun emptyScanAndListUseInstalledIndexAndAlwaysIncludeRoot() {
        val base = "icu4k-test/full-names-index"
        val bytes = BundleFixture.write(mapOf("InstalledLocales" to mapOf("en" to "", "root" to "", "abcd" to "")))
        val loader = ICUResourceLoader { name -> when (name) {
            "$base/fullLocaleNames.lst" -> BinaryBuffer("#empty\r\n\n".encodeToByteArray())
            "$base/res_index.res" -> BinaryBuffer(bytes)
            else -> null
        } }
        assertEquals(setOf("", "en", "abcd"), ICUResourceBundle.getFullLocaleNameSet(base, loader))
        assertEquals(setOf(""), ICUResourceBundle.getFullLocaleNameSet("icu4k-test/full-names-missing", ICUResourceLoader { null }))
        assertEquals(setOf("", "ja", "Burmese_graphclust_model5_heavy", "Thai_graphclust_model4_heavy"), ICUResourceBundle.getFullLocaleNameSet(ICUData.ICU_BRKITR_BASE_NAME))
    }
    @Test fun asciiLinesUseReplacementAndDoNotChangeBufferCursor() {
        val buffer = BinaryBuffer(byteArrayOf(0, 65, -61, -87, 13, 10, 66, 13, 67))
        buffer.position = 1
        val lines = mutableListOf<String>()
        ICUResourceLoader { buffer }.readAsciiLines("test", lines::add)
        assertEquals(listOf("A\uFFFD\uFFFD", "B", "C"), lines)
        assertEquals(1, buffer.position)
    }
}
