// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField

internal typealias LocaleUiListItem = io.github.heapy.icu4k.text.LocaleDisplayNames.UiListItem
internal expect fun sortLocaleUiItems(items: MutableList<LocaleUiListItem>, comparator: Comparator<LocaleUiListItem>?)
internal fun sortLocaleUiItemsPortable(items: MutableList<LocaleUiListItem>, comparator: Comparator<LocaleUiListItem>?) {
    if (comparator != null) items.sortWith(comparator)
    else if (items.size > 1) throw ClassCastException("LocaleUiListItem cannot be cast to Comparable")
}
