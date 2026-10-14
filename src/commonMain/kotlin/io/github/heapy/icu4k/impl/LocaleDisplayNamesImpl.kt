// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.text.LocaleDisplayNames
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.util.UResourceBundle
import kotlin.jvm.JvmStatic

open class LocaleDisplayNamesImpl(locale: ULocale?, vararg contexts: DisplayContext?) : LocaleDisplayNames() {
    private val components = LocaleDisplayNameComponents(locale, *contexts)
    constructor(locale: ULocale?, dialect: DialectHandling?) : this(locale, if (dialect == DialectHandling.STANDARD_NAMES) DisplayContext.STANDARD_NAMES else DisplayContext.DIALECT_NAMES, DisplayContext.CAPITALIZATION_NONE)
    override fun getLocale(): ULocale = components.locale
    override fun getDialectHandling(): DialectHandling = if (components.dialectNames) DialectHandling.DIALECT_NAMES else DialectHandling.STANDARD_NAMES
    override fun getContext(type: DisplayContext.Type?): DisplayContext? = components.getContext(type)
    override fun localeDisplayName(locale: ULocale?): String? = components.localeDisplayName(locale)
    override fun localeDisplayName(localeId: String?): String? = components.localeDisplayName(localeId)
    override fun languageDisplayName(lang: String?): String? = components.languageDisplayName(lang)
    override fun scriptDisplayName(script: String?): String? = components.scriptDisplayName(script)
    override fun scriptDisplayNameInContext(script: String?): String? = components.scriptDisplayNameInContext(script)
    override fun scriptDisplayName(scriptCode: Int): String? = components.scriptDisplayName(scriptCode)
    override fun regionDisplayName(region: String?): String? = components.regionDisplayName(region)
    override fun variantDisplayName(variant: String?): String? = components.variantDisplayName(variant)
    override fun keyDisplayName(key: String?): String? = components.keyDisplayName(key)
    override fun keyValueDisplayName(key: String?, value: String?): String? = components.keyValueDisplayName(key, value)
    override fun getUiListCompareWholeItems(localeSet: Set<ULocale?>?, comparator: Comparator<UiListItem>?): MutableList<UiListItem> = components.getUiListCompareWholeItems(localeSet, comparator, getContext(DisplayContext.Type.CAPITALIZATION))
    open class DataTable internal constructor(val nullIfNotFound: Boolean) {
        open fun getLocale(): ULocale = ULocale.ROOT
        open fun get(tableName: String?, code: String?): String? = get(tableName, null, code)
        open fun get(tableName: String?, subTableName: String?, code: String?): String? = if (nullIfNotFound) null else code
    }
    internal open class ICUDataTable(path: String?, locale: ULocale?, nullIfNotFound: Boolean) : DataTable(nullIfNotFound) {
        private val bundle = UResourceBundle.getBundleInstance(path, locale!!.getBaseName()) as ICUResourceBundle
        override fun getLocale(): ULocale = bundle.getULocale()
        override fun get(tableName: String?, subTableName: String?, code: String?): String? = ICUResourceTableAccess.getTableString(bundle, tableName, subTableName, code, if (nullIfNotFound) null else code)
    }
    internal abstract class DataTables {
        abstract fun get(locale: ULocale?, nullIfNotFound: Boolean): DataTable
        companion object {
            fun load(className: String): DataTables = try { loadLocaleDisplayNameDataTables(className) } catch (_: Throwable) {
                object : DataTables() { override fun get(locale: ULocale?, nullIfNotFound: Boolean): DataTable = DataTable(nullIfNotFound) }
            }
        }
    }
    internal abstract class ICUDataTables(private val path: String?) : DataTables() {
        override fun get(locale: ULocale?, nullIfNotFound: Boolean): DataTable = ICUDataTable(path, locale, nullIfNotFound)
    }
    private object LangDataTables { val impl = DataTables.load("com.ibm.icu.impl.ICULangDataTables") }
    private object RegionDataTables { val impl = DataTables.load("com.ibm.icu.impl.ICURegionDataTables") }
    enum class DataTableType { LANG, REGION }
    companion object {
        internal fun languageData(locale: ULocale?, noSubstitute: Boolean): DataTable = LangDataTables.impl.get(locale, noSubstitute)
        internal fun regionData(locale: ULocale?, noSubstitute: Boolean): DataTable = RegionDataTables.impl.get(locale, noSubstitute)
        @JvmStatic fun haveData(type: DataTableType?): Boolean = when (type!!) {
            DataTableType.LANG -> LangDataTables.impl is ICUDataTables
            DataTableType.REGION -> RegionDataTables.impl is ICUDataTables
        }
        private val cache = Cache()
        private val lock = createTextTrieMapLock(cache)
        @JvmStatic fun getInstance(locale: ULocale?, dialect: DialectHandling?): LocaleDisplayNames? = lock.withLock { cache.get(locale, dialect) }
        @JvmStatic fun getInstance(locale: ULocale?, vararg contexts: DisplayContext?): LocaleDisplayNames? = lock.withLock { cache.get(locale, contexts) }
    }
    private class Cache {
        private var locale: ULocale? = null
        private var dialect: DialectHandling? = null
        private var capitalization: DisplayContext? = null
        private var length: DisplayContext? = null
        private var substitute: DisplayContext? = null
        private var value: LocaleDisplayNames? = null
        fun get(requested: ULocale?, handling: DialectHandling?): LocaleDisplayNames? {
            if (!(handling == dialect && capitalization == DisplayContext.CAPITALIZATION_NONE && length == DisplayContext.LENGTH_FULL && substitute == DisplayContext.SUBSTITUTE && requested!!.equals(locale))) {
                locale = requested; dialect = handling; capitalization = DisplayContext.CAPITALIZATION_NONE; length = DisplayContext.LENGTH_FULL; substitute = DisplayContext.SUBSTITUTE
                value = LocaleDisplayNamesImpl(requested, handling)
            }
            return value
        }
        fun get(requested: ULocale?, contexts: Array<out DisplayContext?>): LocaleDisplayNames? {
            var handling = DialectHandling.STANDARD_NAMES; var caps = DisplayContext.CAPITALIZATION_NONE
            var len = DisplayContext.LENGTH_FULL; var subs = DisplayContext.SUBSTITUTE
            for (nullable in contexts) {
                val context = nullable!!
                when (context.type()) {
                    DisplayContext.Type.DIALECT_HANDLING -> handling = if (context.value() == DisplayContext.STANDARD_NAMES.value()) DialectHandling.STANDARD_NAMES else DialectHandling.DIALECT_NAMES
                    DisplayContext.Type.CAPITALIZATION -> caps = context
                    DisplayContext.Type.DISPLAY_LENGTH -> len = context
                    DisplayContext.Type.SUBSTITUTE_HANDLING -> subs = context
                }
            }
            if (!(handling == dialect && caps == capitalization && len == length && subs == substitute && requested!!.equals(locale))) {
                locale = requested; dialect = handling; capitalization = caps; length = len; substitute = subs
                value = LocaleDisplayNamesImpl(requested, *contexts)
            }
            return value
        }
    }
}

internal class ICULangDataTables : LocaleDisplayNamesImpl.ICUDataTables(ICUData.ICU_LANG_BASE_NAME)
internal class ICURegionDataTables : LocaleDisplayNamesImpl.ICUDataTables(ICUData.ICU_REGION_BASE_NAME)
internal expect fun loadLocaleDisplayNameDataTables(className: String): LocaleDisplayNamesImpl.DataTables
