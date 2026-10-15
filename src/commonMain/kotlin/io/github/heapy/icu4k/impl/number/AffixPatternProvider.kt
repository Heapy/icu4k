// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

interface AffixPatternProvider {
    class Flags {
        companion object {
            const val PLURAL_MASK = 0xff
            const val PREFIX = 0x100
            const val NEGATIVE_SUBPATTERN = 0x200
            const val PADDING = 0x400
        }
    }
    fun charAt(flags: Int, index: Int): Char
    fun length(flags: Int): Int
    fun getString(flags: Int): String
    fun hasCurrencySign(): Boolean
    fun positiveHasPlusSign(): Boolean
    fun hasNegativeSubpattern(): Boolean
    fun negativeHasMinusSign(): Boolean
    fun containsSymbolType(type: Int): Boolean
    fun hasBody(): Boolean
    fun currencyAsDecimal(): Boolean
    companion object {
        const val FLAG_POS_PREFIX = Flags.PREFIX
        const val FLAG_POS_SUFFIX = 0
        const val FLAG_NEG_PREFIX = Flags.PREFIX or Flags.NEGATIVE_SUBPATTERN
        const val FLAG_NEG_SUFFIX = Flags.NEGATIVE_SUBPATTERN
    }
}
