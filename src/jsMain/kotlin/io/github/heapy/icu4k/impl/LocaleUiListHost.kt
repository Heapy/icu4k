package io.github.heapy.icu4k.impl

internal actual fun sortLocaleUiItems(items: MutableList<LocaleUiListItem>, comparator: Comparator<LocaleUiListItem>?) = sortLocaleUiItemsPortable(items, comparator)
