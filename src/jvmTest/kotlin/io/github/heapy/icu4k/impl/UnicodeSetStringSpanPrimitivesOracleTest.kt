package com.ibm.icu.impl

import io.github.heapy.icu4k.impl.UnicodeSetStringSpan as CommonSpan
import io.github.heapy.icu4k.util.OutputInt as CommonOutput
import io.github.heapy.icu4k.text.UnicodeSet as CommonSet
import com.ibm.icu.text.UnicodeSet
import com.ibm.icu.util.OutputInt
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class UnicodeSetStringSpanPrimitivesOracleTest {
    @Test fun offsetRingAndMinimumCountsMatchIcu() {
        val type = UnicodeSetStringSpan::class.java.declaredClasses.single { it.simpleName == "OffsetList" }
        val constructor = type.getDeclaredConstructor().apply { isAccessible = true }
        fun method(name: String, vararg params: Class<*>) = type.getDeclaredMethod(name, *params).apply { isAccessible = true }
        val int = Int::class.javaPrimitiveType!!
        val setMax = method("setMaxLength", int); val clear = method("clear"); val empty = method("isEmpty")
        val add = method("addOffset", int); val addCount = method("addOffsetAndCount", int, int)
        val contains = method("containsOffset", int); val hasCount = method("hasCountAtOffset", int, int)
        val pop = method("popMinimum", OutputInt::class.java); val shift = method("shift", int)
        val random = Random(669)
        repeat(100) {
            val j = constructor.newInstance(); val k = CommonSpan.OffsetList()
            var max = 16
            repeat(500) {
                val offset = random.nextInt(1, max + 1)
                val count = random.nextInt(1, 100)
                when (random.nextInt(6)) {
                    0 -> if (!(contains.invoke(j, offset) as Boolean)) { add.invoke(j, offset); k.addOffset(offset) }
                    1 -> { addCount.invoke(j, offset, count); k.addOffsetAndCount(offset, count) }
                    2 -> { clear.invoke(j); k.clear() }
                    3 -> { max = listOf(8, 16, 31, 65)[random.nextInt(4)]; setMax.invoke(j, max); k.setMaxLength(max) }
                    4 -> if (!(empty.invoke(j) as Boolean)) {
                        val jo = OutputInt(); val ko = CommonOutput()
                        assertEquals(pop.invoke(j, jo), k.popMinimum(ko)); assertEquals(jo.value, ko.value)
                    }
                    else -> {
                        val minimum = (1..max).firstOrNull { contains.invoke(j, it) as Boolean } ?: max
                        val delta = random.nextInt(1, minimum + 1)
                        shift.invoke(j, delta); k.shift(delta)
                    }
                }
                assertEquals(empty.invoke(j), k.isEmpty())
                for (probe in 1..max) {
                    assertEquals(contains.invoke(j, probe), k.containsOffset(probe))
                    assertEquals(hasCount.invoke(j, probe, count), k.hasCountAtOffset(probe, count))
                }
            }
        }
    }
    @Test fun codePointBoundariesAndSingleSpansMatchIcu() {
        val jset = UnicodeSet().add(65, 90).add(0xd800, 0xdfff).add(0x10000, 0x10500)
        val kset = CommonSet().add(65, 90).add(0xd800, 0xdfff).add(0x10000, 0x10500)
        val random = Random(259)
        repeat(500) {
            val text = CharArray(20) { if (random.nextBoolean()) random.nextInt(0xd800, 0xe000).toChar() else random.nextInt(256).toChar() }.concatToString()
            for (start in text.indices) {
                assertEquals(UnicodeSetStringSpan.spanOne(jset, text, start, text.length - start), CommonSpan.spanOne(kset, text, start, text.length - start))
                assertEquals(UnicodeSetStringSpan.spanOneBack(jset, text, start + 1), CommonSpan.spanOneBack(kset, text, start + 1))
                val length = random.nextInt(1, text.length - start + 1)
                val match = text.substring(start, start + length)
                assertEquals(UnicodeSetStringSpan.matches16CPB(text, start, text.length, match, length), CommonSpan.matches16CPB(text, start, text.length, match, length))
            }
        }
        for (length in listOf(-1, 0, 1, 253, 254, 255, 65536, Int.MAX_VALUE)) assertEquals(UnicodeSetStringSpan.makeSpanLengthByte(length), CommonSpan.makeSpanLengthByte(length))
    }
}
