@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.heapy.icu4k.impl

import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import platform.posix.usleep
import kotlin.test.*

class ICURWLockNativeTest {
    private class State {
        val lock = ICURWLock()
        val readerEntered = AtomicInt(0)
        val releaseReader = AtomicInt(0)
        val writerEntered = AtomicInt(0)
        var counter = 0
    }
    private fun await(predicate: () -> Boolean) {
        var attempts = 0
        while (!predicate() && attempts++ < 5000) usleep(1000u)
        assertTrue(predicate())
    }
    @Test fun readersOverlapWritersWaitAndOwnershipIsEnforced() {
        val state = State(); val reader = Worker.start(); val writer = Worker.start()
        state.lock.resetStats(); state.lock.acquireRead()
        var mainReadHeld = true
        try {
            val reading = reader.execute(TransferMode.SAFE, { state }) { s ->
                s.lock.acquireRead()
                try {
                    s.readerEntered.store(1)
                    while (s.releaseReader.load() == 0) usleep(1000u)
                } finally { s.lock.releaseRead() }
            }
            await { state.readerEntered.load() == 1 }
            val writing = writer.execute(TransferMode.SAFE, { state }) { s ->
                s.lock.acquireWrite()
                try { s.writerEntered.store(1) } finally { s.lock.releaseWrite() }
            }
            await { state.lock.getStats()!!._wc == 1 }
            assertEquals(0, state.writerEntered.load())
            state.lock.releaseRead(); mainReadHeld = false
            assertEquals(0, state.writerEntered.load())
            state.releaseReader.store(1)
            reading.result; writing.result
            assertEquals(1, state.writerEntered.load())
            assertEquals(1, state.lock.getStats()!!._mrc); assertEquals(1, state.lock.getStats()!!._wwc)
            state.lock.acquireRead(); mainReadHeld = true
            val wrongThread = reader.execute(TransferMode.SAFE, { state }) { s ->
                try { s.lock.releaseRead(); false } catch (_: IllegalMonitorStateException) { true }
            }
            assertTrue(wrongThread.result)
        } finally {
            state.releaseReader.store(1)
            if (mainReadHeld) state.lock.releaseRead()
            reader.requestTermination().result; writer.requestTermination().result
        }
    }
    @Test fun writersProtectSharedUpdates() {
        val state = State(); val workers = List(4) { Worker.start() }
        try {
            val tasks = workers.map { worker -> worker.execute(TransferMode.SAFE, { state }) { s ->
                repeat(500) {
                    s.lock.acquireWrite()
                    try { s.counter++ } finally { s.lock.releaseWrite() }
                }
            } }
            tasks.forEach { it.result }
            assertEquals(2000, state.counter)
        } finally { workers.forEach { it.requestTermination().result } }
    }
}
