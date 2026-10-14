@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
package io.github.heapy.icu4k.impl

import kotlinx.cinterop.*
import kotlin.native.ref.createCleaner
import platform.posix.*

internal actual fun createAvailabilityLock(): ResourceCacheLock = PosixAvailabilityLock()
private class PosixAvailabilityLock : ResourceCacheLock {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().ptr
    init {
        try {
            val result = memScoped {
                val attributes = alloc<pthread_mutexattr_t>()
                check(pthread_mutexattr_init(attributes.ptr) == 0)
                try {
                    check(pthread_mutexattr_settype(attributes.ptr, PTHREAD_MUTEX_RECURSIVE.toInt()) == 0)
                    pthread_mutex_init(mutex, attributes.ptr)
                } finally { pthread_mutexattr_destroy(attributes.ptr) }
            }
            check(result == 0) { "pthread_mutex_init: $result" }
        } catch (failure: Throwable) { nativeHeap.free(mutex); throw failure }
    }
    @Suppress("unused")
    private val cleaner = createCleaner(mutex) { pointer -> pthread_mutex_destroy(pointer); nativeHeap.free(pointer) }
    override fun <T> withLock(block: () -> T): T {
        check(pthread_mutex_lock(mutex) == 0)
        try { return block() } finally { check(pthread_mutex_unlock(mutex) == 0) }
    }
}
