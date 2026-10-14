package io.github.heapy.icu4k.text

internal actual fun createLocaleDisplayNamesFactory(className: String): LocaleDisplayNamesFactory =
    if (className == "com.ibm.icu.impl.LocaleDisplayNamesImpl" || className == "io.github.heapy.icu4k.impl.LocaleDisplayNamesImpl") DirectLocaleDisplayNamesFactory() else MissingLocaleDisplayNamesFactory()
private val emptyUiList = EmptyLocaleDisplayUiList()
internal actual fun emptyLocaleDisplayUiList(): List<LocaleDisplayNames.UiListItem> = emptyUiList
