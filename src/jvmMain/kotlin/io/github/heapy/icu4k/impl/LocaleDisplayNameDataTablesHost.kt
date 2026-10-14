package io.github.heapy.icu4k.impl

internal actual fun loadLocaleDisplayNameDataTables(className: String): LocaleDisplayNamesImpl.DataTables {
    val target = when (className) {
        "com.ibm.icu.impl.ICULangDataTables" -> "io.github.heapy.icu4k.impl.ICULangDataTables"
        "com.ibm.icu.impl.ICURegionDataTables" -> "io.github.heapy.icu4k.impl.ICURegionDataTables"
        else -> className
    }
    return Class.forName(target).getDeclaredConstructor().newInstance() as LocaleDisplayNamesImpl.DataTables
}
