package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.locale.createLocaleCacheLock

internal actual fun createResourceCacheLock(): ResourceCacheLock = object : ResourceCacheLock {
    private val lock = createLocaleCacheLock()
    override fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
