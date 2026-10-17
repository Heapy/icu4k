// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import io.github.heapy.icu4k.impl.locale.LSR
import io.github.heapy.icu4k.impl.locale.LikelySubtags
import io.github.heapy.icu4k.impl.locale.LocaleDistance
import kotlin.jvm.JvmStatic

/** Immutable locale negotiation using CLDR likely subtags and language matching distances. */
class LocaleMatcher private constructor(builder: Builder) {
    enum class FavorSubtag { LANGUAGE, SCRIPT }
    enum class Demotion { NONE, REGION }
    enum class Direction { WITH_ONE_WAY, ONLY_TWO_WAY }
    class Result internal constructor(
        private val desiredULocale: ULocale?, private val supportedULocale: ULocale?,
        private val desiredLocale: Any?, private val supportedLocale: Any?,
        private val desiredIndex: Int, private val supportedIndex: Int,
    ) {
        fun getDesiredULocale(): ULocale? = if (desiredULocale == null && desiredLocale != null) localeMatcherFromHost(desiredLocale) else desiredULocale
        internal fun desiredHost(): Any? = if (desiredLocale == null && desiredULocale != null) localeMatcherToHost(desiredULocale) else desiredLocale
        fun getSupportedULocale(): ULocale? = supportedULocale
        internal fun supportedHost(): Any? = supportedLocale
        fun getDesiredIndex(): Int = desiredIndex
        fun getSupportedIndex(): Int = supportedIndex
        fun makeResolvedULocale(): ULocale? {
            val desired = getDesiredULocale()
            if (supportedULocale == null || desired == null || supportedULocale == desired) return supportedULocale
            val builder = ULocale.Builder().setLocale(supportedULocale)
            val region = desired.getCountry()
            if (region.isNotEmpty()) builder.setRegion(region)
            val variant = desired.getVariant()
            if (variant.isNotEmpty()) builder.setVariant(variant)
            for (key in desired.getExtensionKeys()) builder.setExtension(key, desired.getExtension(key))
            return builder.build()
        }
    }
    class Builder internal constructor() {
        internal var supportedLocales: MutableList<ULocale?>? = null
        internal var thresholdDistance = -1
        internal var demotion: Demotion? = null
        internal var defaultLocale: ULocale? = null
        internal var withDefault = true
        internal var favor: FavorSubtag? = null
        internal var direction: Direction? = null
        internal var maxDistanceDesired: ULocale? = null
        internal var maxDistanceSupported: ULocale? = null
        fun setSupportedLocales(locales: String?): Builder = setSupportedULocales(LocalePriorityList.add(locales).build().getULocales())
        fun setSupportedULocales(locales: Collection<ULocale?>?): Builder { supportedLocales = ArrayList(locales!!); return this }
        fun addSupportedULocale(locale: ULocale?): Builder {
            if (supportedLocales == null) supportedLocales = ArrayList()
            supportedLocales!!.add(locale)
            return this
        }
        fun setNoDefaultLocale(): Builder { defaultLocale = null; withDefault = false; return this }
        fun setDefaultULocale(locale: ULocale?): Builder { defaultLocale = locale; withDefault = true; return this }
        fun setFavorSubtag(subtag: FavorSubtag?): Builder { favor = subtag; return this }
        fun setDemotionPerDesiredLocale(value: Demotion?): Builder { demotion = value; return this }
        fun setDirection(value: Direction?): Builder { direction = value; return this }
        fun setMaxDistance(desired: ULocale?, supported: ULocale?): Builder {
            if (desired == null || supported == null) throw IllegalArgumentException("desired/supported locales must not be null")
            maxDistanceDesired = desired; maxDistanceSupported = supported
            return this
        }
        @Deprecated("This API is ICU internal only.")
        fun internalSetThresholdDistance(value: Int): Builder { thresholdDistance = minOf(value, 100); return this }
        fun build(): LocaleMatcher = LocaleMatcher(this)
        override fun toString(): String = buildString {
            append("{LocaleMatcher.Builder")
            if (!supportedLocales.isNullOrEmpty()) append(" supported={").append(supportedLocales).append('}')
            if (defaultLocale != null) append(" default=").append(defaultLocale)
            if (favor != null) append(" distance=").append(favor)
            if (thresholdDistance >= 0) append(" threshold=").append(localeMatcherFormatInt(thresholdDistance))
            if (demotion != null) append(" demotion=").append(demotion)
            append('}')
        }
    }
    private val thresholdDistance: Int
    private val demotionPerDesiredLocale: Int
    private val favorSubtag = builder.favor
    private val direction = builder.direction
    private val supportedULocales: Array<ULocale?>
    private val supportedLocales: Array<Any?>
    private val supportedLsrToIndex: MutableMap<LSR, Int>
    private val supportedLSRs: Array<LSR?>
    private val supportedIndexes: IntArray
    private val supportedLSRsLength: Int
    private val defaultULocale: ULocale?
    private val defaultLocale: Any?
    constructor(supportedLocales: LocalePriorityList?) : this(builder().setSupportedULocales(supportedLocales!!.getULocales()))
    constructor(supportedLocales: String?) : this(builder().setSupportedLocales(supportedLocales))
    init {
        var udef = builder.defaultLocale
        var def: Any? = null
        var defLSR: LSR? = null
        if (udef != null) { def = localeMatcherToHost(udef); defLSR = maximal(udef) }
        val length = builder.supportedLocales?.size ?: 0
        supportedULocales = arrayOfNulls(length)
        supportedLocales = arrayOfNulls(length)
        val lsrs = arrayOfNulls<LSR>(length)
        if (length > 0) for ((i, locale) in builder.supportedLocales!!.withIndex()) {
            supportedULocales[i] = locale
            supportedLocales[i] = localeMatcherToHost(locale!!)
            lsrs[i] = maximal(locale)
        }
        supportedLsrToIndex = HashMap(length)
        supportedLSRs = arrayOfNulls(length)
        supportedIndexes = IntArray(length)
        var suppLength = 0
        val order = ByteArray(length)
        var numParadigms = 0
        for (i in supportedULocales.indices) {
            val lsr = lsrs[i]!!
            if (defLSR == null && builder.withDefault) {
                udef = supportedULocales[i]
                def = supportedLocales[0]
                defLSR = lsr
                suppLength = putIfAbsent(lsr, 0, suppLength)
            } else if (defLSR != null && lsr.isEquivalentTo(defLSR)) suppLength = putIfAbsent(lsr, i, suppLength)
            else if (LocaleDistance.INSTANCE.isParadigmLSR(lsr)) { order[i] = 2; ++numParadigms }
            else order[i] = 3
        }
        val paradigmLimit = suppLength + numParadigms
        var i = 0
        while (i < length && suppLength < paradigmLimit) {
            if (order[i].toInt() == 2) suppLength = putIfAbsent(lsrs[i]!!, i, suppLength)
            ++i
        }
        for (j in 0 until length) if (order[j].toInt() == 3) suppLength = putIfAbsent(lsrs[j]!!, j, suppLength)
        supportedLSRsLength = suppLength
        defaultULocale = udef
        defaultLocale = def
        demotionPerDesiredLocale = if (builder.demotion == Demotion.NONE) 0 else LocaleDistance.INSTANCE.getDefaultDemotionPerDesiredLocale()
        thresholdDistance = when {
            builder.thresholdDistance >= 0 -> builder.thresholdDistance
            builder.maxDistanceDesired != null -> LocaleDistance.getDistanceFloor(LocaleDistance.INSTANCE.getBestIndexAndDistance(
                maximal(builder.maxDistanceDesired), arrayOf(maximal(builder.maxDistanceSupported)), 1,
                LocaleDistance.shiftDistance(100), favorSubtag, direction)) + 1
            else -> LocaleDistance.INSTANCE.getDefaultScriptDistance()
        }
    }
    private fun putIfAbsent(lsr: LSR, originalIndex: Int, length: Int): Int {
        if (supportedLsrToIndex.containsKey(lsr)) return length
        supportedLsrToIndex[lsr] = originalIndex
        supportedLSRs[length] = lsr
        supportedIndexes[length] = originalIndex
        return length + 1
    }
    private abstract class LsrIterator : Iterator<LSR> {
        var bestDesiredIndex = -1
        abstract fun rememberCurrent(index: Int)
    }
    private class ULocaleLsrIterator(private val locales: Iterator<ULocale?>) : LsrIterator() {
        private var current: ULocale? = null
        var remembered: ULocale? = null
        override fun hasNext(): Boolean = locales.hasNext()
        override fun next(): LSR { current = locales.next(); return maximal(current) }
        override fun rememberCurrent(index: Int) { bestDesiredIndex = index; remembered = current }
    }
    private class HostLsrIterator(private val locales: Iterator<Any?>) : LsrIterator() {
        private var current: Any? = null
        var remembered: Any? = null
        override fun hasNext(): Boolean = locales.hasNext()
        override fun next(): LSR { current = locales.next(); return localeMatcherHostLsr(current!!) }
        override fun rememberCurrent(index: Int) { bestDesiredIndex = index; remembered = current }
    }
    fun getBestMatch(desiredLocale: ULocale?): ULocale? {
        val index = getBestSuppIndex(maximal(desiredLocale), null)
        return if (index >= 0) supportedULocales[index] else defaultULocale
    }
    fun getBestMatch(desiredLocales: Iterable<ULocale?>?): ULocale? {
        val locales = desiredLocales!!.iterator()
        if (!locales.hasNext()) return defaultULocale
        val iter = ULocaleLsrIterator(locales)
        val index = getBestSuppIndex(iter.next(), iter)
        return if (index >= 0) supportedULocales[index] else defaultULocale
    }
    fun getBestMatch(desiredLocaleList: String?): ULocale? = getBestMatch(LocalePriorityList.add(desiredLocaleList).build())
    internal fun bestHost(desired: Any?): Any? {
        val index = getBestSuppIndex(localeMatcherHostLsr(desired!!), null)
        return if (index >= 0) supportedLocales[index] else defaultLocale
    }
    internal fun bestHostList(desired: Iterable<Any?>?): Any? {
        val locales = desired!!.iterator()
        if (!locales.hasNext()) return defaultLocale
        val iter = HostLsrIterator(locales)
        val index = getBestSuppIndex(iter.next(), iter)
        return if (index >= 0) supportedLocales[index] else defaultLocale
    }
    private fun defaultResult() = Result(null, defaultULocale, null, defaultLocale, -1, -1)
    private fun result(desired: ULocale?, hostDesired: Any?, desiredIndex: Int, index: Int): Result =
        if (index < 0) defaultResult() else Result(desired, supportedULocales[index], hostDesired, supportedLocales[index], desiredIndex, index)
    fun getBestMatchResult(desiredLocale: ULocale?): Result = result(desiredLocale, null, 0, getBestSuppIndex(maximal(desiredLocale), null))
    fun getBestMatchResult(desiredLocales: Iterable<ULocale?>?): Result {
        val locales = desiredLocales!!.iterator()
        if (!locales.hasNext()) return defaultResult()
        val iter = ULocaleLsrIterator(locales)
        val index = getBestSuppIndex(iter.next(), iter)
        return result(iter.remembered, null, iter.bestDesiredIndex, index)
    }
    internal fun bestHostResult(desired: Any?): Result = result(null, desired, 0, getBestSuppIndex(localeMatcherHostLsr(desired!!), null))
    internal fun bestHostListResult(desired: Iterable<Any?>?): Result {
        val locales = desired!!.iterator()
        if (!locales.hasNext()) return defaultResult()
        val iter = HostLsrIterator(locales)
        val index = getBestSuppIndex(iter.next(), iter)
        return result(null, iter.remembered, iter.bestDesiredIndex, index)
    }
    private fun getBestSuppIndex(initialDesired: LSR, remaining: LsrIterator?): Int {
        var desired = initialDesired
        var desiredIndex = 0
        var bestLsrIndex = -1
        var bestDistance = LocaleDistance.shiftDistance(thresholdDistance)
        while (true) {
            val exact = supportedLsrToIndex[desired]
            if (exact != null) { remaining?.rememberCurrent(desiredIndex); return exact }
            val packed = LocaleDistance.INSTANCE.getBestIndexAndDistance(desired, supportedLSRs, supportedLSRsLength, bestDistance, favorSubtag, direction)
            if (packed >= 0) {
                bestDistance = LocaleDistance.getShiftedDistance(packed)
                remaining?.rememberCurrent(desiredIndex)
                bestLsrIndex = LocaleDistance.getIndex(packed)
            }
            bestDistance -= LocaleDistance.shiftDistance(demotionPerDesiredLocale)
            if (bestDistance <= 0) break
            if (remaining == null || !remaining.hasNext()) break
            desired = remaining.next()
            ++desiredIndex
        }
        return if (bestLsrIndex < 0) -1 else supportedIndexes[bestLsrIndex]
    }
    fun isMatch(desired: ULocale?, supported: ULocale?): Boolean = distance(maximal(desired), maximal(supported)) >= 0
    internal fun isHostMatch(desired: Any?, supported: Any?): Boolean = distance(localeMatcherHostLsr(desired!!), localeMatcherHostLsr(supported!!)) >= 0
    private fun distance(desired: LSR, supported: LSR): Int = LocaleDistance.INSTANCE.getBestIndexAndDistance(desired, arrayOf(supported), 1,
        LocaleDistance.shiftDistance(thresholdDistance), favorSubtag, direction)
    @Deprecated("This API is ICU internal only.")
    fun match(desired: ULocale?, desiredMax: ULocale?, supported: ULocale?, supportedMax: ULocale?): Double =
        (100.0 - LocaleDistance.getDistanceDouble(distance(maximal(desired), maximal(supported)))) / 100.0
    fun canonicalize(locale: ULocale?): ULocale = LikelySubtags.INSTANCE.canonicalize(locale!!)
    override fun toString(): String = buildString {
        append("{LocaleMatcher")
        if (supportedLSRsLength > 0) {
            append(" supportedLSRs={").append(supportedLSRs[0])
            for (i in 1 until supportedLSRsLength) append(", ").append(supportedLSRs[i])
            append('}')
        }
        append(" default=").append(defaultULocale)
        if (favorSubtag != null) append(" favor=").append(favorSubtag)
        if (direction != null) append(" direction=").append(direction)
        if (thresholdDistance >= 0) append(" threshold=").append(localeMatcherFormatInt(thresholdDistance))
        append(" demotion=").append(localeMatcherFormatInt(demotionPerDesiredLocale)).append('}')
    }
    companion object {
        private val UND_LSR = LSR("und", "", "", LSR.EXPLICIT_LSR)
        private val UND_ULOCALE = ULocale("und")
        private fun maximal(locale: ULocale?): LSR = if (locale!!.equals(UND_ULOCALE)) UND_LSR else LikelySubtags.INSTANCE.makeMaximizedLsrFrom(locale, false)
        @JvmStatic fun builder(): Builder = Builder()
    }
}
internal expect fun localeMatcherToHost(locale: ULocale): Any
internal expect fun localeMatcherFromHost(locale: Any): ULocale
internal expect fun localeMatcherHostLsr(locale: Any): LSR
internal expect fun localeMatcherFormatInt(value: Int): String

internal fun localeMatcherPortableInt(value: Int): String {
    val zero = io.github.heapy.icu4k.text.DecimalFormatSymbols(ULocale.getDefault(ULocale.Category.FORMAT)).getZeroDigit()
    return value.toString().map { if (it in '0'..'9') zero + (it - '0') else it }.joinToString("")
}
