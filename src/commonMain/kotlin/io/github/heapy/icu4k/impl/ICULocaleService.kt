// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.ULocale
import kotlin.concurrent.atomics.AtomicReference

/** Locale-aware service registry with ICU fallback and resource factories. */
open class ICULocaleService(name: String? = "") : ICUService(name) {
    open class LocaleKey protected constructor(primaryID: String?, canonicalPrimaryID: String?, canonicalFallbackID: String?, private val kind: Int) : ICUService.Key(primaryID) {
        private var variantStart = 0
        private val primary: String
        private var fallback: String?
        private var current: String?
        init {
            if (canonicalPrimaryID == null || canonicalPrimaryID.equals("root", ignoreCase = true)) {
                primary = ""; fallback = null
            } else {
                val index = canonicalPrimaryID.indexOf('@')
                if (index == 4 && canonicalPrimaryID.regionMatches(0, "root", 0, 4, ignoreCase = true)) {
                    primary = canonicalPrimaryID.substring(4); variantStart = 0; fallback = null
                } else {
                    primary = canonicalPrimaryID; variantStart = index
                    fallback = if (canonicalFallbackID == null || primary == canonicalFallbackID) "" else canonicalFallbackID
                }
            }
            current = if (variantStart == -1) primary else primary.substring(0, variantStart)
        }
        open fun prefix(): String? = if (kind == KIND_ANY) null else kind().toString()
        open fun kind(): Int = kind
        override fun canonicalID(): String? = primary
        override fun currentID(): String? = current
        override fun currentDescriptor(): String? {
            val id = currentID() ?: return null
            return buildString {
                if (kind != KIND_ANY) append(prefix())
                append('/'); append(id)
                if (variantStart != -1) append(primary.substring(variantStart))
            }
        }
        open fun canonicalLocale(): ULocale = ULocale(primary)
        open fun currentLocale(): ULocale = if (variantStart == -1) ULocale(current!!) else ULocale(current + primary.substring(variantStart))
        override fun fallback(): Boolean {
            val id = current!!
            var index = id.lastIndexOf('_')
            if (index != -1) {
                do { index-- } while (index >= 0 && id[index] == '_')
                current = id.substring(0, index + 1)
                return true
            }
            val next = fallback
            if (next != null) {
                current = next; fallback = if (next.isEmpty()) null else ""
                return true
            }
            current = null
            return false
        }
        override fun isFallbackOf(idToCheck: String?): Boolean {
            val parent = canonicalID()!!
            val child = idToCheck!!
            if (!child.startsWith(parent)) return false
            return parent.length == child.length || child[parent.length] == '_'
        }
        companion object {
            const val KIND_ANY = -1
            fun createWithCanonicalFallback(primaryID: String?, canonicalFallbackID: String?): LocaleKey? = createWithCanonicalFallback(primaryID, canonicalFallbackID, KIND_ANY)
            fun createWithCanonicalFallback(primaryID: String?, canonicalFallbackID: String?, kind: Int): LocaleKey? = primaryID?.let { LocaleKey(it, ULocale.getName(it), canonicalFallbackID, kind) }
            fun createWithCanonical(locale: ULocale?, canonicalFallbackID: String?, kind: Int): LocaleKey? = locale?.getName()?.let { LocaleKey(it, it, canonicalFallbackID, kind) }
        }
    }

