package io.github.heapy.icu4k.impl

import kotlin.js.Promise
import kotlin.test.*

class ICUNotifierJsTest {
    private class Listener(val id: Int) : EventListener
    @Test fun microtaskDeliveryRetainsSnapshotsAndHandlesReentrancy(): Promise<Unit> {
        val seen = mutableListOf<Int>()
        val a = Listener(1); val b = Listener(2)
        val notifier = object : ICUNotifier() {
            override fun acceptsListener(listener: EventListener): Boolean = listener is Listener
            override fun notifyListener(listener: EventListener) {
                seen.add((listener as Listener).id)
                if (seen.size == 1) { removeListener(b); notifyChanged() }
            }
        }
        notifier.addListener(a); notifier.addListener(a); notifier.addListener(b)
        notifier.notifyChanged()
        assertTrue(seen.isEmpty())
        return Promise.resolve(Unit).then {
            assertEquals(listOf(1, 2, 1), seen)
            notifier.removeListener(a)
        }
    }
}
