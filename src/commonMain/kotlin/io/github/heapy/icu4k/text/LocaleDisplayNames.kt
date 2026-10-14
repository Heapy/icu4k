// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUConfig
import io.github.heapy.icu4k.impl.LocaleDisplayNamesImpl
import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

abstract class LocaleDisplayNames protected constructor() {
    enum class DialectHandling { STANDARD_NAMES, DIALECT_NAMES }
    abstract fun getLocale(): ULocale?
    abstract fun getDialectHandling(): DialectHandling?
    abstract fun getContext(type: DisplayContext.Type?): DisplayContext?
    abstract fun localeDisplayName(locale: ULocale?): String?
    abstract fun localeDisplayName(localeId: String?): String?
    abstract fun languageDisplayName(lang: String?): String?
    abstract fun scriptDisplayName(script: String?): String?
    open fun scriptDisplayNameInContext(script: String?): String? = scriptDisplayName(script)
    abstract fun scriptDisplayName(scriptCode: Int): String?
    abstract fun regionDisplayName(region: String?): String?
    abstract fun variantDisplayName(variant: String?): String?
    abstract fun keyDisplayName(key: String?): String?
    abstract fun keyValueDisplayName(key: String?, value: String?): String?
    open fun getUiList(localeSet: Set<ULocale?>?, inSelf: Boolean, collator: Comparator<Any?>?): List<UiListItem> = getUiListCompareWholeItems(localeSet, UiListItem.getComparator(collator, inSelf))
    abstract fun getUiListCompareWholeItems(localeSet: Set<ULocale?>?, comparator: Comparator<UiListItem>?): List<UiListItem>
    open class UiListItem(
        @JvmField val minimized: ULocale?, @JvmField val modified: ULocale?,
        @JvmField val nameInDisplayLocale: String?, @JvmField val nameInSelf: String?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UiListItem) return false
            return nameInDisplayLocale!!.equals(other.nameInDisplayLocale) && nameInSelf!!.equals(other.nameInSelf) && minimized!!.equals(other.minimized) && modified!!.equals(other.modified)
        }
        override fun hashCode(): Int = modified!!.hashCode() xor nameInDisplayLocale!!.hashCode()
        override fun toString(): String = "{$minimized, $modified, $nameInDisplayLocale, $nameInSelf}"
        companion object {
            @JvmStatic fun getComparator(comparator: Comparator<Any?>?, inSelf: Boolean): Comparator<UiListItem> = Comparator { a, b ->
                val result = if (inSelf) comparator!!.compare(a.nameInSelf, b.nameInSelf) else comparator!!.compare(a.nameInDisplayLocale, b.nameInDisplayLocale)
                if (result != 0) result else a.modified!!.compareTo(b.modified!!)
            }
        }
    }
    companion object {
        private val factory = createLocaleDisplayNamesFactory(ICUConfig.get("com.ibm.icu.text.LocaleDisplayNames.impl", "com.ibm.icu.impl.LocaleDisplayNamesImpl")!!)
        @JvmStatic fun getInstance(locale: ULocale?): LocaleDisplayNames = getInstance(locale, DialectHandling.STANDARD_NAMES)
        @JvmStatic fun getInstance(locale: ULocale?, dialect: DialectHandling?): LocaleDisplayNames = factory.get(locale, dialect) ?: LastResort(locale, arrayOf(if (dialect == DialectHandling.DIALECT_NAMES) DisplayContext.DIALECT_NAMES else DisplayContext.STANDARD_NAMES))
        @JvmStatic fun getInstance(locale: ULocale?, vararg contexts: DisplayContext?): LocaleDisplayNames = factory.get(locale, contexts) ?: LastResort(locale, contexts.copyOf())
    }
    private class LastResort(private val locale: ULocale?, private val contexts: Array<out DisplayContext?>) : LocaleDisplayNames() {
        override fun getLocale(): ULocale? = locale
        override fun getDialectHandling(): DialectHandling {
            for (context in contexts) if (context!!.type() == DisplayContext.Type.DIALECT_HANDLING && context.value() == DisplayContext.DIALECT_NAMES.ordinal) return DialectHandling.DIALECT_NAMES
            return DialectHandling.STANDARD_NAMES
        }
        override fun getContext(type: DisplayContext.Type?): DisplayContext {
            for (context in contexts) if (context!!.type() == type) return context
            return DisplayContext.STANDARD_NAMES
        }
        override fun localeDisplayName(locale: ULocale?): String = locale!!.getName()
        override fun localeDisplayName(localeId: String?): String = ULocale(localeId!!).getName()
        override fun languageDisplayName(lang: String?): String? = lang
        override fun scriptDisplayName(script: String?): String? = script
        override fun scriptDisplayName(scriptCode: Int): String? = UScript.getShortName(scriptCode)
        override fun regionDisplayName(region: String?): String? = region
        override fun variantDisplayName(variant: String?): String? = variant
        override fun keyDisplayName(key: String?): String? = key
        override fun keyValueDisplayName(key: String?, value: String?): String? = value
        override fun getUiListCompareWholeItems(localeSet: Set<ULocale?>?, comparator: Comparator<UiListItem>?): List<UiListItem> = emptyLocaleDisplayUiList()
    }
}
internal interface LocaleDisplayNamesFactory {
    fun get(locale: ULocale?, dialect: LocaleDisplayNames.DialectHandling?): LocaleDisplayNames?
    fun get(locale: ULocale?, contexts: Array<out DisplayContext?>): LocaleDisplayNames?
}
internal expect fun createLocaleDisplayNamesFactory(className: String): LocaleDisplayNamesFactory
internal class DirectLocaleDisplayNamesFactory : LocaleDisplayNamesFactory {
    override fun get(locale: ULocale?, dialect: LocaleDisplayNames.DialectHandling?): LocaleDisplayNames? = try { LocaleDisplayNamesImpl.getInstance(locale, dialect) } catch (_: Throwable) { null }
    override fun get(locale: ULocale?, contexts: Array<out DisplayContext?>): LocaleDisplayNames? = try { LocaleDisplayNamesImpl.getInstance(locale, *contexts) } catch (_: Throwable) { null }
}
internal class MissingLocaleDisplayNamesFactory : LocaleDisplayNamesFactory {
    override fun get(locale: ULocale?, dialect: LocaleDisplayNames.DialectHandling?): LocaleDisplayNames? = null
    override fun get(locale: ULocale?, contexts: Array<out DisplayContext?>): LocaleDisplayNames? = null
}
internal expect fun emptyLocaleDisplayUiList(): List<LocaleDisplayNames.UiListItem>
internal class EmptyLocaleDisplayUiList : AbstractMutableList<LocaleDisplayNames.UiListItem>() {
    override val size: Int get() = 0
    override fun get(index: Int): LocaleDisplayNames.UiListItem = throw IndexOutOfBoundsException("Index: $index")
    override fun add(index: Int, element: LocaleDisplayNames.UiListItem): Nothing = throw UnsupportedOperationException()
    override fun removeAt(index: Int): Nothing = throw UnsupportedOperationException()
    override fun set(index: Int, element: LocaleDisplayNames.UiListItem): Nothing = throw UnsupportedOperationException()
    override fun clear() { }
}
