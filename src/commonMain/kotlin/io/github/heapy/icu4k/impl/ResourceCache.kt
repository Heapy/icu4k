// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** ICU resource cache: sorted small table, then a compact radix tree with reclaimable large values. */
internal class ResourceCache(maxOffset: Int) {
    private var keys: IntArray? = IntArray(SIMPLE_LENGTH)
    private var values: Array<Any?>? = arrayOfNulls(SIMPLE_LENGTH)
    private var length = 0
    private var maxOffsetBits = 28
    private var levelBitsList = 0
    private var rootLevel: Level? = null
    private val lock = createResourceCacheLock()
    init {
        var offset = maxOffset
        while (offset <= 0x7ffffff) { offset = offset shl 1; --maxOffsetBits }
        var keyBits = maxOffsetBits + 2
        if (keyBits <= ROOT_BITS) levelBitsList = keyBits
        else if (keyBits < ROOT_BITS + 3) levelBitsList = 0x30 or (keyBits - 3)
        else {
            levelBitsList = ROOT_BITS; keyBits -= ROOT_BITS
            var shift = 4
            while (true) {
                if (keyBits <= NEXT_BITS) { levelBitsList = levelBitsList or (keyBits shl shift); break }
                else if (keyBits < NEXT_BITS + 3) {
                    levelBitsList = levelBitsList or ((0x30 or (keyBits - 3)) shl shift); break
                } else { levelBitsList = levelBitsList or (NEXT_BITS shl shift); keyBits -= NEXT_BITS; shift += 4 }
            }
        }
    }
    private fun makeKey(res: Int): Int {
        val miniType = when (ICUResourceBundleReader.RES_GET_TYPE(res)) { 6 -> 1; 5 -> 3; 9 -> 2; else -> 0 }
        return ICUResourceBundleReader.RES_GET_UINT(res) or (miniType shl maxOffsetBits)
    }
    private fun findSimple(key: Int): Int {
        var low = 0; var high = length - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = keys!![mid]
            if (value < key) low = mid + 1 else if (value > key) high = mid - 1 else return mid
        }
        return low.inv()
    }
    fun get(res: Int): Any? = lock.withLock {
        val value = if (length >= 0) {
            val index = findSimple(res)
            if (index < 0) return@withLock null
            values!![index]
        } else rootLevel!!.get(makeKey(res))
        if (value is Reclaimable) value.reference.get() else value
    }
    fun putIfAbsent(res: Int, item: Any, size: Int): Any = lock.withLock {
        if (length >= 0) {
            var index = findSimple(res)
            if (index >= 0) return@withLock putIfCleared(values!!, index, item, size)
            if (length < SIMPLE_LENGTH) {
                index = index.inv()
                if (index < length) {
                    keys!!.copyInto(keys!!, index + 1, index, length)
                    values!!.copyInto(values!!, index + 1, index, length)
                }
                ++length; keys!![index] = res; values!![index] = stored(item, size)
                return@withLock item
            }
            rootLevel = Level(levelBitsList, 0)
            for (i in 0 until SIMPLE_LENGTH) rootLevel!!.putIfAbsent(makeKey(keys!![i]), values!![i]!!, 0)
            keys = null; values = null; length = -1
        }
        rootLevel!!.putIfAbsent(makeKey(res), item, size)
    }
    private class Reclaimable(val reference: CacheReference<Any>)
    private class Level(val levelBitsList: Int, val shift: Int) {
        val mask = (1 shl (levelBitsList and 0xf)) - 1
        val keys = IntArray(mask + 1)
        val values = arrayOfNulls<Any>(mask + 1)
        fun get(key: Int): Any? {
            val index = (key shr shift) and mask
            val k = keys[index]
            if (k == key) return values[index]
            if (k == 0) return (values[index] as Level?)?.get(key)
            return null
        }
        fun putIfAbsent(key: Int, item: Any, size: Int): Any {
            val index = (key shr shift) and mask
            val k = keys[index]
            if (k == key) return putIfCleared(values, index, item, size)
            if (k == 0) {
                val level = values[index] as Level?
                if (level != null) return level.putIfAbsent(key, item, size)
                keys[index] = key; values[index] = stored(item, size)
                return item
            }
            val level = Level(levelBitsList shr 4, shift + (levelBitsList and 0xf))
            val i = (k shr level.shift) and level.mask
            level.keys[i] = k; level.values[i] = values[index]
            keys[index] = 0; values[index] = level
            return level.putIfAbsent(key, item, size)
        }
    }
    companion object {
        private const val SIMPLE_LENGTH = 32
        private const val ROOT_BITS = 7
        private const val NEXT_BITS = 6
        private fun stored(item: Any, size: Int): Any =
            if (size < 24 || CacheValue.futureInstancesWillBeStrong()) item else Reclaimable(createCacheReference(item))
        private fun putIfCleared(values: Array<Any?>, index: Int, item: Any, size: Int): Any {
            val previous = values[index]
            if (previous !is Reclaimable) return previous!!
            previous.reference.get()?.let { return it }
            values[index] = if (CacheValue.futureInstancesWillBeStrong()) item else Reclaimable(createCacheReference(item))
            return item
        }
    }
}

internal interface ResourceCacheLock { fun <T> withLock(block: () -> T): T }
internal expect fun createResourceCacheLock(): ResourceCacheLock
