package io.github.heapy.icu4k.impl

internal actual fun createTextTrieMapLock(owner: Any): ResourceCacheLock = object : ResourceCacheLock {
    override fun <T> withLock(block: () -> T): T = synchronized(owner, block)
}
