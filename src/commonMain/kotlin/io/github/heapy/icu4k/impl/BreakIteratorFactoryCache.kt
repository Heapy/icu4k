// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.BreakIterator
import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.atomics.AtomicReference

/** One reclaimable locale/prototype slot per kind, matching BreakIterator's cache. */
internal class BreakIteratorFactoryCache(private val create: (ULocale, Int) -> BreakIterator) {
    private class Entry(val locale: ULocale, iterator: BreakIterator) {
        private val prototype = iterator.clone()
        fun create(): BreakIterator = prototype.clone()
    }
    private val indices = intArrayOf(0, 1, 2, 3, 4)
    private val entries = Array(5) { AtomicReference<CacheValue<Entry>?>(null) }
    fun get(locale: ULocale?, kind: Int): BreakIterator {
        val where = locale ?: throw NullPointerException("Specified locale is null")
        checkedArrayElement(indices, kind)
        val entry = entries[kind].load()?.get()
        if (entry != null && entry.locale == where) return entry.create()
        val result = create(where, kind)
        entries[kind].store(CacheValue.getInstance(Entry(where, result)))
        return result
    }
    fun clearMatching(locale: ULocale?, kind: Int) {
        checkedArrayElement(indices, kind)
        val entry = entries[kind].load()?.get()
        if (entry != null && entry.locale == locale) entries[kind].store(null)
    }
    fun clear() { for (entry in entries) entry.store(null) }
}
