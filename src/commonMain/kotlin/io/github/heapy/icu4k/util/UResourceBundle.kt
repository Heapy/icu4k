// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.ICUResourceBundleReader
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.locale.LocaleReadOnlySet

/** Common resource base. Named factories and the concrete ICU bundle are still being ported. */
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
