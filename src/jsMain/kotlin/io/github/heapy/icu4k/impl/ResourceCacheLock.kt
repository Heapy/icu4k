package io.github.heapy.icu4k.impl

internal actual fun createResourceCacheLock(): ResourceCacheLock = object : ResourceCacheLock {
    override fun <T> withLock(block: () -> T): T = block()
}
