package io.github.heapy.icu4k.impl

internal actual fun loadLocaleDisplayNameDataTables(className: String): LocaleDisplayNamesImpl.DataTables = when (className) {
    "com.ibm.icu.impl.ICULangDataTables", "io.github.heapy.icu4k.impl.ICULangDataTables" -> ICULangDataTables()
    "com.ibm.icu.impl.ICURegionDataTables", "io.github.heapy.icu4k.impl.ICURegionDataTables" -> ICURegionDataTables()
    else -> throw IllegalArgumentException("Unavailable data table provider: $className")
}
