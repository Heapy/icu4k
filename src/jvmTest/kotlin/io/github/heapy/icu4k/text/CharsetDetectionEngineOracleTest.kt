@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.CharsetDetector as SourceDetector
import kotlin.random.Random
import kotlin.test.*

class CharsetDetectionEngineOracleTest {
    private fun field(name: String) = SourceDetector::class.java.getDeclaredField(name).apply { isAccessible = true }
    private val filtered = field("fInputBytes")
    private val filteredLength = field("fInputLen")
    private val stats = field("fByteStats")
    private val c1 = field("fC1Bytes")
    private val length = field("fRawLength")
    private fun compare(source: SourceDetector, port: CharsetDetectionEngine) {
        val expected = source.detectAll().map { CharsetCandidate(it.name, it.confidence, it.language) }
        assertEquals(expected, port.detectAll().toList())
        assertEquals(expected.firstOrNull(), port.detect())
        assertEquals(filteredLength.getInt(source), port.filteredLength)
        assertEquals(c1.getBoolean(source), port.c1Bytes)
        assertContentEquals(stats.get(source) as ShortArray, port.byteStats)
        assertContentEquals((filtered.get(source) as ByteArray).copyOf(port.filteredLength), port.mungeInput().filtered.copyOf(port.filteredLength))
        assertContentEquals(source.detectableCharsets, port.getDetectableCharsets())
        assertEquals(source.inputFilterEnabled(), port.inputFilterEnabled())
    }
    @Test fun registryConfigurationAndReverseTieOrder() {
        assertContentEquals(SourceDetector.getAllDetectableCharsets(), CharsetDetectionEngine.getAllDetectableCharsets())
        val source = SourceDetector().setText(byteArrayOf())
        val port = CharsetDetectionEngine().setText(byteArrayOf())
        compare(source, port)
        for (name in SourceDetector.getAllDetectableCharsets()) {
            for (enabled in listOf(false, true, true, false)) {
                source.setDetectableCharset(name, enabled); port.setDetectableCharset(name, enabled)
                compare(source, port)
            }
        }
        for (name in listOf(null, "", "utf-8", "windows-1252", "unknown")) {
            val expected = assertFailsWith<IllegalArgumentException> { source.setDetectableCharset(name, true) }
            assertEquals(expected.message, assertFailsWith<IllegalArgumentException> { port.setDetectableCharset(name, true) }.message)
        }
        assertNull(source.detect()); assertNull(port.detect())
    }
    @Test fun randomBytesTagFilteringAndMutableInputs() {
        val source = SourceDetector()
        val port = CharsetDetectionEngine()
        val random = Random(8000)
        repeat(1400) { index ->
            val bytes = ByteArray(random.nextInt(200)) { random.nextInt(256).toByte() }
            if (index % 3 == 0) for (name in SourceDetector.getAllDetectableCharsets()) {
                val enabled = random.nextBoolean(); source.setDetectableCharset(name, enabled); port.setDetectableCharset(name, enabled)
            }
            val filter = random.nextBoolean()
            assertEquals(source.enableInputFilter(filter), port.enableInputFilter(filter))
            source.setDeclaredEncoding(if (index % 2 == 0) "UTF-8" else null)
            port.declaredEncoding = if (index % 2 == 0) "UTF-8" else null
            source.setText(bytes); port.setText(bytes)
            compare(source, port)
            bytes.fill(0x20); compare(source, port)
        }
    }
    @Test fun exactFilterFallbacksLongInputAndStreamCapacity() {
        val bodies = listOf("", "a".repeat(99), "a".repeat(100), "a".repeat(601), "a".repeat(7999), "a".repeat(8000), "a".repeat(8001))
        val tags = listOf("<b>", "<b><i><u><p><a>", "<<b><i><u><p><a>", "<<<b><i><u><p><a>", "<".repeat(5), ">".repeat(5))
        for (body in bodies) for (tag in tags) for (tail in listOf("", "<z>\u0080</z>", "\u0080")) for (filter in listOf(false, true)) {
            val bytes = (tag + body + tail).toByteArray(Charsets.ISO_8859_1)
            val source = SourceDetector().setText(bytes); source.enableInputFilter(filter)
            val port = CharsetDetectionEngine().setText(bytes); port.enableInputFilter(filter)
            compare(source, port)
        }
        for (hex in listOf("", "00", "fffe", "feff", "efbbbf", "0000feff", "6162")) {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val padded = bytes.copyOf(8000)
            val source = SourceDetector().setText(padded); length.setInt(source, bytes.size)
            compare(source, CharsetDetectionEngine().setText(padded, bytes.size))
        }
    }
}
