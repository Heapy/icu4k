// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField

/** LocaleDisplayNames.UiListItem, extracted pending the public display-name facade. */
internal open class LocaleUiListItem(
    @JvmField val minimized: ULocale?,
    @JvmField val modified: ULocale?,
    @JvmField val nameInDisplayLocale: String?,
    @JvmField val nameInSelf: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocaleUiListItem) return false
        return nameInDisplayLocale!!.equals(other.nameInDisplayLocale) && nameInSelf!!.equals(other.nameInSelf) &&
            minimized!!.equals(other.minimized) && modified!!.equals(other.modified)
    }
    override fun hashCode(): Int = modified!!.hashCode() xor nameInDisplayLocale!!.hashCode()
    override fun toString(): String = "{$minimized, $modified, $nameInDisplayLocale, $nameInSelf}"
    companion object {
        fun getComparator(comparator: Comparator<Any?>?, inSelf: Boolean): Comparator<LocaleUiListItem> = Comparator { a, b ->
            val result = if (inSelf) comparator!!.compare(a.nameInSelf, b.nameInSelf) else comparator!!.compare(a.nameInDisplayLocale, b.nameInDisplayLocale)
            if (result != 0) result else a.modified!!.compareTo(b.modified!!)
        }
    }
}
internal expect fun sortLocaleUiItems(items: MutableList<LocaleUiListItem>, comparator: Comparator<LocaleUiListItem>?)
internal fun sortLocaleUiItemsPortable(items: MutableList<LocaleUiListItem>, comparator: Comparator<LocaleUiListItem>?) {
    if (comparator != null) items.sortWith(comparator)
    else if (items.size > 1) throw ClassCastException("LocaleUiListItem cannot be cast to Comparable")
}
