package io.github.heapy.icu4k.impl

actual class IllegalMonitorStateException actual constructor(message: String?) : RuntimeException(message)
internal actual fun createICURWLockBackend(): ICURWLockBackend = PortableRWLock(object : RWMonitor {
    override val threadId: Int get() = 0
    override fun <T> withLock(block: () -> T): T = block()
    override fun await(): Nothing = throw IllegalStateException("Read-to-write lock upgrade is unsupported on the JavaScript event loop")
    override fun signalAll() { }
})
