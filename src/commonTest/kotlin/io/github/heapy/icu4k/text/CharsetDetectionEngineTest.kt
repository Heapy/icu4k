package io.github.heapy.icu4k.text

import kotlin.test.*

class CharsetDetectionEngineTest {
    private val tags = listOf("<b>", "<b><i><u><p><a>", "<<b><i><u><p><a>", "<<<b><i><u><p><a>", "<<<<<", ">>>>>")
    private fun goldens(predicate: (String) -> Boolean) {
        for (row in charsetDetectorGoldenRows.filter(predicate)) {
            val fields = row.split('|')
            val description = fields[0].split(':')
            val bytes = if (description[0] == "t") {
                val text = tags[description[2].toInt()] + "a".repeat(description[1].toInt()) + if (description[3] == "0") "" else "<z>\u0080</z>"
                ByteArray(text.length) { text[it].code.toByte() }
            } else description[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val engine = CharsetDetectionEngine().setText(if (description[0] == "p") bytes.copyOf(8000) else bytes, bytes.size)
            engine.enableInputFilter(fields[1].toBoolean())
            val mode = fields[2].toInt()
            if (mode != 0) for (name in CharsetDetectionEngine.getAllDetectableCharsets()) {
                engine.setDetectableCharset(name, mode == 1 || (mode == 2 && name == "UTF-8") || (mode == 4 && name.startsWith("IBM")))
            }
            val results = engine.detectAll()
            assertEquals(fields[7], results.joinToString(",") { "${it.name}:${it.confidence}:${it.language}" }, fields[0])
            assertEquals(results.firstOrNull(), engine.detect(), fields[0])
            assertEquals(fields[3].toInt(), engine.filteredLength, fields[0])
            assertEquals(fields[4].toBoolean(), engine.c1Bytes, fields[0])
            val input = engine.mungeInput()
            assertEquals(fields[5].toInt(), input.filtered.copyOf(input.filteredLength).contentHashCode(), fields[0])
            assertEquals(fields[6].toInt(), engine.byteStats.contentHashCode(), fields[0])
        }
    }
    @Test fun recognitionRegistryAndConfigurationGoldens() = goldens { it.startsWith("h:") }
    @Test fun streamBackingCapacityGoldens() = goldens { it.startsWith("p:") }
    @Test fun shortAndLongMarkupFallbackGoldens() = goldens { it.startsWith("t:") && !it.startsWith("t:8001:") }
    @Test fun filteringBeyondEightKilobytesGoldens() = goldens { it.startsWith("t:8001:") }
    @Test fun independentInstancesAndConfigurationSnapshots() {
        val first = CharsetDetectionEngine()
        val second = CharsetDetectionEngine()
        val names = CharsetDetectionEngine.getAllDetectableCharsets()
        assertEquals(28, names.size)
        assertEquals(24, first.getDetectableCharsets().size)
        names.fill("changed")
        assertEquals("UTF-8", CharsetDetectionEngine.getAllDetectableCharsets()[0])
        first.getDetectableCharsets().fill("changed")
        assertEquals("UTF-8", first.getDetectableCharsets()[0])
        assertFalse(first.enableInputFilter(true))
        assertTrue(first.enableInputFilter(false))
        assertFalse(second.inputFilterEnabled())
        for (name in first.getDetectableCharsets()) first.setDetectableCharset(name, false)
        assertEquals(0, first.getDetectableCharsets().size)
        assertEquals(24, second.getDetectableCharsets().size)
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
        second.setText(bytes)
        assertEquals(100, second.detect()?.confidence)
        bytes.fill(0)
        assertNotEquals(CharsetCandidate("UTF-8", 100, null), second.detect())
        assertNull(first.setText(bytes).detect())
    }
}