    private val fallbackLocale = AtomicReference<ULocale?>(null)
    private var fallbackLocaleName: String? = null
    private val fallbackMonitor = createTextTrieMapLock(this)
    open fun validateFallbackLocale(): String? {
        val locale = ULocale.getDefault()
        if (locale !== fallbackLocale.load()) fallbackMonitor.withLock {
            if (locale !== fallbackLocale.load()) {
                fallbackLocaleName = locale.getBaseName()
                clearServiceCache()
                fallbackLocale.store(locale)
            }
        }
        return fallbackLocaleName
    }
    override fun createKey(id: String?): ICUService.Key? = LocaleKey.createWithCanonicalFallback(id, validateFallbackLocale())
    open fun createKey(id: String?, kind: Int): ICUService.Key? = LocaleKey.createWithCanonicalFallback(id, validateFallbackLocale(), kind)
    open fun createKey(locale: ULocale?, kind: Int): ICUService.Key? = LocaleKey.createWithCanonical(locale, validateFallbackLocale(), kind)
    open fun get(locale: ULocale?): Any? = get(locale, LocaleKey.KIND_ANY, null)
    open fun get(locale: ULocale?, kind: Int): Any? = get(locale, kind, null)
    open fun get(locale: ULocale?, actualReturn: Array<ULocale?>?): Any? = get(locale, LocaleKey.KIND_ANY, actualReturn)
    open fun get(locale: ULocale?, kind: Int, actualReturn: Array<ULocale?>?): Any? {
        val key = createKey(locale, kind)
        if (actualReturn == null) return getKey(key)
        val actual = arrayOfNulls<String>(1)
        val result = getKey(key, actual)
        if (result != null) {
            var descriptor = actual[0]!!
            val separator = descriptor.indexOf('/')
            if (separator >= 0) descriptor = descriptor.substring(separator + 1)
            checkedSetLocaleArrayElement(actualReturn, 0, ULocale(descriptor))
        }
        return result
    }
    open fun registerObject(obj: Any?, locale: ULocale?): Factory = registerObject(obj, locale, LocaleKey.KIND_ANY, true)
    open fun registerObject(obj: Any?, locale: ULocale?, visible: Boolean): Factory = registerObject(obj, locale, LocaleKey.KIND_ANY, visible)
    open fun registerObject(obj: Any?, locale: ULocale?, kind: Int): Factory = registerObject(obj, locale, kind, true)
    open fun registerObject(obj: Any?, locale: ULocale?, kind: Int, visible: Boolean): Factory = registerFactory(SimpleLocaleKeyFactory(obj, locale, kind, visible))
    open fun getAvailableULocales(): Array<ULocale> = getVisibleIDs().map(::ULocale).toTypedArray()

    abstract class LocaleKeyFactory protected constructor(
        @kotlin.jvm.JvmField protected val visible: Boolean,
        @kotlin.jvm.JvmField protected val name: String? = null,
    ) : Factory {
        override fun create(key: Key?, service: ICUService?): Any? {
            if (!handlesKey(key)) return null
            val localeKey = key as LocaleKey
            return handleCreate(localeKey.currentLocale(), localeKey.kind(), service)
        }
        protected open fun handlesKey(key: Key?): Boolean = key != null && getSupportedIDs().contains(key.currentID())
        override fun updateVisibleIDs(result: MutableMap<String, Factory>) {
            for (id in getSupportedIDs()) if (visible) result[id] = this else result.remove(id)
        }
        override fun getDisplayName(id: String?, locale: ULocale?): String? = if (locale == null) id else ULocale(id!!).getDisplayName(locale)
        protected open fun handleCreate(loc: ULocale?, kind: Int, service: ICUService?): Any? = null
        protected open fun isSupportedID(id: String?): Boolean = getSupportedIDs().contains(id)
        protected open fun getSupportedIDs(): Set<String> = emptySet()
        override fun toString(): String = super.toString() + (if (name != null) ", name: $name" else "") + ", visible: $visible"
        companion object { const val VISIBLE = true; const val INVISIBLE = false }
    }
    open class SimpleLocaleKeyFactory @kotlin.jvm.JvmOverloads constructor(
        private val obj: Any?, locale: ULocale?, private val kind: Int, visible: Boolean, name: String? = null,
    ) : LocaleKeyFactory(visible, name) {
        private val id = locale!!.getBaseName()
        override fun create(key: Key?, service: ICUService?): Any? {
            if (key !is LocaleKey || kind != LocaleKey.KIND_ANY && kind != key.kind() || id != key.currentID()) return null
            return obj
        }
        override fun isSupportedID(id: String?): Boolean = this.id == id
        override fun updateVisibleIDs(result: MutableMap<String, Factory>) {
            if (visible) result[id] = this else result.remove(id)
        }
        override fun toString(): String = super.toString() + ", id: $id, kind: $kind"
    }
    open class ICUResourceBundleFactory @kotlin.jvm.JvmOverloads constructor(
        @kotlin.jvm.JvmField protected val bundleName: String = ICUData.ICU_BASE_NAME,
    ) : LocaleKeyFactory(true) {
        override fun getSupportedIDs(): Set<String> = ICUResourceBundle.getFullLocaleNameSet(bundleName, loader())
        override fun updateVisibleIDs(result: MutableMap<String, Factory>) {
            for (id in ICUResourceBundle.getAvailableLocaleNameSet(bundleName, loader())) result[id] = this
        }
        override fun handleCreate(loc: ULocale?, kind: Int, service: ICUService?): Any? = ICUResourceBundle.getBundleInstance(bundleName, loc!!.getBaseName(), loader())
        protected open fun loader(): ICUResourceLoader = defaultICUResourceLoader()
        override fun toString(): String = super.toString() + ", bundle: $bundleName"
    }

}
internal expect fun checkedSetLocaleArrayElement(values: Array<ULocale?>, index: Int, value: ULocale)
