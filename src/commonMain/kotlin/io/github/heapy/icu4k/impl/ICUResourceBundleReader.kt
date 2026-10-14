// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.UResourceTypeMismatchException
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** Binary ResB value decoding. Locale fallback and the resource-object facade are separate. */
internal class ICUResourceBundleReader(input: BinaryBuffer, private var pool: ICUResourceBundleReader? = null,
    private val loadPool: (() -> ICUResourceBundleReader?)? = null) {
    private val bytes: BinaryBuffer
    private val keys: ByteArray
    private val units: CharArray
    private val resourceCache: ResourceCache?
    private val localKeyLimit: Int
    private val poolStringIndexLimit: Int
    private val poolStringIndex16Limit: Int
    val rootResource: Int
    val noFallback: Boolean
    val isPoolBundle: Boolean
    val usesPoolBundle: Boolean
    val poolChecksum: Int
    val dataVersion: Int
    init {
        dataVersion = ICUBinary.readHeader(input, 0x52657342, object : ICUBinary.Authenticate {
            override fun isDataVersionAcceptable(version: ByteArray): Boolean =
                (version[0].toInt() == 1 && (version[1].toInt() and 255) >= 1) || version[0].toInt() in 2..3
        })
        val major = input.getByte(16).toInt() and 255
        bytes = input.slice()
        rootResource = intAt(0)
        val indexes0 = intAt(4)
        val count = indexes0 and 255
        check(count > 4) { "not enough indexes" }
        check(bytes.limit >= (1 + count) * 4 && bytes.limit >= intAt(16) * 4) { "not enough bytes" }
        val attributes = if (count > 5) intAt(24) else 0
        noFallback = attributes and 1 != 0
        isPoolBundle = attributes and 2 != 0
        usesPoolBundle = attributes and 4 != 0
        poolStringIndexLimit = (if (major >= 3) indexes0 ushr 8 else 0) or ((attributes and 0xf000) shl 12)
        poolStringIndex16Limit = attributes ushr 16
        val keysBottom = (1 + count) * 4
        val keysTop = intAt(8) * 4
        localKeyLimit = if (!isPoolBundle && keysTop > keysBottom) keysTop else 0
        keys = if (keysTop > keysBottom) {
            val start = if (isPoolBundle) keysBottom else 0
            ByteArray(keysTop - start) { bytes.getByte(start + it) }
        } else byteArrayOf()
        val unitsTop = if (count > 6) intAt(28) * 4 else keysTop
        units = if (unitsTop > keysTop) CharArray((unitsTop - keysTop) / 2) { charAt(keysTop + 2 * it).toChar() }
            else charArrayOf('\u0000')
        resourceCache = if (!isPoolBundle || units.size > 1) ResourceCache((intAt(16) - 1) or (units.size - 1)) else null
        poolChecksum = if (count > 7) intAt(32) else 0
        if (usesPoolBundle) {
            if (pool == null) pool = loadPool?.invoke()
            check(pool != null && pool!!.isPoolBundle) { "pool.res is not a pool bundle" }
            check(poolChecksum == pool!!.poolChecksum) { "pool.res has a different checksum than this bundle" }
        }
    }
    private fun intAt(offset: Int): Int {
        val cursor = bytes.sliceAt(offset)
        return cursor.readInt()
    }
    private fun charAt(offset: Int): Int = bytes.sliceAt(offset).readUnsignedShort()
    private fun BinaryBuffer.sliceAt(offset: Int): BinaryBuffer = slice(offset)
    private fun unitAt(offset: Int): Int {
        if (offset !in units.indices) throw IndexOutOfBoundsException()
        return units[offset].code
    }
    private fun stringAt(offset: Int, length: Int): String {
        if (length < 0 || offset < 0 || length > (bytes.limit - offset) / 2) throw IndexOutOfBoundsException()
        return CharArray(length) { charAt(offset + 2 * it).toChar() }.concatToString()
    }
    private fun stringV2(offsetValue: Int): String {
        var offset = offsetValue
        val first = unitAt(offset)
        if (first and 0xfc00 != 0xdc00) {
            val result = StringBuilder()
            while (unitAt(offset) != 0) result.append(unitAt(offset++).toChar())
            return result.toString()
        }
        val length: Int
        if (first < 0xdfef) { length = first and 0x3ff; offset++ }
        else if (first < 0xdfff) { length = ((first - 0xdfef) shl 16) or unitAt(offset + 1); offset += 2 }
        else { length = (unitAt(offset + 1) shl 16) or unitAt(offset + 2); offset += 3 }
        if (length < 0 || offset > units.size - length) throw IndexOutOfBoundsException()
        return units.concatToString(offset, offset + length)
    }
    private fun getStringV2(res: Int): String {
        val offset = RES_GET_UINT(res)
        (resourceCache!!.get(res) as String?)?.let { return it }
        if (unitAt(offset) == 0) return ""
        val value = stringV2(offset)
        return resourceCache.putIfAbsent(res, value, value.length * 2) as String
    }
    fun getString(res: Int): String? {
        val offset = RES_GET_UINT(res)
        val type = RES_GET_TYPE(res)
        if (type != 0 && type != 6) return null
        if (offset == 0) return ""
        if (type == 6) return if (offset < poolStringIndexLimit) pool!!.getStringV2(res)
            else getStringV2(res - poolStringIndexLimit)
        (resourceCache!!.get(res) as String?)?.let { return it }
        val value = stringAt(offset * 4 + 4, intAt(offset * 4))
        return resourceCache.putIfAbsent(res, value, value.length * 2) as String
    }
    fun getAlias(res: Int): String? {
        if (RES_GET_TYPE(res) != 3) return null
        val offset = RES_GET_UINT(res) * 4
        if (offset == 0) return ""
        (resourceCache!!.get(res) as String?)?.let { return it }
        val value = stringAt(offset + 4, intAt(offset))
        return resourceCache.putIfAbsent(res, value, value.length * 2) as String
    }
    fun getBinary(res: Int): ByteArray? {
        if (RES_GET_TYPE(res) != 1) return null
        val offset = RES_GET_UINT(res) * 4
        if (offset == 0) return byteArrayOf()
        val length = intAt(offset)
        return bytes.sliceAt(offset + 4).readBytes(length)
    }
    fun getBinary(res: Int, destination: ByteArray?): ByteArray? {
        val input = binaryBuffer(res) ?: return null
        val length = input.remaining
        if (length == 0) return EMPTY_BYTES
        val result = if (destination != null && destination.size == length) destination else ByteArray(length)
        for (i in result.indices) result[i] = input.readByte()
        return result
    }
    fun getIntVector(res: Int): IntArray? {
        if (RES_GET_TYPE(res) != 14) return null
        val offset = RES_GET_UINT(res) * 4
        if (offset == 0) return intArrayOf()
        return bytes.sliceAt(offset + 4).readInts(intAt(offset))
    }
    private fun isNoInheritanceMarker(res: Int): Boolean {
        val offset = RES_GET_UINT(res)
        if (offset == 0) return false
        if (RES_GET_TYPE(res) == 0) {
            val byteOffset = offset * 4
            return intAt(byteOffset) == 3 && charAt(byteOffset + 4) == 0x2205 &&
                charAt(byteOffset + 6) == 0x2205 && charAt(byteOffset + 8) == 0x2205
        }
        if (RES_GET_TYPE(res) != 6) return false
        return if (offset < poolStringIndexLimit) pool!!.stringV2Marker(offset)
            else stringV2Marker(offset - poolStringIndexLimit)
    }
    private fun stringV2Marker(offset: Int): Boolean = when (unitAt(offset)) {
        0x2205 -> unitAt(offset + 1) == 0x2205 && unitAt(offset + 2) == 0x2205 && unitAt(offset + 3) == 0
        0xdc03 -> unitAt(offset + 1) == 0x2205 && unitAt(offset + 2) == 0x2205 && unitAt(offset + 3) == 0x2205
        else -> false
    }
    /** Shares binary storage while keeping an independent cursor. */
    internal fun binaryBuffer(res: Int): BinaryBuffer? {
        if (RES_GET_TYPE(res) != 1) return null
        val offset = RES_GET_UINT(res) * 4
        if (offset == 0) return BinaryBuffer(byteArrayOf()).asReadOnlyBuffer()
        return bytes.sliceAt(offset + 4).also { it.limit = intAt(offset) }.slice().asReadOnlyBuffer()
    }
    class ReaderValue : UResource.Value() {
        lateinit var reader: ICUResourceBundleReader
        var res: Int = 0
        private fun mismatch(): Nothing = throw UResourceTypeMismatchException("")
        override fun getType(): Int = PUBLIC_TYPES[RES_GET_TYPE(res)]
        override fun getString(): String = reader.getString(res) ?: mismatch()
        override fun getAliasString(): String = reader.getAlias(res) ?: mismatch()
        override fun getInt(): Int = if (RES_GET_TYPE(res) == 7) RES_GET_INT(res) else mismatch()
        override fun getUInt(): Int = if (RES_GET_TYPE(res) == 7) RES_GET_UINT(res) else mismatch()
        override fun getIntVector(): IntArray = reader.getIntVector(res) ?: mismatch()
        override fun getBinary(): BinaryBuffer = reader.binaryBuffer(res) ?: mismatch()
        override fun getArray(): UResource.Array = reader.getArray(res)?.asArray() ?: mismatch()
        override fun getTable(): UResource.Table = reader.getTable(res)?.asTable() ?: mismatch()
        override fun isNoInheritanceMarker(): Boolean = reader.isNoInheritanceMarker(res)
        private fun strings(array: Container): Array<String> = Array(array.size) {
            reader.getString(array.getContainerResource(it)) ?: mismatch()
        }
        override fun getStringArray(): Array<String> = strings(reader.getArray(res) ?: mismatch())
        override fun getStringArrayOrStringAsArray(): Array<String> {
            reader.getArray(res)?.let { return strings(it) }
            return arrayOf(reader.getString(res) ?: mismatch())
        }
        override fun getStringOrFirstOfArray(): String {
            reader.getString(res)?.let { return it }
            val array = reader.getArray(res) ?: mismatch()
            if (array.size == 0) mismatch()
            return reader.getString(array.getContainerResource(0)) ?: mismatch()
        }
    }
    private fun setKey(offset: Int, key16: Boolean, key: UResource.Key) {
        if (key16 && offset >= localKeyLimit) key.setBytes(pool!!.keys, offset - localKeyLimit)
        else if (!key16 && offset < 0) key.setBytes(pool!!.keys, offset and 0x7fffffff)
        else key.setBytes(keys, offset)
    }
    private fun keyString(offset: Int, key16: Boolean): String {
        val keyBytes: ByteArray
        var position: Int
        if (key16 && offset >= localKeyLimit) { keyBytes = pool!!.keys; position = offset - localKeyLimit }
        else if (!key16 && offset < 0) { keyBytes = pool!!.keys; position = offset and 0x7fffffff }
        else { keyBytes = keys; position = offset }
        val result = StringBuilder()
        while (true) {
            if (position !in keyBytes.indices) throw IndexOutOfBoundsException()
            val c = keyBytes[position++].toInt() and 255
            if (c == 0) return result.toString()
            result.append(c.toChar())
        }
    }
    inner class Container internal constructor(val size: Int, private val itemsOffset: Int, private val compact: Boolean,
                                               private val keyOffsets: IntArray? = null, private val key16: Boolean = true) {
        fun getContainerResource(index: Int): Int = resource(this@ICUResourceBundleReader, index)
        private fun resource(reader: ICUResourceBundleReader, index: Int): Int {
            if (index < 0 || index >= size) return RES_BOGUS
            if (!compact) return reader.intAt(itemsOffset + 4 * index)
            var res16 = reader.unitAt(itemsOffset + index)
            if (res16 >= reader.poolStringIndex16Limit) res16 += reader.poolStringIndexLimit - reader.poolStringIndex16Limit
            return (6 shl 28) or res16
        }
        fun getKey(index: Int): String? = if (index < 0 || index >= size || keyOffsets == null) null
            else keyString(keyOffsets[index], key16)
        fun findTableItem(key: CharSequence): Int {
            var start = 0
            var limit = size
            while (start < limit) {
                val mid = (start + limit) ushr 1
                val result = key.toString().compareTo(getKey(mid)!!)
                if (result < 0) limit = mid else if (result > 0) start = mid + 1 else return mid
            }
            return -1
        }
        private val arrayView: UResource.Array by lazy { createArrayView() }
        private val tableView: UResource.Table by lazy { createTableView() }
        fun asArray(): UResource.Array = arrayView
        fun asTable(): UResource.Table = tableView
        private fun createArrayView(): UResource.Array = object : UResource.Array {
            override fun getSize(): Int = size
            override fun getValue(i: Int, value: UResource.Value): Boolean {
                if (i !in 0 until size) return false
                val output = value as ReaderValue
                output.res = resource(output.reader, i)
                return true
            }
        }
        private fun createTableView(): UResource.Table = object : UResource.Table {
            override fun getSize(): Int = size
            override fun getKeyAndValue(i: Int, key: UResource.Key, value: UResource.Value): Boolean {
                if (i !in 0 until size) return false
                val output = value as ReaderValue
                output.reader.setKey(keyOffsets!![i], key16, key)
                output.res = resource(output.reader, i)
                return true
            }
            override fun findValue(key: CharSequence, value: UResource.Value): Boolean {
                val output = value as ReaderValue
                var start = 0
                var limit = size
                val candidate = UResource.Key()
                while (start < limit) {
                    val mid = (start + limit) ushr 1
                    output.reader.setKey(keyOffsets!![mid], key16, candidate)
                    val comparison = candidate.compareTo(key)
                    if (comparison > 0) limit = mid
                    else if (comparison < 0) start = mid + 1
                    else { output.res = resource(output.reader, mid); return true }
                }
                return false
            }
        }
        fun getResource(key: String): Int = getContainerResource(if (keyOffsets == null) key.toInt() else findTableItem(key))
    }
    fun getArray(res: Int): Container? {
        if (!URES_IS_ARRAY(RES_GET_TYPE(res))) return null
        if (RES_GET_UINT(res) == 0) return readArray(res)
        (resourceCache!!.get(res) as Container?)?.let { return it }
        return resourceCache.putIfAbsent(res, readArray(res)!!, 0) as Container
    }
    private fun readArray(res: Int): Container? {
        val type = RES_GET_TYPE(res)
        if (!URES_IS_ARRAY(type)) return null
        val offset = RES_GET_UINT(res)
        if (offset == 0) return Container(0, 0, false)
        return if (type == 9) Container(unitAt(offset), offset + 1, true)
            else Container(intAt(offset * 4), offset * 4 + 4, false)
    }
    fun getTable(res: Int): Container? {
        val type = RES_GET_TYPE(res)
        if (!URES_IS_TABLE(type)) return null
        if (RES_GET_UINT(res) == 0) return readTable(res)
        (resourceCache!!.get(res) as Container?)?.let { return it }
        val table = readTable(res)!!
        return resourceCache.putIfAbsent(res, table, table.size * (if (type == 4) 4 else 2)) as Container
    }
    private fun readTable(res: Int): Container? {
        val type = RES_GET_TYPE(res)
        if (!URES_IS_TABLE(type)) return null
        val offset = RES_GET_UINT(res)
        if (offset == 0) return Container(0, 0, false, intArrayOf())
        if (type == 5) {
            val size = unitAt(offset)
            return Container(size, offset + 1 + size, true, IntArray(size) { unitAt(offset + 1 + it) })
        }
        val byteOffset = offset * 4
        if (type == 4) {
            val size = intAt(byteOffset)
            return Container(size, byteOffset + 4 * (1 + size), false,
                IntArray(size) { intAt(byteOffset + 4 * (1 + it)) }, false)
        }
        val size = charAt(byteOffset)
        return Container(size, byteOffset + 2 * ((size + 2) and -2), false,
            IntArray(size) { charAt(byteOffset + 2 * (1 + it)) })
    }
    /** Common counterpart of the caller-supplied ClassLoader resource lookup. */
    fun interface ResourceLoader {
        fun getData(fullName: String): BinaryBuffer?
    }
    companion object {
        private data class ReaderCacheKey(val baseName: String, val localeID: String) {
            override fun hashCode(): Int = baseName.hashCode() xor localeID.hashCode()
        }
        private val embeddedLoader = ResourceLoader { name -> ICUData.getBytes(name)?.let { BinaryBuffer(it) } }
        // Cache keys intentionally omit the loader, matching ICU4J's first-publication semantics.
        private val readers = object : SoftCache<ReaderCacheKey, ICUResourceBundleReader, ResourceLoader>() {
            override fun createInstance(key: ReaderCacheKey, data: ResourceLoader): ICUResourceBundleReader? {
                val fullName = getFullName(key.baseName, key.localeID)
                try {
                    val input = data.getData(fullName) ?: return null
                    return ICUResourceBundleReader(input, loadPool = { getReader(key.baseName, "pool", data) })
                } catch (ex: ICUUncheckedIOException) {
                    throw ICUUncheckedIOException("Data file $fullName is corrupt - ${ex.message}", ex)
                }
            }
        }
        fun getReader(baseName: String?, localeID: String?, loader: ResourceLoader = embeddedLoader): ICUResourceBundleReader? =
            readers.getInstance(ReaderCacheKey(baseName ?: "", localeID ?: ""), loader)

        fun getFullName(baseName: String?, localeName: String): String {
            if (baseName.isNullOrEmpty()) {
                return if (localeName.isEmpty()) resourceDefaultLocaleName() else "$localeName.res"
            }
            if ('.' !in baseName) return baseName + (if (baseName.endsWith('/')) "" else "/") + localeName + ".res"
            val path = baseName.replace('.', '/')
            return path + (if (localeName.isEmpty()) "" else "_$localeName") + ".res"
        }
        private val EMPTY_BYTES = byteArrayOf()
        private val PUBLIC_TYPES = intArrayOf(0, 1, 2, 3, 2, 2, 0, 7, 8, 8, -1, -1, -1, -1, 14, -1)
        const val RES_BOGUS = -1
        fun RES_GET_TYPE(res: Int): Int = res ushr 28
        fun RES_GET_UINT(res: Int): Int = res and 0x0fffffff
        fun RES_GET_INT(res: Int): Int = (res shl 4) shr 4
        fun URES_IS_ARRAY(type: Int): Boolean = type == 8 || type == 9
        fun URES_IS_TABLE(type: Int): Boolean = type == 2 || type == 4 || type == 5
    }
}
