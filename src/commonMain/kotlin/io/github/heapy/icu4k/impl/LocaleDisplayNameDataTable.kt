// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle

/** LocaleDisplayNamesImpl data tables, extracted pending the display-name facade. */
internal open class LocaleDisplayNameDataTable(val nullIfNotFound: Boolean) {
    open fun getLocale(): ULocale = ULocale.ROOT
    open fun get(tableName: String?, code: String?): String? = get(tableName, null, code)
    open fun get(tableName: String?, subTableName: String?, code: String?): String? = if (nullIfNotFound) null else code
}
internal open class ResourceLocaleDisplayNameDataTable(path: String?, locale: ULocale?, nullIfNotFound: Boolean) : LocaleDisplayNameDataTable(nullIfNotFound) {
    private val bundle = UResourceBundle.getBundleInstance(path, locale!!.getBaseName()) as ICUResourceBundle
    override fun getLocale(): ULocale = bundle.getULocale()
    override fun get(tableName: String?, subTableName: String?, code: String?): String? = ICUResourceTableAccess.getTableString(bundle, tableName, subTableName, code, if (nullIfNotFound) null else code)
}
