// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.timezone

import io.github.heapy.icu4k.impl.CacheReference
import io.github.heapy.icu4k.text.LocaleDisplayNames
internal actual fun timeZoneGenericNamesAssertionsEnabled(): Boolean = TimeZoneGenericNamesCore::class.java.desiredAssertionStatus()
internal actual fun createGenericNameMap(): GenericNameMap = object : GenericNameMap {
    private val map = java.util.concurrent.ConcurrentHashMap<String,String>()
    override fun get(key: String): String? = map[key]
    override fun putIfAbsent(key: String,value: String): String? = map.putIfAbsent(key,value)
    override fun isEmpty(): Boolean = map.isEmpty()
}
internal actual fun createGenericDisplayNamesReference(value: LocaleDisplayNames): CacheReference<LocaleDisplayNames> = object : CacheReference<LocaleDisplayNames> {
    private val reference = java.lang.ref.WeakReference(value)
    override fun get(): LocaleDisplayNames? = reference.get()
    override fun clear() = reference.clear()
}
