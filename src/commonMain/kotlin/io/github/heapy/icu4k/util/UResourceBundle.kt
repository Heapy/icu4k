// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.*
import kotlin.concurrent.atomics.AtomicReference
import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.locale.LocaleReadOnlySet

/** Common resource base and ICU/Java root-type factory dispatch. */
abstract class UResourceBundle {
    protected var parentBundle: UResourceBundle? = null
    abstract fun getULocale(): ULocale
    protected abstract fun getLocaleID(): String
    protected abstract fun getBaseName(): String
    protected abstract fun getParent(): UResourceBundle?
    protected open fun setParent(parent: UResourceBundle?) { parentBundle = parent }
    protected open fun resourceClassName(): String = resourceBundleClassName(this)

    open fun getBinary(): BinaryBuffer = throw UResourceTypeMismatchException("")
    open fun getBinary(ba: ByteArray?): ByteArray = throw UResourceTypeMismatchException("")
    open fun getString(): String = throw UResourceTypeMismatchException("")
    open fun getStringArray(): Array<String> = throw UResourceTypeMismatchException("")
    open fun getIntVector(): IntArray = throw UResourceTypeMismatchException("")
    open fun getInt(): Int = throw UResourceTypeMismatchException("")
    open fun getUInt(): Int = throw UResourceTypeMismatchException("")

