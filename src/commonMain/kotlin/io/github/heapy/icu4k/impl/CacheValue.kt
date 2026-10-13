// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ICUException
import kotlin.concurrent.atomics.AtomicReference

/** Original strong/reclaimable/null cache value semantics. */
abstract class CacheValue<V : Any> {
    enum class Strength { STRONG, SOFT }
    open fun isNull(): Boolean = false
    abstract fun get(): V?
    abstract fun resetIfCleared(value: V?): V?
    private class NullValue : CacheValue<Any>() {
        override fun isNull(): Boolean = true
        override fun get(): Any? = null
        override fun resetIfCleared(value: Any?): Any? {
            if (value != null) throw ICUException("resetting a null value to a non-null value")
            return null
        }
    }
    private class StrongValue<V : Any>(private val value: V) : CacheValue<V>() {
        override fun get(): V = value
        override fun resetIfCleared(value: V?): V = this.value
    }
    private class SoftValue<V : Any>(value: V) : CacheValue<V>() {
        private val ref = AtomicReference(createCacheReference(value))
        override fun get(): V? = ref.load().get()
        override fun resetIfCleared(value: V?): V? {
            while (true) {
                val previous = ref.load()
                previous.get()?.let { return it }
                val replacement = createCacheReference(value)
                if (ref.compareAndSet(previous, replacement)) return value
            }
        }
    }
    companion object {
        private val strength = AtomicReference<Strength?>(Strength.SOFT)
        private val nullValue = NullValue()
        fun setStrength(value: Strength?) { strength.store(value) }
        fun futureInstancesWillBeStrong(): Boolean = strength.load() == Strength.STRONG
        @Suppress("UNCHECKED_CAST")
        fun <V : Any> getInstance(value: V?): CacheValue<V> = when {
            value == null -> nullValue as CacheValue<V>
            futureInstancesWillBeStrong() -> StrongValue(value)
            else -> SoftValue(value)
        }
    }
}
internal interface CacheReference<V : Any> { fun get(): V?; fun clear() }
internal expect fun <V : Any> createCacheReference(value: V?): CacheReference<V>
