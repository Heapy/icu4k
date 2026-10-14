// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

/** Concrete scalar resources and the shared container base; alias-aware child creation is being ported. */
internal open class ICUResourceBundleImpl : ICUResourceBundle {
    protected var resourceHandle: Int
    protected constructor(container: ICUResourceBundleImpl, key: String, resource: Int) : super(container, key) {
        resourceHandle = resource
    }
    protected constructor(wholeBundle: WholeBundle) : super(wholeBundle) {
        resourceHandle = wholeBundle.reader.rootResource
    }
    fun getResource(): Int = resourceHandle

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
    }
}
