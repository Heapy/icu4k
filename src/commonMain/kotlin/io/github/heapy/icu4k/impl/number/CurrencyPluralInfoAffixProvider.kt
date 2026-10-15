// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.text.CurrencyPluralInfo

open class CurrencyPluralInfoAffixProvider(cpi: CurrencyPluralInfo?, properties: DecimalFormatProperties?) : AffixPatternProvider {
    private val affixesByPlural: Array<PropertiesAffixPatternProvider>
    init {
        val pluralProperties = DecimalFormatProperties().copyFrom(properties)
        affixesByPlural = Array(StandardPlural.COUNT) { ordinal ->
            val pattern = cpi!!.getCurrencyPluralPattern(StandardPlural.VALUES[ordinal].getKeyword())
            PatternStringParserCore.parseToExistingProperties(pattern, pluralProperties)
            PropertiesAffixPatternProvider(pluralProperties)
        }
    }
    private fun affix(flags: Int) = affixesByPlural[flags and AffixPatternProvider.Flags.PLURAL_MASK]
    private fun other() = affixesByPlural[StandardPlural.OTHER_INDEX]
    override fun charAt(flags: Int, index: Int): Char = affix(flags).charAt(flags, index)
    override fun length(flags: Int): Int = affix(flags).length(flags)
    override fun getString(flags: Int): String = affix(flags).getString(flags)
    override fun positiveHasPlusSign(): Boolean = other().positiveHasPlusSign()
    override fun hasNegativeSubpattern(): Boolean = other().hasNegativeSubpattern()
    override fun negativeHasMinusSign(): Boolean = other().negativeHasMinusSign()
    override fun hasCurrencySign(): Boolean = other().hasCurrencySign()
    override fun containsSymbolType(type: Int): Boolean = other().containsSymbolType(type)
    override fun hasBody(): Boolean = other().hasBody()
    override fun currencyAsDecimal(): Boolean = other().currencyAsDecimal()
}
