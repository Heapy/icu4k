// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number.parse

import io.github.heapy.icu4k.impl.number.AffixUtils
import kotlin.jvm.JvmStatic

open class AffixPatternMatcher private constructor(private val affixPattern: String) : SeriesMatcher(), AffixUtils.TokenConsumer {
    private var factory: AffixTokenMatcherFactory? = null
    private var ignorables: IgnorablesMatcher? = null
    private var lastTypeOrCp = 0
    override fun consumeToken(typeOrCp: Int) {
        if (ignorables != null && length() > 0 && (lastTypeOrCp < 0 || !ignorables!!.getSet()!!.contains(lastTypeOrCp))) addMatcher(ignorables)
        if (typeOrCp < 0) {
            addMatcher(when (typeOrCp) {
                AffixUtils.TYPE_MINUS_SIGN -> factory!!.minusSign()
                AffixUtils.TYPE_PLUS_SIGN -> factory!!.plusSign()
                AffixUtils.TYPE_APPROXIMATELY_SIGN -> factory!!.approximatelySign()
                AffixUtils.TYPE_PERCENT -> factory!!.percent()
                AffixUtils.TYPE_PERMILLE -> factory!!.permille()
                AffixUtils.TYPE_CURRENCY_SINGLE, AffixUtils.TYPE_CURRENCY_DOUBLE, AffixUtils.TYPE_CURRENCY_TRIPLE,
                AffixUtils.TYPE_CURRENCY_QUAD, AffixUtils.TYPE_CURRENCY_QUINT, AffixUtils.TYPE_CURRENCY_OVERFLOW -> factory!!.currency()
                else -> throw AssertionError()
            })
        } else if (ignorables != null && ignorables!!.getSet()!!.contains(typeOrCp)) {
            // The weak matcher was inserted above.
        } else addMatcher(CodePointMatcher.getInstance(typeOrCp))
        lastTypeOrCp = typeOrCp
    }
    open fun getPattern(): String = affixPattern
    override fun equals(other: Any?): Boolean = this === other || other is AffixPatternMatcher && affixPattern == other.affixPattern
    override fun hashCode(): Int = affixPattern.hashCode()
    override fun toString(): String = affixPattern
    companion object {
        @JvmStatic fun fromAffixPattern(affixPattern: String?, factory: AffixTokenMatcherFactory?, parseFlags: Int): AffixPatternMatcher? {
            if (affixPattern!!.isEmpty()) return null
            val series = AffixPatternMatcher(affixPattern)
            series.factory = factory
            series.ignorables = if (parseFlags and ParsingUtils.PARSE_FLAG_EXACT_AFFIX != 0) null else factory!!.ignorables()
            series.lastTypeOrCp = 0
            AffixUtils.iterateWithConsumer(affixPattern, series)
            series.factory = null; series.ignorables = null; series.lastTypeOrCp = 0
            series.freeze()
            return series
        }
    }
}
