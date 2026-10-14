@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
package io.github.heapy.icu4k.impl

internal actual fun sortLocaleUiItems(items: MutableList<LocaleUiListItem>, comparator: Comparator<LocaleUiListItem>?) {
    (items as java.util.List<LocaleUiListItem>).sort(comparator)
}
