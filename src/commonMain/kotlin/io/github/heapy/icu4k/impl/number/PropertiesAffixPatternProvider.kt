// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import kotlin.jvm.JvmStatic

open class PropertiesAffixPatternProvider internal constructor(properties: DecimalFormatProperties?) : AffixPatternProvider {
    private val posPrefix: String
    private val posSuffix: String
    private val negPrefix: String
    private val negSuffix: String
    private val isCurrencyPattern: Boolean
    private val currencyAsDecimal: Boolean

    init {
        val p = properties!!
        val ppo = AffixUtils.escape(p.getPositivePrefix())
        val pso = AffixUtils.escape(p.getPositiveSuffix())
        val npo = AffixUtils.escape(p.getNegativePrefix())
        val nso = AffixUtils.escape(p.getNegativeSuffix())
        val ppp = p.getPositivePrefixPattern()
        val psp = p.getPositiveSuffixPattern()
        val npp = p.getNegativePrefixPattern()
        val nsp = p.getNegativeSuffixPattern()
        posPrefix = ppo ?: ppp ?: ""
        posSuffix = pso ?: psp ?: ""
        negPrefix = npo ?: npp ?: if (ppp == null) "-" else "-$ppp"
        negSuffix = nso ?: nsp ?: psp ?: ""
        isCurrencyPattern = AffixUtils.hasCurrencySymbols(ppp) || AffixUtils.hasCurrencySymbols(psp) ||
            AffixUtils.hasCurrencySymbols(npp) || AffixUtils.hasCurrencySymbols(nsp) || p.getCurrencyAsDecimal()
        currencyAsDecimal = p.getCurrencyAsDecimal()
    }

    override fun charAt(flags: Int, index: Int): Char = getString(flags)[index]
    override fun length(flags: Int): Int = getString(flags).length
    override fun getString(flags: Int): String = when {
        flags and AffixPatternProvider.Flags.PREFIX != 0 ->
            if (flags and AffixPatternProvider.Flags.NEGATIVE_SUBPATTERN != 0) negPrefix else posPrefix
        flags and AffixPatternProvider.Flags.NEGATIVE_SUBPATTERN != 0 -> negSuffix
        else -> posSuffix
    }
    override fun positiveHasPlusSign(): Boolean = AffixUtils.containsType(posPrefix, AffixUtils.TYPE_PLUS_SIGN) ||
        AffixUtils.containsType(posSuffix, AffixUtils.TYPE_PLUS_SIGN)
    // ICU intentionally uses reference identity for the suffix comparison.
    override fun hasNegativeSubpattern(): Boolean = negSuffix !== posSuffix ||
        negPrefix.length != posPrefix.length + 1 ||
        !negPrefix.regionMatches(1, posPrefix, 0, posPrefix.length) || negPrefix[0] != '-'
    override fun negativeHasMinusSign(): Boolean = AffixUtils.containsType(negPrefix, AffixUtils.TYPE_MINUS_SIGN) ||
        AffixUtils.containsType(negSuffix, AffixUtils.TYPE_MINUS_SIGN)
    override fun hasCurrencySign(): Boolean = isCurrencyPattern
    override fun containsSymbolType(type: Int): Boolean = AffixUtils.containsType(posPrefix, type) ||
        AffixUtils.containsType(posSuffix, type) || AffixUtils.containsType(negPrefix, type) ||
        AffixUtils.containsType(negSuffix, type)
    override fun hasBody(): Boolean = true
    override fun currencyAsDecimal(): Boolean = currencyAsDecimal
    override fun toString(): String = super.toString() + " {$posPrefix#$posSuffix;$negPrefix#$negSuffix}"

    companion object {
        @JvmStatic fun forProperties(properties: DecimalFormatProperties?): AffixPatternProvider =
            if (properties!!.getCurrencyPluralInfo() == null) PropertiesAffixPatternProvider(properties)
            else CurrencyPluralInfoAffixProvider(properties.getCurrencyPluralInfo(), properties)
    }
}
