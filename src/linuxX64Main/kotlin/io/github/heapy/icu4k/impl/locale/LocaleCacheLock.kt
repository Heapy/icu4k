@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
package io.github.heapy.icu4k.impl.locale

import kotlinx.cinterop.*
import kotlin.native.ref.createCleaner
import platform.posix.*

internal actual fun createLocaleCacheLock(): LocaleCacheLock = PosixLocaleCacheLock()
private class PosixLocaleCacheLock : LocaleCacheLock {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().ptr
    init {
        val result = pthread_mutex_init(mutex, null)
        if (result != 0) { nativeHeap.free(mutex); error("pthread_mutex_init: $result") }
    }
    @Suppress("unused")
    private val cleaner = createCleaner(mutex) { pointer ->
        pthread_mutex_destroy(pointer)
        nativeHeap.free(pointer)
    }
    override fun <T> withLock(block: () -> T): T {
        check(pthread_mutex_lock(mutex) == 0)
        try { return block() } finally { check(pthread_mutex_unlock(mutex) == 0) }
    }
}
