package io.github.heapy.icu4k.impl.breakiter

import com.ibm.icu.impl.breakiter.DictionaryBreakEngine.DequeI as JavaDeque
import kotlin.random.Random
import kotlin.test.*

class DictionaryDequeOracleTest {
    @Test fun mixedOperationsMatchIcuDeque() {
        val peekLast = JavaDeque::class.java.getDeclaredMethod("peekLast").apply { isAccessible = true }
        val pollLast = JavaDeque::class.java.getDeclaredMethod("pollLast").apply { isAccessible = true }
        val contains = JavaDeque::class.java.getDeclaredMethod("contains", Int::class.javaPrimitiveType).apply { isAccessible = true }
        val random = Random(352)
        repeat(100) {
            val j = JavaDeque()
            val k = DictionaryBreakEngine.DequeI()
            var offers = 0
            repeat(500) {
                val value = random.nextInt()
                when (random.nextInt(8)) {
                    0 -> if (offers < 4) { j.offer(value); k.offer(value); ++offers }
                    1 -> { j.removeAllElements(); k.removeAllElements(); offers = 0 }
                    2 -> if (!j.isEmpty) assertEquals(j.pop(), k.pop())
                    3 -> if (!j.isEmpty) assertEquals(pollLast.invoke(j), k.pollLast())
                    4 -> if (!j.isEmpty) {
                        assertEquals(j.peek(), k.peek())
                        assertEquals(peekLast.invoke(j), k.peekLast())
                    }
                    5 -> assertEquals(contains.invoke(j, value), k.contains(value))
                    else -> { j.push(value); k.push(value) }
                }
                assertEquals(j.size(), k.size())
                assertEquals(j.isEmpty, k.isEmpty())
                for (index in 0 until j.size()) assertEquals(j.elementAt(index), k.elementAt(index))
                val jc = j.clone()
                val kc = k.clone()
                jc.push(value); kc.push(value)
                assertEquals(jc.pop(), kc.pop())
                assertEquals(j.size(), k.size())
            }
        }
    }
}
