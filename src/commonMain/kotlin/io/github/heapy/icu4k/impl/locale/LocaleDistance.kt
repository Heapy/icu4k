// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.impl.*
import io.github.heapy.icu4k.util.BytesTrie
import io.github.heapy.icu4k.util.LocaleMatcher
import io.github.heapy.icu4k.util.MissingResourceException
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** CLDR language/script/region distances, encoded in ICU's byte trie. */
class LocaleDistance private constructor(data: Data) {
    class Data(
        @JvmField var trie: ByteArray,
        @JvmField var regionToPartitionsIndex: ByteArray,
        @JvmField var partitionArrays: Array<String>,
        @JvmField var paradigmLSRs: Set<LSR>,
        @JvmField var distances: IntArray,
    ) {
        override fun equals(other: Any?): Boolean = this === other || other is Data &&
            trie.contentEquals(other.trie) && regionToPartitionsIndex.contentEquals(other.regionToPartitionsIndex) &&
            partitionArrays.contentEquals(other.partitionArrays) && paradigmLSRs == other.paradigmLSRs && distances.contentEquals(other.distances)
        override fun hashCode(): Int = 1
        companion object {
            @JvmStatic fun load(): Data {
                val bundle = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, "langInfo", defaultICUResourceLoader(), ICUResourceBundle.OpenType.DIRECT)
                val value = bundle.getValueWithFallback("match")
                val table = value.getTable()
                fun required(table: UResource.Table, key: String): UResource.Value {
                    if (!table.findValue(key, value)) throw MissingResourceException("langInfo.res missing data", "", "match/$key")
                    return value
                }
                val trieBuffer = required(table, "trie").getBinary()
                val trie = trieBuffer.readBytes(trieBuffer.remaining)
                val regionBuffer = required(table, "regionToPartitions").getBinary()
                val regions = regionBuffer.readBytes(regionBuffer.remaining)
                if (regions.size < LSR.REGION_INDEX_LIMIT) throw MissingResourceException("langInfo.res binary data too short", "", "match/regionToPartitions")
                val partitions = required(table, "partitions").getStringArray()
                val paradigms = if (table.findValue("paradigmnum", value)) {
                    val m49 = required(bundle.getValueWithFallback("likely").getTable(), "m49").getStringArray()
                    LSR.decodeInts(required(table, "paradigmnum").getIntVector(), m49).toCollection(LinkedHashSet())
                } else emptySet()
                val distances = required(table, "distances").getIntVector()
                if (distances.size < IX_LIMIT) throw MissingResourceException("langInfo.res intvector too short", "", "match/distances")
                return Data(trie, regions, partitions, paradigms, distances)
            }
        }
    }
    private val trie = BytesTrie(data.trie, 0)
    private val regionToPartitionsIndex = data.regionToPartitionsIndex
    private val partitionArrays = data.partitionArrays
    private val paradigmLSRs = data.paradigmLSRs
    private val defaultLanguageDistance = data.distances[IX_DEF_LANG_DISTANCE]
    private val defaultScriptDistance = data.distances[IX_DEF_SCRIPT_DISTANCE]
    private val defaultRegionDistance = data.distances[IX_DEF_REGION_DISTANCE]
    private val minRegionDistance = data.distances[IX_MIN_REGION_DISTANCE]
    private val defaultDemotionPerDesiredLocale: Int
    init {
        val distance = getBestIndexAndDistance(LSR("en", "Latn", "US", LSR.EXPLICIT_LSR),
            arrayOf(LSR("en", "Latn", "GB", LSR.EXPLICIT_LSR)), 1,
            shiftDistance(50), LocaleMatcher.FavorSubtag.LANGUAGE, LocaleMatcher.Direction.WITH_ONE_WAY)
        defaultDemotionPerDesiredLocale = getDistanceFloor(distance)
    }
    fun testOnlyDistance(desired: ULocale, supported: ULocale, threshold: Int, favorSubtag: LocaleMatcher.FavorSubtag?): Int {
        val supportedLSR = LikelySubtags.INSTANCE.makeMaximizedLsrFrom(supported, false)
        val desiredLSR = LikelySubtags.INSTANCE.makeMaximizedLsrFrom(desired, false)
        return getDistanceFloor(getBestIndexAndDistance(desiredLSR, arrayOf(supportedLSR), 1,
            shiftDistance(threshold), favorSubtag, LocaleMatcher.Direction.WITH_ONE_WAY))
    }
    fun getBestIndexAndDistance(desired: LSR, supportedLSRs: Array<out LSR?>, supportedLSRsLength: Int,
        initialThreshold: Int, favorSubtag: LocaleMatcher.FavorSubtag?, direction: LocaleMatcher.Direction?): Int {
        var threshold = initialThreshold
        val iter = BytesTrie(trie)
        val desLangDistance = trieNext(iter, desired.language, false)
        val desLangState = if (desLangDistance >= 0 && supportedLSRsLength > 1) iter.getState64() else 0L
        var bestIndex = -1
        var bestLikelyInfo = -1
        for (index in 0 until supportedLSRsLength) {
            val supported = supportedLSRs[index]!!
            var star = false
            var distance = desLangDistance
            if (distance >= 0) {
                assertLocaleDistance { (distance and DISTANCE_IS_FINAL) == 0 }
                if (index != 0) iter.resetToState64(desLangState)
                distance = trieNext(iter, supported.language, true)
            }
            var flags: Int
            if (distance >= 0) {
                flags = distance and DISTANCE_IS_FINAL_OR_SKIP_SCRIPT
                distance = distance and DISTANCE_IS_FINAL_OR_SKIP_SCRIPT.inv()
            } else {
                distance = if (desired.language == supported.language) 0 else defaultLanguageDistance
                flags = 0
                star = true
            }
            assertLocaleDistance { distance in 0..100 }
            val roundedThreshold = (threshold + DISTANCE_FRACTION_MASK) shr DISTANCE_SHIFT
            if (favorSubtag == LocaleMatcher.FavorSubtag.SCRIPT) distance = distance shr 2
            if (distance > roundedThreshold) continue
            val scriptDistance: Int
            if (star || flags != 0) scriptDistance = if (desired.script == supported.script) 0 else defaultScriptDistance
            else {
                val d = getDesSuppScriptDistance(iter, iter.getState64(), desired.script, supported.script)
                flags = d and DISTANCE_IS_FINAL
                scriptDistance = d and DISTANCE_IS_FINAL.inv()
            }
            distance += scriptDistance
            if (distance > roundedThreshold) continue
            if (desired.region == supported.region) {
                // No regional penalty.
            } else if (star || (flags and DISTANCE_IS_FINAL) != 0) distance += defaultRegionDistance
            else {
                val remaining = roundedThreshold - distance
                if (minRegionDistance > remaining) continue
                distance += getRegionPartitionsDistance(iter, iter.getState64(), partitionsForRegion(desired), partitionsForRegion(supported), remaining)
            }
            var shiftedDistance = shiftDistance(distance)
            if (shiftedDistance == 0) {
                shiftedDistance = shiftedDistance or (desired.flags xor supported.flags)
                if (shiftedDistance < threshold && (direction != LocaleMatcher.Direction.ONLY_TWO_WAY || isMatch(supported, desired, threshold, favorSubtag))) {
                    if (shiftedDistance == 0) return index shl INDEX_SHIFT
                    bestIndex = index
                    threshold = shiftedDistance
                    bestLikelyInfo = -1
                }
            } else {
                if (shiftedDistance < threshold) {
                    if (direction != LocaleMatcher.Direction.ONLY_TWO_WAY || isMatch(supported, desired, threshold, favorSubtag)) {
                        bestIndex = index
                        threshold = shiftedDistance
                        bestLikelyInfo = -1
                    }
                } else if (shiftedDistance == threshold && bestIndex >= 0) {
                    if (direction != LocaleMatcher.Direction.ONLY_TWO_WAY || isMatch(supported, desired, threshold, favorSubtag)) {
                        bestLikelyInfo = LikelySubtags.INSTANCE.compareLikely(supported, supportedLSRs[bestIndex]!!, bestLikelyInfo)
                        if ((bestLikelyInfo and 1) != 0) bestIndex = index
                    }
                }
            }
        }
        return if (bestIndex >= 0) (bestIndex shl INDEX_SHIFT) or threshold else INDEX_NEG_1 or shiftDistance(ABOVE_THRESHOLD)
    }
    private fun isMatch(desired: LSR, supported: LSR, threshold: Int, favor: LocaleMatcher.FavorSubtag?): Boolean =
        getBestIndexAndDistance(desired, arrayOf(supported), 1, threshold, favor, null) >= 0
    private fun getDesSuppScriptDistance(iter: BytesTrie, state: Long, desired: String, supported: String): Int {
        var distance = trieNext(iter, desired, false)
        if (distance >= 0) distance = trieNext(iter, supported, true)
        if (distance < 0) {
            val result = iter.resetToState64(state).next('*'.code)
            assertLocaleDistance { result.hasValue() }
            distance = if (desired == supported) 0 else iter.getValue().also { d -> assertLocaleDistance { d >= 0 } }
            if (result == BytesTrie.Result.FINAL_VALUE) distance = distance or DISTANCE_IS_FINAL
        }
        return distance
    }
    private fun getRegionPartitionsDistance(iter: BytesTrie, state: Long, desired: String, supported: String, threshold: Int): Int {
        val desLength = desired.length
        val suppLength = supported.length
        if (desLength == 1 && suppLength == 1) {
            var result = iter.next(desired[0].code or END_OF_SUBTAG)
            if (result.hasNext()) {
                result = iter.next(supported[0].code or END_OF_SUBTAG)
                if (result.hasValue()) return iter.getValue()
            }
            return getFallbackRegionDistance(iter, state)
        }
        var regionDistance = 0
        var star = false
        var di = 0
        while (true) {
            val result = iter.next(desired[di++].code or END_OF_SUBTAG)
            if (result.hasNext()) {
                val desState = if (suppLength > 1) iter.getState64() else 0L
                var si = 0
                while (true) {
                    val next = iter.next(supported[si++].code or END_OF_SUBTAG)
                    val distance = if (next.hasValue()) iter.getValue() else if (star) 0
                        else getFallbackRegionDistance(iter, state).also { star = true }
                    if (distance > threshold) return distance
                    if (regionDistance < distance) regionDistance = distance
                    if (si < suppLength) iter.resetToState64(desState) else break
                }
            } else if (!star) {
                val distance = getFallbackRegionDistance(iter, state)
                if (distance > threshold) return distance
                if (regionDistance < distance) regionDistance = distance
                star = true
            }
            if (di < desLength) iter.resetToState64(state) else break
        }
        return regionDistance
    }
    private fun getFallbackRegionDistance(iter: BytesTrie, state: Long): Int {
        val result = iter.resetToState64(state).next('*'.code)
        assertLocaleDistance { result.hasValue() }
        return iter.getValue().also { d -> assertLocaleDistance { d >= 0 } }
    }
    private fun trieNext(iter: BytesTrie, text: String, wantValue: Boolean): Int {
        if (text.isEmpty()) return -1
        for (i in text.indices) {
            val c = text[i].code
            if (i < text.lastIndex) { if (!iter.next(c).hasNext()) return -1 }
            else {
                val result = iter.next(c or END_OF_SUBTAG)
                if (wantValue) {
                    if (result.hasValue()) return iter.getValue() or (if (result == BytesTrie.Result.FINAL_VALUE) DISTANCE_IS_FINAL else 0)
                } else if (result.hasNext()) return 0
                return -1
            }
        }
        return -1
    }
    private fun partitionsForRegion(lsr: LSR): String = partitionArrays[regionToPartitionsIndex[lsr.regionIndex].toInt()]
    fun isParadigmLSR(lsr: LSR): Boolean {
        assertLocaleDistance { paradigmLSRs.size <= 15 }
        return paradigmLSRs.any { lsr.isEquivalentTo(it) }
    }
    fun getDefaultScriptDistance(): Int = defaultScriptDistance
    internal fun getDefaultRegionDistance(): Int = defaultRegionDistance
    fun getDefaultDemotionPerDesiredLocale(): Int = defaultDemotionPerDesiredLocale
    fun testOnlyGetDistanceTable(): MutableMap<String, Int> {
        val entries = ArrayList<Pair<String, Int>>()
        for (entry in trie) {
            val key = buildString {
                for (i in 0 until entry.bytesLength()) {
                    val byte = entry.byteAt(i).toInt()
                    if (byte == '*'.code) append("*-*-")
                    else if (byte >= 0) append(byte.toChar()) else append((byte and 0x7f).toChar()).append('-')
                }
            }
            assertLocaleDistance { key.isNotEmpty() && key.last() == '-' }
            entries.add(key.dropLast(1) to entry.value)
        }
        return entries.sortedBy { it.first }.toMap(LinkedHashMap())
    }
    fun testOnlyPrintDistanceTable() {
        for ((key, original) in testOnlyGetDistanceTable()) {
            val skip = (original and DISTANCE_SKIP_SCRIPT) != 0
            val value = if (skip) original and DISTANCE_SKIP_SCRIPT.inv() else original
            println("$key=$value" + if (skip) " skip script" else "")
        }
    }
    override fun toString(): String = testOnlyGetDistanceTable().toString()
    companion object {
        const val END_OF_SUBTAG = 0x80
        const val DISTANCE_SKIP_SCRIPT = 0x80
        private const val DISTANCE_IS_FINAL = 0x100
        private const val DISTANCE_IS_FINAL_OR_SKIP_SCRIPT = DISTANCE_IS_FINAL or DISTANCE_SKIP_SCRIPT
        private const val DISTANCE_SHIFT = 3
        private const val DISTANCE_FRACTION_MASK = 7
        private const val DISTANCE_INT_SHIFT = 7
        private const val INDEX_SHIFT = DISTANCE_INT_SHIFT + DISTANCE_SHIFT
        private const val DISTANCE_MASK = 0x3ff
        private const val INDEX_NEG_1 = -1024
        const val IX_DEF_LANG_DISTANCE = 0
        const val IX_DEF_SCRIPT_DISTANCE = 1
        const val IX_DEF_REGION_DISTANCE = 2
        const val IX_MIN_REGION_DISTANCE = 3
        const val IX_LIMIT = 4
        private const val ABOVE_THRESHOLD = 100
        @JvmField val INSTANCE = LocaleDistance(Data.load())
        @JvmStatic fun shiftDistance(distance: Int): Int = distance shl DISTANCE_SHIFT
        @JvmStatic fun getShiftedDistance(indexAndDistance: Int): Int = indexAndDistance and DISTANCE_MASK
        @JvmStatic fun getDistanceDouble(indexAndDistance: Int): Double = getShiftedDistance(indexAndDistance).toDouble() / (1 shl DISTANCE_SHIFT)
        @JvmStatic fun getDistanceFloor(indexAndDistance: Int): Int = (indexAndDistance and DISTANCE_MASK) shr DISTANCE_SHIFT
        @JvmStatic fun getIndex(indexAndDistance: Int): Int { assertLocaleDistance { indexAndDistance >= 0 }; return indexAndDistance shr INDEX_SHIFT }
    }
}
internal expect fun assertLocaleDistance(condition: () -> Boolean)