    open operator fun get(aKey: String): UResourceBundle = findTopLevel(aKey) ?: run {
        val fullName = ICUResourceBundleReader.getFullName(getBaseName(), getLocaleID())
        throw MissingResourceException("Can't find resource for bundle $fullName, key $aKey", resourceClassName(), aKey)
    }
    protected open fun findTopLevel(aKey: String): UResourceBundle? {
        var resource: UResourceBundle? = this
        while (resource != null) {
            resource.handleGet(aKey, null, this)?.let { return it }
            resource = resource.getParent()
        }
        return null
    }
    open operator fun get(index: Int): UResourceBundle {
        handleGet(index, null, this)?.let { return it }
        getParent()?.let { return it[index] }
        throw MissingResourceException("Can't find resource for bundle ${resourceClassName()}, key ${getKey()}", resourceClassName(), getKey())
    }
    protected open fun findTopLevel(index: Int): UResourceBundle? {
        var resource: UResourceBundle? = this
        while (resource != null) {
            resource.handleGet(index, null, this)?.let { return it }
            resource = resource.getParent()
        }
        return null
    }
    open fun getString(index: Int): String {
        val value = get(index) as ICUResourceBundle
        if (value.getType() == STRING) return value.getString()
        throw UResourceTypeMismatchException("")
    }
    fun getString(aKey: String): String = getObject(aKey) as String
    @Suppress("UNCHECKED_CAST")
    fun getStringArray(aKey: String): Array<String> = getObject(aKey) as Array<String>
    fun getObject(aKey: String): Any = handleGetObject(aKey) ?: parentBundle?.getObject(aKey)
        ?: throw MissingResourceException("Can't find resource for bundle ${resourceClassName()}, key $aKey", resourceClassName(), aKey)
    open fun getKeys(): Enumeration<String> = enumeration(keySet())
    open fun keySet(): Set<String> {
        if (!isTopLevelResource()) return handleKeySet()
        topLevelKeySet()?.let { return it }
        val keys = HashSet<String>()
        parentBundle?.let { keys.addAll(it.keySet()) }
        keys.addAll(handleKeySet())
        return LocaleReadOnlySet({ keys }, null).also { cacheTopLevelKeySet(it) }
    }
    fun containsKey(key: String): Boolean {
        var resource: UResourceBundle? = this
        while (resource != null) {
            if (key in resource.handleKeySet()) return true
            resource = resource.parentBundle
        }
        return false
    }
    protected open fun handleKeySet(): Set<String> = emptySet()
    internal open fun topLevelKeySet(): Set<String>? = null
    internal open fun cacheTopLevelKeySet(keys: Set<String>) {}
    open fun getSize(): Int = 1
    open fun getType(): Int = NONE
    open fun getVersion(): VersionInfo? = null
    open fun getIterator(): UResourceBundleIterator = UResourceBundleIterator(this)
    open fun getKey(): String? = null
    protected open fun handleGet(aKey: String, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle? = null
    protected open fun handleGet(index: Int, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle? = null
    protected open fun handleGetStringArray(): Array<String>? = null
    protected open fun handleGetKeys(): Enumeration<String>? = null
    protected open fun isTopLevelResource(): Boolean = true
    protected open fun handleGetObject(aKey: String): Any? = handleGetObjectImpl(aKey, this)
    private fun handleGetObjectImpl(aKey: String, requested: UResourceBundle): Any {
        resolveObject(aKey, requested)?.let { return it }
        getParent()?.let { return it.handleGetObjectImpl(aKey, requested) }
        throw MissingResourceException("Can't find resource for bundle ${resourceClassName()}, key $aKey", resourceClassName(), aKey)
    }
    private fun resolveObject(aKey: String, requested: UResourceBundle): Any? {
        if (getType() == STRING) return getString()
        val value = handleGet(aKey, null, requested) ?: return null
        if (value.getType() == STRING) return value.getString()
        try {
            if (value.getType() == ARRAY) return value.handleGetStringArray()
        } catch (_: UResourceTypeMismatchException) { return value }
        return value
    }
    companion object {
        private enum class RootType { MISSING, ICU, JAVA }
        private val rootTypes = AtomicReference<Map<String, RootType>>(emptyMap())
        private fun setRootType(baseName: String, type: RootType) {
            while (true) {
                val previous = rootTypes.load()
                if (rootTypes.compareAndSet(previous, previous + (baseName to type))) return
            }
        }
        private val nullBinaryLoader = ICUResourceLoader { throw NullPointerException("Resource loader is null") }
        private fun binaryLoader(baseName: String, loader: ICUResourceLoader?): ICUResourceLoader = loader
            ?: if (baseName.startsWith(ICUData.ICU_BASE_NAME)) defaultICUResourceLoader() else nullBinaryLoader
        private fun getRootType(baseName: String, loader: ICUResourceLoader?): RootType {
            rootTypes.load()[baseName]?.let { return it }
            val rootLocale = if ('.' in baseName) "" else "root"
            val type = try {
                ICUResourceBundle.getBundleInstance(baseName, rootLocale, binaryLoader(baseName, loader), true)
                RootType.ICU
            } catch (_: MissingResourceException) {
                try {
                    ResourceBundleWrapper.getBundleInstance(baseName, rootLocale, loader, true)
                    RootType.JAVA
                } catch (_: MissingResourceException) { RootType.MISSING }
            }
            setRootType(baseName, type)
            return type
        }
        @kotlin.jvm.JvmStatic
        @kotlin.jvm.JvmOverloads
        fun getBundleInstance(baseName: String?, localeName: String?, loader: ICUResourceLoader? = defaultICUResourceLoader(), disableFallback: Boolean = false): UResourceBundle =
            instantiateBundle(baseName, localeName, loader, disableFallback)
        @kotlin.jvm.JvmStatic
        fun getBundleInstance(locale: ULocale?): UResourceBundle = getBundleInstance(ICUData.ICU_BASE_NAME,
            locale?.getBaseName() ?: ULocale.getBaseName(resourceDefaultLocaleName()))
        @kotlin.jvm.JvmStatic
        fun getBundleInstance(baseName: String?): UResourceBundle = getBundleInstance(baseName ?: ICUData.ICU_BASE_NAME,
            ULocale.getBaseName(resourceDefaultLocaleName()))
        @kotlin.jvm.JvmStatic
        @kotlin.jvm.JvmOverloads
        fun getBundleInstance(baseName: String?, locale: ULocale?, loader: ICUResourceLoader? = defaultICUResourceLoader()): UResourceBundle =
            getBundleInstance(baseName ?: ICUData.ICU_BASE_NAME, locale?.getBaseName() ?: ULocale.getBaseName(resourceDefaultLocaleName()), loader)
        internal fun instantiateBundle(baseName: String?, localeName: String?, loader: ICUResourceLoader?, disableFallback: Boolean): UResourceBundle {
            val base = baseName!! // ConcurrentHashMap rejects a null key before probing in ICU4J.
            val type = getRootType(base, loader)
            val locale = localeName!!
            return when (type) {
                RootType.ICU -> ICUResourceBundle.getBundleInstance(base, locale, binaryLoader(base, loader), disableFallback)
                RootType.JAVA -> ResourceBundleWrapper.getBundleInstance(base, locale, loader, disableFallback)
                RootType.MISSING -> try {
                    ICUResourceBundle.getBundleInstance(base, locale, binaryLoader(base, loader), disableFallback).also { setRootType(base, RootType.ICU) }
                } catch (_: MissingResourceException) {
                    ResourceBundleWrapper.getBundleInstance(base, locale, loader, disableFallback).also { setRootType(base, RootType.JAVA) }
                }
            }
        }

        const val NONE = -1
        const val STRING = 0
        const val BINARY = 1
        const val TABLE = 2
        const val INT = 7
        const val ARRAY = 8
        const val INT_VECTOR = 14
    }
}

internal expect fun resourceBundleClassName(bundle: UResourceBundle): String
