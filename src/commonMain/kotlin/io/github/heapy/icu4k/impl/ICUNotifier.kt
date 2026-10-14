// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

expect interface EventListener

/** Identity-based listener registration and asynchronous snapshot notification. */
abstract class ICUNotifier {
    private val lock = createResourceCacheLock()
    private var listeners: MutableList<EventListener>? = null
    private var dispatcher: NotifierDispatcher? = null
    fun addListener(listener: EventListener?) {
        val value = listener ?: throw NullPointerException()
        if (!acceptsListener(value)) throw IllegalStateException("Listener invalid for this notifier.")
        lock.withLock {
            val list = listeners ?: ArrayList<EventListener>().also { listeners = it }
            if (list.none { it === value }) list.add(value)
        }
    }
    fun removeListener(listener: EventListener?) {
        val value = listener ?: throw NullPointerException()
        lock.withLock {
            val list = listeners ?: return@withLock
            val index = list.indexOfFirst { it === value }
            if (index >= 0) {
                list.removeAt(index)
                if (list.isEmpty()) listeners = null
            }
        }
    }
    fun notifyChanged() {
        lock.withLock {
            val list = listeners ?: return@withLock
            val queue = dispatcher ?: createNotifierDispatcher(::notifyListener).also { dispatcher = it }
            queue.enqueue(list.toTypedArray())
        }
    }
    protected abstract fun acceptsListener(listener: EventListener): Boolean
    protected abstract fun notifyListener(listener: EventListener)
}
internal interface NotifierDispatcher { fun enqueue(listeners: Array<EventListener>) }
internal expect fun createNotifierDispatcher(notify: (EventListener) -> Unit): NotifierDispatcher

/** JS/Native serial event pump. A callback failure permanently stops this queue. */
internal class NotifierEventPump(private val notify: (EventListener) -> Unit, private val schedule: (() -> Unit) -> Unit) : NotifierDispatcher {
    private val lock = createResourceCacheLock()
    private val queue = ArrayList<Array<EventListener>>()
    private var scheduled = false
    private var stopped = false
    override fun enqueue(listeners: Array<EventListener>) {
        lock.withLock {
            queue.add(listeners)
            if (!scheduled && !stopped) {
                scheduled = true
                schedule(::drain)
            }
        }
    }
    private fun drain() {
        try {
            while (true) {
                val batch = lock.withLock {
                    if (queue.isEmpty()) { scheduled = false; null } else queue.removeAt(0)
                } ?: return
                for (listener in batch) notify(listener)
            }
        } catch (failure: Throwable) {
            lock.withLock { stopped = true }
            throw failure
        }
    }
}
