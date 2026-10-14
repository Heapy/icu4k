package io.github.heapy.icu4k.impl

internal actual fun createResourceCacheLock(): ResourceCacheLock = object : ResourceCacheLock {
    private val monitor = Any()
    override fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
