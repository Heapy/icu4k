@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)
package io.github.heapy.icu4k.impl.locale

import kotlin.native.ref.WeakReference

// The cache key must not strongly retain the String referent being interned.
private class LocaleStringKey(value: String) {
    val chars = value.toCharArray()
    val source = WeakReference(value)
    private val hash = chars.contentHashCode()
    override fun hashCode(): Int = hash
    override fun equals(other: Any?): Boolean = this === other || other is LocaleStringKey && chars.contentEquals(other.chars)
}
private object LocaleStrings {
    val lock = createLocaleCacheLock()
    val cache = object : LocaleObjectCache<LocaleStringKey, String>() {
        override fun createObject(key: LocaleStringKey): String = key.source.get() ?: key.chars.concatToString()
    }
}
internal actual fun internLocaleString(value: String): String = LocaleStrings.lock.withLock {
    LocaleStrings.cache.get(LocaleStringKey(value))!!
}
