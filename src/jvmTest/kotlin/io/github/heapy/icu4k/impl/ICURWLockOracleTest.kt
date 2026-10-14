package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICURWLockOracleTest {
    @Test fun statisticsAndInvalidReleaseMatchSource() {
        val j = com.ibm.icu.impl.ICURWLock(); val k = ICURWLock()
        assertNull(j.resetStats()); assertNull(k.resetStats())
        for (operation in listOf(0, 0, 1, 1, 2, 2, 0, 3, 3, 1)) {
            when (operation) {
                0 -> { j.acquireRead(); k.acquireRead() }
                1 -> { j.releaseRead(); k.releaseRead() }
                2 -> { j.acquireWrite(); k.acquireWrite() }
                3 -> { j.releaseWrite(); k.releaseWrite() }
            }
            assertEquals(j.stats.toString(), k.getStats().toString())
        }
        fun outcome(block: () -> Unit): Any? = try { block(); null } catch (e: Exception) { e.javaClass.simpleName to e.message }
        assertEquals(outcome { j.releaseRead() }, outcome { k.releaseRead() })
        assertEquals(outcome { j.releaseWrite() }, outcome { k.releaseWrite() })
        assertEquals(j.clearStats().toString(), k.clearStats().toString())
    }
}
