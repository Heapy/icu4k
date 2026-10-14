// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.UResourceBundle
import io.github.heapy.icu4k.util.UResourceTypeMismatchException

/** Concrete ICU binary resource objects. */
internal open class ICUResourceBundleImpl : ICUResourceBundle {
    protected var resourceHandle: Int
    protected constructor(container: ICUResourceBundleImpl, key: String, resource: Int) : super(container, key) {
        resourceHandle = resource
    }
    protected constructor(wholeBundle: WholeBundle) : super(wholeBundle) {
        resourceHandle = wholeBundle.reader.rootResource
    }
    fun getResource(): Int = resourceHandle

    protected fun createBundleObject(key: String, res: Int, visited: HashMap<String, String>?, requested: UResourceBundle): ICUResourceBundle =
        when (ICUResourceBundleReader.RES_GET_TYPE(res)) {
            STRING, STRING_V2 -> ResourceString(this, key, res)
            BINARY -> ResourceBinary(this, key, res)
            ALIAS -> getAliasedResource(this, null, 0, key, res, visited, requested)
            INT -> ResourceInt(this, key, res)
            INT_VECTOR -> ResourceIntVector(this, key, res)
            ARRAY, ARRAY16 -> ResourceArray(this, key, res)
            TABLE, TABLE16, TABLE32 -> ResourceTable(this, key, res)
            else -> throw IllegalStateException("The resource type is unknown")
        }

    class ResourceBinary(container: ICUResourceBundleImpl, key: String, resource: Int) : ICUResourceBundleImpl(container, key, resource) {
        override fun getType(): Int = BINARY
        override fun getBinary(): BinaryBuffer = wholeBundle.reader.binaryBuffer(resourceHandle)!!
        override fun getBinary(ba: ByteArray?): ByteArray = wholeBundle.reader.getBinary(resourceHandle, ba)!!
    }
    class ResourceInt(container: ICUResourceBundleImpl, key: String, resource: Int) : ICUResourceBundleImpl(container, key, resource) {
        override fun getType(): Int = INT
        override fun getInt(): Int = ICUResourceBundleReader.RES_GET_INT(resourceHandle)
        override fun getUInt(): Int = ICUResourceBundleReader.RES_GET_UINT(resourceHandle)
    }
    class ResourceString(container: ICUResourceBundleImpl, key: String, resource: Int) : ICUResourceBundleImpl(container, key, resource) {
        private val value: String? = wholeBundle.reader.getString(resourceHandle)!!.let {
            if (it.length < 12 || CacheValue.futureInstancesWillBeStrong()) it else null
        }
        override fun getType(): Int = STRING
        override fun getString(): String = value ?: wholeBundle.reader.getString(resourceHandle)!!
    }
    class ResourceIntVector(container: ICUResourceBundleImpl, key: String, resource: Int) : ICUResourceBundleImpl(container, key, resource) {
        override fun getType(): Int = INT_VECTOR
        override fun getIntVector(): IntArray = wholeBundle.reader.getIntVector(resourceHandle)!!
    }
    abstract class ResourceContainer : ICUResourceBundleImpl {
        protected lateinit var value: ICUResourceBundleReader.Container
        protected constructor(container: ICUResourceBundleImpl, key: String, resource: Int) : super(container, key, resource)
        protected constructor(wholeBundle: WholeBundle) : super(wholeBundle)
        override fun getSize(): Int = value.size
        override fun getString(index: Int): String {
            val res = getContainerResource(index)
            if (res == ICUResourceBundleReader.RES_BOGUS) throw IndexOutOfBoundsException()
            return wholeBundle.reader.getString(res) ?: super.getString(index)
        }
        protected fun getContainerResource(index: Int): Int = value.getContainerResource(index)
        protected fun createBundleObject(index: Int, key: String, visited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle {
            val item = getContainerResource(index)
            if (item == RES_BOGUS) throw IndexOutOfBoundsException()
            return createBundleObject(key, item, visited, requested)
        }
    }
    class ResourceArray(container: ICUResourceBundleImpl, key: String, resource: Int) : ResourceContainer(container, key, resource) {
        init { value = wholeBundle.reader.getArray(resource)!! }
        override fun getType(): Int = ARRAY
        override fun handleGetStringArray(): Array<String> = Array(value.size) {
            wholeBundle.reader.getString(value.getContainerResource(it)) ?: throw UResourceTypeMismatchException("")
        }
        override fun getStringArray(): Array<String> = handleGetStringArray()
        override fun handleGet(aKey: String, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle =
            createBundleObject(aKey.toInt(), aKey, aliasesVisited, requested)
        override fun handleGet(index: Int, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle =
            createBundleObject(index, index.toString(), aliasesVisited, requested)
    }
    class ResourceTable : ResourceContainer {
        constructor(container: ICUResourceBundleImpl, key: String, resource: Int) : super(container, key, resource) {
            value = wholeBundle.reader.getTable(resource)!!
        }
        private constructor(wholeBundle: WholeBundle, rootRes: Int) : super(wholeBundle) {
            value = wholeBundle.reader.getTable(rootRes)!!
        }
        override fun getType(): Int = TABLE
        override fun handleKeySet(): Set<String> = ResourceKeySet().also { keys ->
            for (i in 0 until value.size) keys.add(value.getKey(i)!!)
        }
        override fun handleGet(aKey: String, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle? {
            val index = value.findTableItem(aKey)
            if (index < 0) return null
            return createBundleObject(aKey, getContainerResource(index), aliasesVisited, requested)
        }
        override fun handleGet(index: Int, aliasesVisited: HashMap<String, String>?, requested: UResourceBundle): UResourceBundle {
            val key = value.getKey(index) ?: throw IndexOutOfBoundsException()
            return createBundleObject(key, getContainerResource(index), aliasesVisited, requested)
        }
        override fun handleGetObject(aKey: String): Any? {
            val reader = wholeBundle.reader
            val index = value.findTableItem(aKey)
            if (index >= 0) {
                val res = getContainerResource(index)
                reader.getString(res)?.let { return it }
                reader.getArray(res)?.let { array ->
                    val strings = Array(array.size) { "" }
                    var i = 0
                    while (i < array.size) {
                        strings[i] = reader.getString(array.getContainerResource(i)) ?: break
                        i++
                    }
                    if (i == array.size) return strings
                }
            }
            return super.handleGetObject(aKey)
        }
        companion object {
            fun createRoot(baseName: String, localeID: String, reader: ICUResourceBundleReader, loader: ICUResourceLoader): ResourceTable =
                ResourceTable(WholeBundle(baseName, localeID, reader, loader), reader.rootResource)
        }
        fun findString(key: String): String? {
            val index = value.findTableItem(key)
            return if (index < 0) null else wholeBundle.reader.getString(getContainerResource(index))
        }
    }
    private class ResourceKeySet : AbstractMutableSet<String>() {
        private val keys = LocaleKeywordMap()
        override val size: Int get() = keys.size
        override fun contains(element: String): Boolean = keys.containsKey(element)
        override fun add(element: String): Boolean = keys.put(element, "") == null
        override fun remove(element: String): Boolean = keys.remove(element) != null
        override fun clear() { keys.clear() }
        override fun iterator(): MutableIterator<String> = keys.keys.iterator()
    }

}
