package io.github.heapy.icu4k.impl

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ICUNotifierOracleTest {
    private class Listener(val id: Int) : EventListener {
        override fun equals(other: Any?): Boolean = other is Listener
        override fun hashCode(): Int = 0
    }
    @Test fun orderedIdentitySnapshotsMatchSource() {
        fun run(original: Boolean): List<Int> {
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val done = CountDownLatch(6)
            val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())
            fun notify(listener: EventListener) {
                val id = (listener as Listener).id
                if (seen.isEmpty()) { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) }
                seen.add(id); done.countDown()
            }
            val j = object : com.ibm.icu.impl.ICUNotifier() {
                override fun acceptsListener(l: EventListener): Boolean = l is Listener
                override fun notifyListener(l: EventListener) = notify(l)
            }
            val k = object : ICUNotifier() {
                override fun acceptsListener(listener: EventListener): Boolean = listener is Listener
                override fun notifyListener(listener: EventListener) = notify(listener)
            }
            fun add(l: EventListener) { if (original) j.addListener(l) else k.addListener(l) }
            fun remove(l: EventListener) { if (original) j.removeListener(l) else k.removeListener(l) }
            fun changed() { if (original) j.notifyChanged() else k.notifyChanged() }
            val a = Listener(1); val b = Listener(2); val c = Listener(3)
            add(a); add(a); add(b); changed()
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            try { remove(Listener(1)); changed(); remove(a); add(c); changed() } finally { release.countDown() }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            remove(b); remove(c)
            return seen.toList()
        }
        assertEquals(listOf(1, 2, 1, 2, 2, 3), run(true))
        assertEquals(run(true), run(false))
    }
    @Test fun uncheckedCallbackFailureTerminatesNotificationThread() {
        fun run(original: Boolean) {
            val failure = IllegalStateException("listener")
            val died = CountDownLatch(1)
            val thread = java.util.concurrent.atomic.AtomicReference<Thread>()
            var calls = 0
            fun notify(listener: EventListener) {
                calls++
                val current = Thread.currentThread(); thread.set(current)
                current.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, thrown ->
                    assertSame(failure, thrown); died.countDown()
                }
                throw failure
            }
            val j = object : com.ibm.icu.impl.ICUNotifier() {
                override fun acceptsListener(l: EventListener): Boolean = true
                override fun notifyListener(l: EventListener) = notify(l)
            }
            val k = object : ICUNotifier() {
                override fun acceptsListener(listener: EventListener): Boolean = true
                override fun notifyListener(listener: EventListener) = notify(listener)
            }
            if (original) { j.addListener(Listener(1)); j.addListener(Listener(2)); j.notifyChanged() }
            else { k.addListener(Listener(1)); k.addListener(Listener(2)); k.notifyChanged() }
            assertTrue(died.await(10, TimeUnit.SECONDS))
            thread.get().join(10000)
            assertFalse(thread.get().isAlive); assertTrue(thread.get().isDaemon)
            if (original) j.notifyChanged() else k.notifyChanged()
            assertEquals(1, calls)
        }
        run(true); run(false)
    }

}
