package io.github.heapy.icu4k.impl

import kotlin.test.*

class ICUNotifierTest {
    private class Listener : EventListener {
        override fun equals(other: Any?): Boolean = other is Listener
        override fun hashCode(): Int = 0
    }
    @Test fun identitySnapshotsAndReentrantNotifications() {
        val tasks = mutableListOf<() -> Unit>()
        val seen = mutableListOf<EventListener>()
        val pump = NotifierEventPump({ seen.add(it) }, { tasks.add(it) })
        val first = Listener(); val second = Listener()
        pump.enqueue(arrayOf(first, second)); pump.enqueue(arrayOf(second))
        assertTrue(seen.isEmpty()); assertEquals(1, tasks.size)
        tasks.removeAt(0)()
        assertEquals(3, seen.size); assertSame(first, seen[0]); assertSame(second, seen[1]); assertSame(second, seen[2])
    }
    @Test fun failedCallbackStopsQueuedAndFutureNotifications() {
        val tasks = mutableListOf<() -> Unit>()
        val failure = IllegalStateException("listener")
        var count = 0
        val pump = NotifierEventPump({ count++; throw failure }, { tasks.add(it) })
        pump.enqueue(arrayOf(Listener(), Listener())); pump.enqueue(arrayOf(Listener()))
        assertSame(failure, assertFailsWith<IllegalStateException> { tasks.removeAt(0)() })
        pump.enqueue(arrayOf(Listener()))
        assertEquals(1, count); assertTrue(tasks.isEmpty())
    }
    @Test fun registrationValidation() {
        val notifier = object : ICUNotifier() {
            override fun acceptsListener(listener: EventListener): Boolean = listener is Listener
            override fun notifyListener(listener: EventListener) { }
        }
        assertFailsWith<NullPointerException> { notifier.addListener(null) }
        assertFailsWith<NullPointerException> { notifier.removeListener(null) }
        assertFailsWith<IllegalStateException> { notifier.addListener(object : EventListener {}) }
        val a = Listener(); notifier.addListener(a); notifier.addListener(a)
        notifier.removeListener(Listener()); notifier.removeListener(a); notifier.removeListener(a)
    }
}
