package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import com.ibm.icu.util.OutputInt as JavaOutput
import io.github.heapy.icu4k.util.OutputInt
import kotlin.random.Random
import kotlin.test.*

@Suppress("DEPRECATION")
class UnicodeSetCodePointSpanOracleTest {
    @Test fun unfrozenForwardCountsAndReverseFallbackMatch() {
        val forward = JavaSet::class.java.getDeclaredMethod("spanCodePointsAndCount", CharSequence::class.java, Int::class.javaPrimitiveType, JavaSet.SpanCondition::class.java, JavaOutput::class.java).apply { isAccessible = true }
        val random = Random(313)
        repeat(200) {
            val j = JavaSet().add(65, 90).add(0xd800, 0xdfff).add(0x10000, 0x10200)
            val k = UnicodeSet().add(65, 90).add(0xd800, 0xdfff).add(0x10000, 0x10200)
            if (random.nextBoolean()) { j.complement(); k.complement() }
            val text = CharArray(30) { if (random.nextBoolean()) random.nextInt(0xd800, 0xe100).toChar() else random.nextInt(256).toChar() }.concatToString()
            for (condition in UnicodeSet.SpanCondition.entries) {
                val jc = JavaSet.SpanCondition.valueOf(condition.name)
                for (start in text.indices) {
                    val jo = JavaOutput(99); val ko = OutputInt(99)
                    assertEquals(forward.invoke(j, text, start, jc, jo), k.spanCodePointsAndCount(text, start, condition, ko))
                    assertEquals(jo.value, ko.value)
                    assertEquals(forward.invoke(j, text, start, jc, null), k.spanCodePointsAndCount(text, start, condition, null))
                }
                for (limit in 1..text.length) assertEquals(j.spanBack(text, limit, jc), k.spanCodePointsBack(text, limit, condition))
            }
        }
    }
}
