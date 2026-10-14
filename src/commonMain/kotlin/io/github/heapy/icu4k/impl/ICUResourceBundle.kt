// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.*
import io.github.heapy.icu4k.impl.locale.localeReadOnlySet
import kotlin.concurrent.atomics.AtomicReference

/** ICU binary bundle loading, resource lookup, locale fallback and aliases. Service/enumeration APIs are being ported. */
open class ICUResourceBundle : UResourceBundle {
    protected class WholeBundle internal constructor(
        val baseName: String,
        val localeID: String,
        internal val reader: ICUResourceBundleReader,
        internal val loader: ICUResourceLoader = ICUResourceBundleReader.embeddedLoader,
    ) {
        val ulocale = ULocale(localeID)
        internal val topLevelKeys = AtomicReference<Set<String>?>(null)
    }
    protected val wholeBundle: WholeBundle
    private val container: ICUResourceBundle?
    protected var resourceKey: String? = null
    protected constructor(wholeBundle: WholeBundle) {
        this.wholeBundle = wholeBundle
        container = null
    }
    protected constructor(container: ICUResourceBundle, key: String) {
        wholeBundle = container.wholeBundle
        this.container = container
        resourceKey = key
        parentBundle = container.parentBundle
    }
    override fun getLocaleID(): String = wholeBundle.localeID
    override fun getBaseName(): String = wholeBundle.baseName
    override fun getULocale(): ULocale = wholeBundle.ulocale
    fun isRoot(): Boolean = wholeBundle.localeID.isEmpty() || wholeBundle.localeID == "root"
    public override fun getParent(): ICUResourceBundle? = parentBundle as ICUResourceBundle?
    override fun setParent(parent: UResourceBundle?) { parentBundle = parent }
    override fun getKey(): String? = resourceKey
    override fun isTopLevelResource(): Boolean = container == null
    override fun handleGetKeys(): Enumeration<String> = enumeration(handleKeySet())
    fun getTopLevelKeySet(): Set<String>? = wholeBundle.topLevelKeys.load()
    fun setTopLevelKeySet(keys: Set<String>?) { wholeBundle.topLevelKeys.store(keys) }
    internal override fun topLevelKeySet(): Set<String>? = getTopLevelKeySet()
    internal override fun cacheTopLevelKeySet(keys: Set<String>) { setTopLevelKeySet(keys) }
    override fun equals(other: Any?): Boolean = this === other || other is ICUResourceBundle &&
        getBaseName() == other.getBaseName() && getLocaleID() == other.getLocaleID()
    override fun hashCode(): Int = 42
    internal fun get(aKey: String, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): ICUResourceBundle {
        (handleGet(aKey, aliasesVisited, requested) as ICUResourceBundle?)?.let { return it }
        getParent()?.let { return it.get(aKey, aliasesVisited, requested) }
        val fullName = ICUResourceBundleReader.getFullName(getBaseName(), getLocaleID())
        throw MissingResourceException("Can't find resource for bundle $fullName, key $aKey", resourceClassName(), aKey)
    }
    public override fun findTopLevel(index: Int): ICUResourceBundle? = super.findTopLevel(index) as ICUResourceBundle?
    public override fun findTopLevel(aKey: String): ICUResourceBundle? = super.findTopLevel(aKey) as ICUResourceBundle?
    fun at(index: Int): ICUResourceBundle? = handleGet(index, null, this) as ICUResourceBundle?
    fun at(key: String): ICUResourceBundle? = if (this is ICUResourceBundleImpl.ResourceTable) handleGet(key, null, this) as ICUResourceBundle? else null
    fun findWithFallback(path: String): ICUResourceBundle? = findResourceWithFallback(path, this, null)
    fun getWithFallback(path: String): ICUResourceBundle {
        val result = findWithFallback(path) ?: throw MissingResourceException(
            "Can't find resource for bundle ${resourceClassName()}, key ${getType()}", path, getKey())
        if (result.getType() == STRING && result.getString() == NO_INHERITANCE_MARKER)
            throw MissingResourceException("Encountered NO_INHERITANCE_MARKER", path, getKey())
        return result
    }
    fun findStringWithFallback(path: String): String? = findStringWithFallback(path, this, null)
    fun getStringWithFallback(path: String): String {
        val value = findStringWithFallback(path) ?: throw MissingResourceException(
            "Can't find resource for bundle ${resourceClassName()}, key ${getType()}", path, getKey())
        if (value == NO_INHERITANCE_MARKER) throw MissingResourceException("Encountered NO_INHERITANCE_MARKER", path, getKey())
        return value
    }
    fun getValueWithFallback(path: String): UResource.Value {
        val bundle = if (path.isEmpty()) this else findWithFallback(path) ?: throw MissingResourceException(
            "Can't find resource for bundle ${resourceClassName()}, key ${getType()}", path, getKey())
        val impl = bundle as ICUResourceBundleImpl
        return ICUResourceBundleReader.ReaderValue().also { it.reader = impl.wholeBundle.reader; it.res = impl.getResource() }
    }
    fun getAllItemsWithFallbackNoFail(path: String, sink: UResource.Sink) {
        try { getAllItemsWithFallback(path, sink) } catch (_: MissingResourceException) { }
    }
    fun getAllItemsWithFallback(path: String, sink: UResource.Sink) {
        val bundle = if (path.isEmpty()) this else findWithFallback(path) ?: throw MissingResourceException(
            "Can't find resource for bundle ${resourceClassName()}, key ${getType()}", path, getKey())
        bundle.getAllItemsWithFallback(UResource.Key(), ICUResourceBundleReader.ReaderValue(), sink, this)
    }
    fun getAllChildrenWithFallback(path: String, sink: UResource.Sink) {
        val allChildren = object : UResource.Sink() {
            override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                val table = value.getTable()
                var i = 0
                while (table.getKeyAndValue(i++, key, value)) {
                    if (value.getType() != ALIAS) {
                        sink.put(key, value, noFallback)
                        continue
                    }
                    val aliasPath = value.getAliasString()
                    var target = getAliasedResource(aliasPath, wholeBundle.loader, "", null, 0, null, null, this@ICUResourceBundle)
                    var impl = target as ICUResourceBundleImpl
                    var aliasValue = ICUResourceBundleReader.ReaderValue().also { it.reader = impl.wholeBundle.reader; it.res = impl.getResource() }
                    if (aliasValue.getType() != TABLE) {
                        sink.put(key, aliasValue, noFallback)
                        continue
                    }
                    var aliasType = TABLE
                    var tablePath = aliasPath.substring("/LOCALE/".length)
                    sink.put(key.clone(), aliasValue, noFallback)
                    while (aliasType == TABLE && target.getParent() != null) {
                        val next = target.getParent()!!.findWithFallback(tablePath)!!
                        if (next.resourceKey!!.equals(target.resourceKey)) target = next
                        else {
                            tablePath = tablePath.substring(0, tablePath.lastIndexOf('/')) + "/" + next.resourceKey
                            target = this@ICUResourceBundle.findWithFallback(tablePath)!!
                        }
                        impl = target as ICUResourceBundleImpl
                        aliasValue = ICUResourceBundleReader.ReaderValue().also { it.reader = impl.wholeBundle.reader; it.res = impl.getResource() }
                        aliasType = aliasValue.getType()
                        sink.put(key.clone(), aliasValue, noFallback)
                    }
                }
            }
        }
        getAllItemsWithFallback(path, allChildren)
    }
    private fun getAllItemsWithFallback(key: UResource.Key, value: ICUResourceBundleReader.ReaderValue, sink: UResource.Sink, requested: UResourceBundle) {
        val impl = this as ICUResourceBundleImpl
        value.reader = impl.wholeBundle.reader
        value.res = impl.getResource()
        key.setString(resourceKey ?: "")
        sink.put(key, value, parentBundle == null)
        val parent = parentBundle as ICUResourceBundle? ?: return
        val depth = getResDepth()
        val next = if (depth == 0) parent else {
            val pathKeys = Array(depth) { "" }
            getResPathKeys(pathKeys, depth)
            findResourceWithFallback(pathKeys, 0, parent, requested)
        }
        next?.getAllItemsWithFallback(key, value, sink, requested)
    }
    private fun getResDepth(): Int = if (container == null) 0 else container.getResDepth() + 1
    private fun getResPathKeys(keys: Array<String>, initialDepth: Int) {
        var depth = initialDepth
        var bundle = this
        while (depth > 0) {
            keys[--depth] = bundle.resourceKey!!
            bundle = bundle.container!!
        }
    }
    enum class OpenType { LOCALE_DEFAULT_ROOT, LOCALE_ROOT, LOCALE_ONLY, DIRECT }
    companion object {
        private class AvailEntry(private val prefix: String, private val loader: ICUResourceLoader) {
            private val lock = createAvailabilityLock()
            private val ulocales = AtomicReference<Map<ULocale.AvailableType, Array<ULocale>>?>(null)
            private val names = AtomicReference<Set<String>?>(null)
            fun getULocaleList(type: ULocale.AvailableType): Array<ULocale>? {
                if (ulocales.load() == null) lock.withLock {
                    if (ulocales.load() == null) ulocales.store(createULocaleList(prefix, loader))
                }
                return ulocales.load()!![type]
            }
            fun getLocaleNameSet(): Set<String> {
                if (names.load() == null) lock.withLock {
                    if (names.load() == null) {
                        val ids = HashSet<String>()
                        try {
                            val installed = getBundleInstance(prefix, "res_index", loader, true)[INSTALLED_LOCALES]
                            val iterator = installed.getIterator()
                            iterator.reset()
                            while (iterator.hasNext()) ids.add(iterator.next().getKey()!!)
                        } catch (_: MissingResourceException) { }
                        names.store(localeReadOnlySet(ids))
                    }
                }
                return names.load()!!
            }
        }
        private val availableCache = object : SoftCache<String, AvailEntry, ICUResourceLoader>() {
            override fun createInstance(key: String, data: ICUResourceLoader): AvailEntry = AvailEntry(key, data)
        }
        private fun getAvailEntry(baseName: String, loader: ICUResourceLoader): AvailEntry = availableCache.getInstance(baseName, loader)!!
        private fun createULocaleList(baseName: String, loader: ICUResourceLoader): Map<ULocale.AvailableType, Array<ULocale>> {
            val bundle = getBundleInstance(baseName, "res_index", loader, true)
            val result = HashMap<ULocale.AvailableType, Array<ULocale>>()
            bundle.getAllItemsWithFallback("", object : UResource.Sink() {
                override fun put(key: UResource.Key, value: UResource.Value, noFallback: Boolean) {
                    val index = value.getTable()
                    var i = 0
                    while (index.getKeyAndValue(i++, key, value)) {
                        val type = when {
                            key.contentEquals("InstalledLocales") -> ULocale.AvailableType.DEFAULT
                            key.contentEquals("AliasLocales") -> ULocale.AvailableType.ONLY_LEGACY_ALIASES
                            else -> continue
                        }
                        val locales = value.getTable()
                        result[type] = Array(locales.getSize()) { j ->
                            locales.getKeyAndValue(j, key, value)
                            ULocale(key.toString())
                        }
                    }
                }
            })
            return result
        }
        fun getAvailableULocales(baseName: String = ICUData.ICU_BASE_NAME, loader: ICUResourceLoader = ICUResourceBundleReader.embeddedLoader,
                                type: ULocale.AvailableType = ULocale.AvailableType.DEFAULT): Array<ULocale>? = getAvailEntry(baseName, loader).getULocaleList(type)
        fun getAvailableULocales(type: ULocale.AvailableType): Array<ULocale>? =
            getAvailableULocales(ICUData.ICU_BASE_NAME, ICUResourceBundleReader.embeddedLoader, type)
        fun getAvailableLocaleNameSet(baseName: String = ICUData.ICU_BASE_NAME, loader: ICUResourceLoader = ICUResourceBundleReader.embeddedLoader): Set<String> =
            getAvailEntry(baseName, loader).getLocaleNameSet()
        fun getFunctionalEquivalent(baseName: String, loader: ICUResourceLoader, resName: String, keyword: String,
                                    locID: ULocale, isAvailable: BooleanArray?, omitDefault: Boolean): ULocale {
            var keywordValue = locID.getKeywordValue(keyword) ?: ""
            val baseLocale = locID.getBaseName()
            var defaultValue: String? = null
            var parent = ULocale(baseLocale)
            var defaultLocale: ULocale? = null
            var lookForDefault = keywordValue.isEmpty() || keywordValue == "default"
            if (lookForDefault) keywordValue = ""
            var fullBase: ULocale? = null
            var defaultDepth = 0
            var resourceDepth = 0
            // Upstream uses its default loader for lookup; the argument is used for availability only.
            var bundle: ICUResourceBundle? = getBundleInstance(baseName, parent.getBaseName())
            if (isAvailable != null) {
                if (isAvailable.isEmpty()) throw IndexOutOfBoundsException("Index 0 out of bounds for length 0")
                isAvailable[0] = false
                val locales = getAvailEntry(baseName, loader).getULocaleList(ULocale.AvailableType.DEFAULT)!!
                for (locale in locales) if (parent == locale) { isAvailable[0] = true; break }
            }
            do {
                val current = bundle!!
                try {
                    val resource = current[resName] as ICUResourceBundle
                    defaultValue = resource.getString("default")
                    if (lookForDefault) { keywordValue = defaultValue; lookForDefault = false }
                    defaultLocale = current.getULocale()
                } catch (_: MissingResourceException) { }
                if (defaultLocale == null) { bundle = current.getParent(); defaultDepth++ }
            } while (bundle != null && defaultLocale == null)
            parent = ULocale(baseLocale)
            bundle = getBundleInstance(baseName, parent.getBaseName())
            do {
                val current = bundle!!
                try {
                    val resource = current[resName] as ICUResourceBundle
                    resource[keywordValue]
                    fullBase = resource.getULocale()
                    if (resourceDepth > defaultDepth) {
                        defaultValue = resource.getString("default")
                        defaultLocale = current.getULocale()
                        defaultDepth = resourceDepth
                    }
                } catch (_: MissingResourceException) { }
                if (fullBase == null) { bundle = current.getParent(); resourceDepth++ }
            } while (bundle != null && fullBase == null)
            if (fullBase == null && defaultValue != null && defaultValue != keywordValue) {
                keywordValue = defaultValue
                parent = ULocale(baseLocale)
                bundle = getBundleInstance(baseName, parent.getBaseName())
                resourceDepth = 0
                do {
                    val current = bundle!!
                    try {
                        val resource = current[resName] as ICUResourceBundle
                        val item = resource[keywordValue] as ICUResourceBundle
                        fullBase = current.getULocale()
                        if (fullBase.getBaseName() != item.getULocale().getBaseName()) fullBase = null
                        if (fullBase != null && resourceDepth > defaultDepth) {
                            defaultValue = resource.getString("default")
                            defaultLocale = current.getULocale()
                            defaultDepth = resourceDepth
                        }
                    } catch (_: MissingResourceException) { }
                    if (fullBase == null) { bundle = current.getParent(); resourceDepth++ }
                } while (bundle != null && fullBase == null)
            }
            val found = fullBase ?: throw MissingResourceException(
                "Could not find locale containing requested or default keyword.", baseName, "$keyword=$keywordValue")
            return if (omitDefault && defaultValue!!.equals(keywordValue) && resourceDepth <= defaultDepth) found
                else ULocale(found.getBaseName() + "@" + keyword + "=" + keywordValue)
        }
        fun getKeywordValues(baseName: String, keyword: String?, loader: ICUResourceLoader = ICUResourceBundleReader.embeddedLoader): Array<String> {
            val keywords = HashSet<String>()
            val locales = getAvailEntry(baseName, loader).getULocaleList(ULocale.AvailableType.DEFAULT)!!
            for (locale in locales) try {
                val bundle = getBundleInstance(baseName, locale.getBaseName(), loader, false)
                val table = bundle.getObject(keyword!!) as ICUResourceBundle
                val keys = table.getKeys()
                while (keys.hasMoreElements()) {
                    val key = keys.nextElement()
                    if (key != "default" && !key.startsWith("private-")) keywords.add(key)
                }
            } catch (_: Throwable) { }
            return keywords.toTypedArray()
        }
        private fun interface BundleLoader { fun load(): ICUResourceBundle? }
        private val bundleCache = object : SoftCache<String, ICUResourceBundle, BundleLoader>() {
            override fun createInstance(key: String, data: BundleLoader): ICUResourceBundle? = data.load()
        }
        fun getBundleInstance(baseName: String?, localeID: String, loader: ICUResourceLoader = ICUResourceBundleReader.embeddedLoader,
                              disableFallback: Boolean = false): ICUResourceBundle =
            getBundleInstance(baseName, localeID, loader, if (disableFallback) OpenType.DIRECT else OpenType.LOCALE_DEFAULT_ROOT)
        fun getBundleInstance(baseName: String?, locale: ULocale?, openType: OpenType): ICUResourceBundle =
            getBundleInstance(baseName, locale?.getBaseName() ?: ULocale.getBaseName(resourceDefaultLocaleName()),
                ICUResourceBundleReader.embeddedLoader, openType)
        fun getBundleInstance(baseName: String?, localeID: String, loader: ICUResourceLoader, openType: OpenType): ICUResourceBundle {
            val base = baseName ?: ICUData.ICU_BASE_NAME
            val id = ULocale.getBaseName(localeID)
            val defaultID = if (openType == OpenType.LOCALE_DEFAULT_ROOT) ULocale.getBaseName(resourceDefaultLocaleName()) else null
            return instantiateBundle(base, id, null, defaultID, loader, openType)
                ?: throw MissingResourceException("Could not find the bundle $base/$id.res", "", "")
        }
        fun createBundle(baseName: String, localeID: String, loader: ICUResourceLoader = ICUResourceBundleReader.embeddedLoader): ICUResourceBundle? {
            val reader = ICUResourceBundleReader.getReader(baseName, localeID, loader) ?: return null
            val res = reader.rootResource
            if (!ICUResourceBundleReader.URES_IS_TABLE(ICUResourceBundleReader.RES_GET_TYPE(res))) throw IllegalStateException("Invalid format error")
            val table = ICUResourceBundleImpl.ResourceTable.createRoot(baseName, localeID, reader, loader)
            val alias = table.findString("%%ALIAS")
            return if (alias == null) table else getBundleInstance(baseName, alias)
        }
        private fun parentResourceName(bundle: ICUResourceBundle): String? =
            (bundle as ICUResourceBundleImpl.ResourceTable).findString("%%Parent")
        private fun localeIDStartsWithLangSubtag(localeID: String, language: String): Boolean = localeID.startsWith(language) &&
            (localeID.length == language.length || localeID[language.length] == '_')
        private fun instantiateBundle(baseName: String, localeID: String, origLocaleID: String?, defaultID: String?,
                                      loader: ICUResourceLoader, openType: OpenType): ICUResourceBundle? {
            val fullName = ICUResourceBundleReader.getFullName(baseName, localeID)
            val mode = ('0'.code + openType.ordinal).toChar()
            val cacheKey = if (openType == OpenType.LOCALE_DEFAULT_ROOT) "$fullName#$mode#$defaultID" else "$fullName#$mode"
            return bundleCache.getInstance(cacheKey, BundleLoader {
                val rootLocale = if ('.' !in baseName) "root" else ""
                var localeName = localeID.ifEmpty { rootLocale }
                var bundle = createBundle(baseName, localeName, loader)
                if (openType == OpenType.DIRECT || bundle?.wholeBundle?.reader?.noFallback == true) return@BundleLoader bundle
                if (bundle == null) {
                    val localMode = if (openType == OpenType.LOCALE_DEFAULT_ROOT && localeName == defaultID) OpenType.LOCALE_ROOT else openType
                    val original = origLocaleID ?: localeName
                    val fallback = getParentLocaleID(localeName, original, openType)
                    bundle = if (fallback != null) instantiateBundle(baseName, fallback, original, defaultID, loader, localMode)
                        else if (localMode == OpenType.LOCALE_DEFAULT_ROOT && !localeIDStartsWithLangSubtag(defaultID!!, localeName))
                            instantiateBundle(baseName, defaultID, null, defaultID, loader, localMode)
                        else if (localMode != OpenType.LOCALE_ONLY && rootLocale.isNotEmpty()) createBundle(baseName, rootLocale, loader)
                        else null
                } else {
                    localeName = bundle.getLocaleID()
                    val index = localeName.lastIndexOf('_')
                    val parentName = parentResourceName(bundle)
                    val parent = when {
                        parentName != null -> instantiateBundle(baseName, parentName, null, defaultID, loader, openType)
                        index != -1 -> instantiateBundle(baseName, localeName.substring(0, index), null, defaultID, loader, openType)
                        localeName != rootLocale -> instantiateBundle(baseName, rootLocale, null, defaultID, loader, openType)
                        else -> null
                    }
                    if (bundle != parent) bundle.setParent(parent)
                }
                bundle
            })
        }
        private fun findStringWithFallback(path: String, actual: UResourceBundle, requestedBundle: UResourceBundle?): String? {
            if (path.isEmpty() || actual !is ICUResourceBundleImpl.ResourceContainer) return null
            val requested = requestedBundle ?: actual
            var base: ICUResourceBundle = actual
            var reader = base.wholeBundle.reader
            var res = RES_BOGUS
            var baseDepth = base.getResDepth()
            var depth = baseDepth
            val parts = path.split('/')
            var keys = Array(depth + parts.size) { "" }
            for (i in parts.indices) keys[depth + i] = parts[i]
            while (true) {
                while (true) {
                    val readerContainer: ICUResourceBundleReader.Container
                    if (res == RES_BOGUS) {
                        if (base.getType() != TABLE && base.getType() != ARRAY) break
                        readerContainer = (base as ICUResourceBundleImpl.ResourceContainer).readerContainer()
                    } else {
                        val type = ICUResourceBundleReader.RES_GET_TYPE(res)
                        readerContainer = when {
                            ICUResourceBundleReader.URES_IS_TABLE(type) -> reader.getTable(res)!!
                            ICUResourceBundleReader.URES_IS_ARRAY(type) -> reader.getArray(res)!!
                            else -> { res = RES_BOGUS; break }
                        }
                    }
                    val key = keys[depth++]
                    res = readerContainer.getResource(key)
                    if (res == RES_BOGUS) { --depth; break }
                    val sub = if (ICUResourceBundleReader.RES_GET_TYPE(res) == ALIAS) {
                        base.getResPathKeys(keys, baseDepth)
                        getAliasedResource(base, keys, depth, key, res, null, requested)
                    } else null
                    if (depth == keys.size) return sub?.getString() ?: reader.getString(res) ?: throw UResourceTypeMismatchException("")
                    if (sub != null) {
                        base = sub
                        reader = base.wholeBundle.reader
                        res = RES_BOGUS
                        baseDepth = base.getResDepth()
                        if (depth != baseDepth) {
                            val replacement = Array(baseDepth + keys.size - depth) { "" }
                            keys.copyInto(replacement, baseDepth, depth, keys.size)
                            keys = replacement
                            depth = baseDepth
                        }
                    }
                }
                val next = base.getParent() ?: return null
                base.getResPathKeys(keys, baseDepth)
                base = next
                reader = base.wholeBundle.reader
                baseDepth = 0
                depth = 0
            }
        }
        private fun findResourceWithFallback(path: String, actual: UResourceBundle, requested: UResourceBundle?): ICUResourceBundle? {
            if (path.isEmpty()) return null
            val base = actual as ICUResourceBundle
            val depth = base.getResDepth()
            val parts = path.split('/')
            val keys = Array(depth + parts.size) { "" }
            for (i in parts.indices) keys[depth + i] = parts[i]
            return findResourceWithFallback(keys, depth, base, requested)
        }
        private fun findResourceWithFallback(initialKeys: Array<String>, initialDepth: Int, initialBase: ICUResourceBundle,
                                             requestedBundle: UResourceBundle?): ICUResourceBundle? {
            var keys = initialKeys
            var depth = initialDepth
            var base = initialBase
            val requested = requestedBundle ?: base
            while (true) {
                while (true) {
                    val sub = base.handleGet(keys[depth++], null, requested) as ICUResourceBundle?
                    if (sub == null) { --depth; break }
                    if (depth == keys.size) return sub
                    base = sub
                }
                val next = base.getParent() ?: return null
                val baseDepth = base.getResDepth()
                if (depth != baseDepth) {
                    val replacement = Array(baseDepth + keys.size - depth) { "" }
                    keys.copyInto(replacement, baseDepth, depth, keys.size)
                    keys = replacement
                }
                base.getResPathKeys(keys, baseDepth)
                base = next
                depth = 0
            }
        }
        @kotlin.jvm.JvmStatic
        protected fun getAliasedResource(base: ICUResourceBundle, keys: Array<String>?, depth: Int, key: String, res: Int,
                                         aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): ICUResourceBundle {
            val whole = base.wholeBundle
            val path = whole.reader.getAlias(res)!!
            val baseDepth = base.getResDepth()
            val basePath = Array(baseDepth + 1) { "" }
            base.getResPathKeys(basePath, baseDepth)
            basePath[baseDepth] = key
            return getAliasedResource(path, whole.loader, whole.baseName, keys, depth, basePath, aliasesVisited, requested)
        }
        @kotlin.jvm.JvmStatic
        protected fun getAliasedResource(path: String, initialLoader: ICUResourceLoader, baseName: String,
                                         initialKeys: Array<String>?, depth: Int, basePath: Array<String>?,
                                         aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): ICUResourceBundle {
            val visited = aliasesVisited ?: HashMap()
            if (visited[path] != null) throw IllegalArgumentException("Circular references in the resource bundles")
            visited[path] = ""
            var loader = initialLoader
            var locale: String
            var keyPath: String? = null
            var bundleName: String
            if (path.startsWith('/')) {
                val i = path.indexOf('/', 1)
                val j = path.indexOf('/', i + 1)
                bundleName = path.substring(1, i)
                if (j < 0) locale = path.substring(i + 1)
                else { locale = path.substring(i + 1, j); keyPath = path.substring(j + 1) }
                if (bundleName == "ICUDATA") { bundleName = ICUData.ICU_BASE_NAME; loader = ICUResourceBundleReader.embeddedLoader }
                else if ("ICUDATA" in bundleName) {
                    val index = bundleName.indexOf('-')
                    if (index > -1) { bundleName = ICUData.ICU_BASE_NAME + "/" + bundleName.substring(index + 1); loader = ICUResourceBundleReader.embeddedLoader }
                }
            } else {
                val i = path.indexOf('/')
                if (i != -1) { locale = path.substring(0, i); keyPath = path.substring(i + 1) }
                else locale = path
                bundleName = baseName
            }
            var sub: ICUResourceBundle? = null
            if (bundleName == "LOCALE") {
                keyPath = path.substring("LOCALE".length + 2)
                var bundle = requested as ICUResourceBundle
                while (bundle.container != null) bundle = bundle.container!!
                sub = findResourceWithFallback(keyPath, bundle, null)
            } else {
                val bundle = getBundleInstance(bundleName, locale, loader, false)
                var keys = initialKeys
                val numKeys = if (keyPath != null) {
                    val parts = if (keyPath.isEmpty()) emptyList() else keyPath.split('/')
                    if (parts.isNotEmpty()) keys = parts.toTypedArray()
                    parts.size
                } else if (keys != null) depth else { keys = basePath; basePath!!.size }
                if (numKeys > 0) {
                    sub = bundle
                    for (i in 0 until numKeys) sub = sub?.get(keys!![i], visited, requested)
                }
            }
            return sub ?: throw MissingResourceException(locale, baseName, basePath!!.last())
        }
        private fun getDefaultScript(language: String, region: String?): String =
            LocaleFallbackData.DEFAULT_SCRIPT_TABLE["${language}_$region"]
                ?: LocaleFallbackData.DEFAULT_SCRIPT_TABLE[language] ?: "Latn"
        fun getParentLocaleID(name: String, origName: String, openType: OpenType): String? {
            if (name.endsWith('_') || ULocale.getVariant(name).isNotEmpty()) {
                val last = name.lastIndexOf('_')
                return if (last >= 0) name.substring(0, last) else null
            }
            val locale = ULocale(name)
            val language = locale.getLanguage()
            val script = locale.getScript()
            val region = locale.getCountry()
            if (openType == OpenType.LOCALE_DEFAULT_ROOT) {
                LocaleFallbackData.PARENT_LOCALE_TABLE[name]?.let { return if (it == "root") null else it }
            }
            if (script.isNotEmpty() && region.isNotEmpty()) {
                return if (getDefaultScript(language, region) == script) "${language}_$region" else "${language}_$script"
            }
            if (region.isNotEmpty()) {
                val originalScript = ULocale.getScript(origName)
                return "${language}_${if (originalScript.isNotEmpty()) originalScript else getDefaultScript(language, region)}"
            }
            if (script.isNotEmpty()) {
                return if (openType != OpenType.LOCALE_DEFAULT_ROOT || getDefaultScript(language, null) == script) language else null
            }
            return null
        }

        const val RES_BOGUS = -1
        const val ALIAS = 3
        const val TABLE32 = 4
        const val TABLE16 = 5
        const val STRING_V2 = 6
        const val ARRAY16 = 9
        const val NO_INHERITANCE_MARKER = "∅∅∅"
        protected const val INSTALLED_LOCALES = "InstalledLocales"
    }
}
