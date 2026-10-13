package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UnicodeSetStringSpan as JavaSpan
import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetStringSpanMetadataOracleTest {
    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
    private fun compare(j: JavaSpan, k: UnicodeSetStringSpan) {
        for (name in listOf("maxLength16", "someRelevant", "all")) assertEquals(field(j, name), field(k, name), name)
        assertContentEquals(field(j, "spanLengths") as ShortArray?, field(k, "spanLengths") as ShortArray?)
        for (name in listOf("spanSet", "spanNotSet")) {
            val a = field(j, name) as JavaSet?
            val b = field(k, name) as UnicodeSet?
            if (a == null) { assertNull(b); continue }
            assertNotNull(b)
            assertEquals(a.isFrozen, b.isFrozen()); assertEquals(a.rangeCount, b.getRangeCount())
            for (i in 0 until a.rangeCount) { assertEquals(a.getRangeStart(i), b.getRangeStart(i)); assertEquals(a.getRangeEnd(i), b.getRangeEnd(i)) }
        }
        assertEquals(j.needsStringSpanUTF16(), k.needsStringSpanUTF16())
        for (cp in listOf(0, 65, 90, 0xd800, 0xdc00, 0xffff, 0x10000, 0x1f600, 0x10ffff)) assertEquals(j.contains(cp), k.contains(cp))
    }
    @Test fun flagsRelevantStringsMetadataAndCopyMatchIcu() {
        val random = Random(787)
        val flags = listOf(0, 1, 2, 16, 17, 18, 32, 33, 34, 64, 65, 66, 127)
        repeat(100) { seed ->
            val jset = JavaSet().add(65, 90).add(0xd800, 0xdfff)
            val kset = UnicodeSet().add(65, 90).add(0xd800, 0xdfff)
            if (seed % 2 == 0) { jset.add(0x10000, 0x10ffff); kset.add(0x10000, 0x10ffff) }
            if (seed % 5 == 0) { jset.complement(); kset.complement() }
            val strings = arrayListOf("", "AB", "ABx", "xAB", "😀A", "A😀", "A".repeat(260) + "x", "x" + "A".repeat(260))
            repeat(10) { strings.add(CharArray(random.nextInt(1, 15)) { if (random.nextBoolean()) random.nextInt(65, 95).toChar() else random.nextInt(0xd800, 0x10000).toChar() }.concatToString()) }
            for (which in flags) {
                val a = ArrayList(strings); val b = ArrayList(strings)
                val j = JavaSpan(jset, a, which)
                val k = UnicodeSetStringSpan(kset, b, which)
                assertEquals(a, b) // Constructor removes empty strings from the supplied list.
                compare(j, k)
                if (which == 127) compare(JavaSpan(j, ArrayList(a)), UnicodeSetStringSpan(k, ArrayList(b)))
            }
        }
    }
}
