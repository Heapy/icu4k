// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.atomics.AtomicReference

internal interface LocaleDefaultHost {
    fun get(category: ULocale.Category? = null): Any
    fun set(category: ULocale.Category?, token: Any)
    fun fromLocale(token: Any): ULocale
    fun toLocale(locale: ULocale): Any
}
internal expect fun localeDefaultHost(): LocaleDefaultHost

/** A process-local locale boundary for hosts without mutable system locale APIs. */
internal class PortableLocaleDefaultHost : LocaleDefaultHost {
    private val general = AtomicReference<String?>(null)
    private val categories = ULocale.Category.entries.map { AtomicReference<String?>(null) }
    override fun get(category: ULocale.Category?): Any =
        (if (category == null) general.load() else categories[category.ordinal].load())
            ?: general.load() ?: initialPlatformLocaleName()
    override fun set(category: ULocale.Category?, token: Any) {
        if (category == null) {
            general.store(token as String)
            categories.forEach { it.store(token) }
        } else categories[category.ordinal].store(token as String)
    }
    override fun fromLocale(token: Any): ULocale = ULocale(token as String)
    override fun toLocale(locale: ULocale): Any = locale.getName()
}

internal object LocaleDefaults {
    private data class Entry(val token: Any, val locale: ULocale)
    private val lock = createAvailabilityLock()
    private val host = localeDefaultHost()
    private val general = AtomicReference<Entry?>(null)
    private val categories = arrayOfNulls<Entry>(ULocale.Category.entries.size)
    init {
        val token = host.get()
        general.store(Entry(token, host.fromLocale(token)))
        for (category in ULocale.Category.entries) {
            val categoryToken = host.get(category)
            categories[category.ordinal] = Entry(categoryToken, host.fromLocale(categoryToken))
        }
    }
    fun get(): ULocale {
        val entry = general.load() ?: return ULocale.ROOT
        if (entry.token == host.get()) return entry.locale
        return lock.withLock {
            val token = host.get()
            val current = general.load()!!
            if (current.token == token) current.locale else host.fromLocale(token).also {
                general.store(Entry(token, it))
            }
        }
    }
    fun get(category: ULocale.Category): ULocale = lock.withLock {
        val index = category.ordinal
        val current = categories[index] ?: return@withLock ULocale.ROOT
        val token = host.get(category)
        if (current.token == token) current.locale else {
            // Publish the new host token before conversion, as ICU4J does.
            categories[index] = Entry(token, current.locale)
            host.fromLocale(token).also { categories[index] = Entry(token, it) }
        }
    }
    fun set(locale: ULocale) = lock.withLock {
        val token = host.toLocale(locale)
        host.set(null, token)
        general.store(Entry(token, locale))
        for (category in ULocale.Category.entries) set(category, locale)
    }
    fun set(category: ULocale.Category?, locale: ULocale) = lock.withLock {
        val token = host.toLocale(locale)
        val index = category!!.ordinal
        categories[index] = Entry(token, locale)
        host.set(category, token)
    }
}
