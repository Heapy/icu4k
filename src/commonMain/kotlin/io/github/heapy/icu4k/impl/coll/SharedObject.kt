// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl.coll

import kotlin.concurrent.atomics.AtomicInt

/** GC owns storage; the atomic count determines whether an owner may mutate in place. */
internal open class SharedObject : SharedObjectCloneBase() {
    private var refCount = AtomicInt(0)
    public override fun clone(): SharedObject {
        val result = (cloneHost() as SharedObject?) ?: cloneNative()
        result.refCount = AtomicInt(0)
        return result
    }
    /** Native subclasses provide a typed shallow copy, as for other open ICU clone bases. */
    protected open fun cloneNative(): SharedObject {
        if (this::class != SharedObject::class) throw UnsupportedOperationException("Native SharedObject subclasses must supply a typed clone")
        return SharedObject()
    }
    fun addRef() { refCount.fetchAndAdd(1) }
    fun removeRef() { refCount.fetchAndAdd(-1) }
    fun getRefCount(): Int = refCount.load()
    fun deleteIfZeroRefCount() {}

    class Reference<T : SharedObject>(r: T?) : SharedObjectCloneBase() {
        private val state = SharedReferenceState(r)
        @Suppress("unused") private val cleanup = registerSharedReferenceCleanup(this, state)
        init { r?.addRef() }
        public override fun clone(): Reference<T> = Reference(state.ref)
        fun readOnly(): T? = state.ref
        fun copyOnWrite(): T {
            val r = state.ref!!
            if (r.getRefCount() <= 1) return r
            @Suppress("UNCHECKED_CAST") val r2 = r.clone() as T
            r.removeRef(); state.ref = r2; r2.addRef()
            return r2
        }
        fun clear() { state.clear() }
    }
}
internal class SharedReferenceState<T : SharedObject>(var ref: T?) {
    fun clear() { if (ref != null) { ref!!.removeRef(); ref = null } }
}
internal expect abstract class SharedObjectCloneBase() {
    protected abstract fun clone(): Any
    protected fun cloneHost(): Any?
}
internal expect fun registerSharedReferenceCleanup(owner: Any, state: SharedReferenceState<*>): Any?
