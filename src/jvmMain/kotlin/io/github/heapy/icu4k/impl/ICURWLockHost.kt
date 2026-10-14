package io.github.heapy.icu4k.impl

actual typealias IllegalMonitorStateException = java.lang.IllegalMonitorStateException
internal actual fun createICURWLockBackend(): ICURWLockBackend = object : ICURWLockBackend {
    private val lock = java.util.concurrent.locks.ReentrantReadWriteLock()
    override val readCount: Int get() = lock.readLockCount
    override val writeLocked: Boolean get() = lock.isWriteLocked
    override fun acquireRead() = lock.readLock().lock()
    override fun releaseRead() = lock.readLock().unlock()
    override fun acquireWrite() = lock.writeLock().lock()
    override fun releaseWrite() = lock.writeLock().unlock()
}
