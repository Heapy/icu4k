package io.github.heapy.icu4k.impl

actual typealias EventListener = java.util.EventListener
internal actual fun createNotifierDispatcher(notify: (EventListener) -> Unit): NotifierDispatcher = object : Thread(), NotifierDispatcher {
    private val monitor = Object()
    private val queue = ArrayList<Array<EventListener>>()
    init { isDaemon = true; start() }
    override fun enqueue(listeners: Array<EventListener>) {
        synchronized(monitor) { queue.add(listeners); monitor.notify() }
    }
    override fun run() {
        while (true) {
            try {
                val batch = synchronized(monitor) {
                    while (queue.isEmpty()) monitor.wait()
                    queue.removeAt(0)
                }
                for (listener in batch) notify(listener)
            } catch (_: InterruptedException) { }
        }
    }
}
