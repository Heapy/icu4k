// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import kotlin.random.Random
import kotlin.test.*
import com.ibm.icu.impl.coll.UVector32 as Source

class UVector32OracleTest {
    @Test fun mutableBackingGrowthAndInvalidOperations() {
        val source = Source(); val target = UVector32()
        val random = Random(7803)
        repeat(3000) {
            val value = random.nextInt()
            val index = random.nextInt(-1, source.getBuffer().size + 2)
            when (random.nextInt(7)) {
                0, 1 -> { source.addElement(value); target.addElement(value) }
                2 -> assertEquals(outcome { source.insertElementAt(value, index) }, outcome { target.insertElementAt(value, index) })
                3 -> assertEquals(outcome { source.setElementAt(value, index) }, outcome { target.setElementAt(value, index) })
                4 -> assertEquals(outcome { source.elementAti(index) }, outcome { target.elementAti(index) })
                5 -> { source.removeAllElements(); target.removeAllElements() }
                6 -> { source.getBuffer()[0] = value; target.getBuffer()[0] = value }
            }
            assertEquals(source.size(), target.size())
            assertEquals(source.isEmpty, target.isEmpty())
            assertContentEquals(source.getBuffer(), target.getBuffer())
        }
        source.removeAllElements(); target.removeAllElements()
        repeat(131073) { source.addElement(it); target.addElement(it) }
        assertContentEquals(source.getBuffer(), target.getBuffer())
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: IndexOutOfBoundsException) { "bounds" }
}
