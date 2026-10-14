@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.heapy.icu4k.impl

import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.concurrent.Worker
import platform.posix.usleep
import kotlin.test.*

class ICUNotifierNativeTest {
    private class Listener(val id: Int) : EventListener
    @Test fun backgroundDeliveryRetainsSnapshotsAndHandlesReentrancy() {
        val main = Worker.current.id
        val callbackWorker = AtomicInt(main)
        val count = AtomicInt(0)
        val seen = mutableListOf<Int>()
        val a = Listener(1); val b = Listener(2)
        val notifier = object : ICUNotifier() {
            override fun acceptsListener(listener: EventListener): Boolean = listener is Listener
            override fun notifyListener(listener: EventListener) {
                callbackWorker.store(Worker.current.id)
                seen.add((listener as Listener).id)
                if (seen.size == 1) { removeListener(b); notifyChanged() }
                count.store(seen.size)
            }
        }
        notifier.addListener(a); notifier.addListener(a); notifier.addListener(b)
        notifier.notifyChanged()
        var attempts = 0
        while (count.load() < 3 && attempts++ < 5000) usleep(1000u)
        assertEquals(3, count.load()); assertEquals(listOf(1, 2, 1), seen)
        assertNotEquals(main, callbackWorker.load())
        notifier.removeListener(a)
    }
}
