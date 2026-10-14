package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICURWLockTest {
    @Test fun reentrantDowngradeAndStatistics() {
        val lock = ICURWLock()
        assertNull(lock.getStats()); assertNull(lock.resetStats())
        lock.acquireRead(); lock.acquireRead(); lock.releaseRead(); lock.releaseRead()
        lock.acquireWrite(); lock.acquireWrite(); lock.acquireRead()
        lock.releaseWrite(); lock.releaseWrite(); lock.releaseRead()
        assertEquals(" rc: 3 mrc: 1 wrc: 1 wc: 2 wwc: 1", lock.getStats().toString())
        val snapshot = lock.getStats()!!; snapshot._rc = 99
        assertEquals(3, lock.getStats()!!._rc)
        val prior = lock.resetStats()!!; assertEquals(3, prior._rc)
        assertEquals(0, lock.clearStats()!!._rc); assertNull(lock.getStats())
        assertFailsWith<IllegalMonitorStateException> { lock.releaseRead() }
        assertFailsWith<IllegalMonitorStateException> { lock.releaseWrite() }
    }
}
