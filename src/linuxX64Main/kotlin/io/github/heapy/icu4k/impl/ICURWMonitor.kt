@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class)
package io.github.heapy.icu4k.impl

import kotlinx.cinterop.*
import kotlin.native.concurrent.Worker
import kotlin.native.ref.createCleaner
import platform.posix.*

internal actual fun createNativeRWMonitor(): RWMonitor = object : RWMonitor {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>().ptr
    private val condition = nativeHeap.alloc<pthread_cond_t>().ptr
    init {
        val result = pthread_mutex_init(mutex, null)
        if (result != 0) {
            nativeHeap.free(condition); nativeHeap.free(mutex)
            error("pthread_mutex_init: $result")
        }
        val conditionResult = pthread_cond_init(condition, null)
        if (conditionResult != 0) {
            pthread_mutex_destroy(mutex)
            nativeHeap.free(condition); nativeHeap.free(mutex)
            error("pthread_cond_init: $conditionResult")
        }
    }
    @Suppress("unused")
    private val cleaner = createCleaner(mutex to condition) { (mutex, condition) ->
        pthread_cond_destroy(condition); pthread_mutex_destroy(mutex)
        nativeHeap.free(condition); nativeHeap.free(mutex)
    }
    override val threadId: Int get() = Worker.current.id
    override fun <T> withLock(block: () -> T): T {
        check(pthread_mutex_lock(mutex) == 0)
        try { return block() } finally { check(pthread_mutex_unlock(mutex) == 0) }
    }
    override fun await() { check(pthread_cond_wait(condition, mutex) == 0) }
    override fun signalAll() { check(pthread_cond_broadcast(condition) == 0) }
}
