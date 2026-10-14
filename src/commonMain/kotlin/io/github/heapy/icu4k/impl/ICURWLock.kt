// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import kotlin.concurrent.atomics.AtomicReference
import kotlin.jvm.JvmField

expect class IllegalMonitorStateException(message: String?) : RuntimeException

open class ICURWLock {
    private val backend = createICURWLockBackend()
    private val monitor = createTextTrieMapLock(this)
    private val stats = AtomicReference<Stats?>(null)
    class Stats internal constructor() {
        @JvmField var _rc = 0
        @JvmField var _mrc = 0
        @JvmField var _wrc = 0
        @JvmField var _wc = 0
        @JvmField var _wwc = 0
        internal constructor(other: Stats) : this() {
            _rc = other._rc; _mrc = other._mrc; _wrc = other._wrc; _wc = other._wc; _wwc = other._wwc
        }
        override fun toString(): String = " rc: $_rc mrc: $_mrc wrc: $_wrc wc: $_wc wwc: $_wwc"
    }
    open fun resetStats(): Stats? = monitor.withLock { stats.load().also { stats.store(Stats()) } }
    open fun clearStats(): Stats? = monitor.withLock { stats.load().also { stats.store(null) } }
    open fun getStats(): Stats? = monitor.withLock { stats.load()?.let(::Stats) }
    open fun acquireRead() {
        if (stats.load() != null) monitor.withLock {
            val current = stats.load()!!
            current._rc++
            if (backend.readCount > 0) current._mrc++
            if (backend.writeLocked) current._wrc++
        }
        backend.acquireRead()
    }
    open fun releaseRead() = backend.releaseRead()
    open fun acquireWrite() {
        if (stats.load() != null) monitor.withLock {
            val current = stats.load()!!
            current._wc++
            if (backend.readCount > 0 || backend.writeLocked) current._wwc++
        }
        backend.acquireWrite()
    }
    open fun releaseWrite() = backend.releaseWrite()
}
internal interface ICURWLockBackend {
    val readCount: Int
    val writeLocked: Boolean
    fun acquireRead()
    fun releaseRead()
    fun acquireWrite()
    fun releaseWrite()
}
internal expect fun createICURWLockBackend(): ICURWLockBackend
internal interface RWMonitor {
    val threadId: Int
    fun <T> withLock(block: () -> T): T
    fun await()
    fun signalAll()
}
internal class PortableRWLock(private val monitor: RWMonitor) : ICURWLockBackend {
    private val readers = HashMap<Int, Int>()
    private var reads = 0
    private var writer: Int? = null
    private var writes = 0
    private var waitingWriters = 0
    override val readCount: Int get() = monitor.withLock { reads }
    override val writeLocked: Boolean get() = monitor.withLock { writer != null }
    override fun acquireRead(): Unit = monitor.withLock {
        val id = monitor.threadId
        while ((writer != null && writer != id) || (waitingWriters > 0 && writer != id && (readers[id] ?: 0) == 0)) monitor.await()
        if (reads == 65535) throw Error("Maximum lock count exceeded")
        readers[id] = (readers[id] ?: 0) + 1; reads++
    }
    override fun releaseRead() = monitor.withLock {
        val id = monitor.threadId
        val count = readers[id] ?: throw IllegalMonitorStateException("attempt to unlock read lock, not locked by current thread")
        if (count == 1) readers.remove(id) else readers[id] = count - 1
        reads--; monitor.signalAll()
    }
    override fun acquireWrite(): Unit = monitor.withLock {
        val id = monitor.threadId
        waitingWriters++
        try {
            while ((writer != null && writer != id) || (writer != id && reads != 0)) monitor.await()
            if (writes == 65535) throw Error("Maximum lock count exceeded")
            writer = id; writes++
        } finally { waitingWriters-- }
    }
    override fun releaseWrite() = monitor.withLock {
        if (writer != monitor.threadId) throw IllegalMonitorStateException(null)
        if (--writes == 0) writer = null
        monitor.signalAll()
    }
}
