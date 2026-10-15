// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.SimpleFormatterImpl
import io.github.heapy.icu4k.impl.number.range.PrefixInfixSuffixLengthHelper
import io.github.heapy.icu4k.text.FormatField
import io.github.heapy.icu4k.util.ICUException
import kotlin.jvm.JvmStatic

open class SimpleModifier(private val compiledPattern: String?, private val field: FormatField?,
    private val strong: Boolean, private val parameters: Modifier.Parameters?
) : Modifier {
    init { assertSimpleModifier(compiledPattern != null) }
    constructor(compiledPattern: String?, field: FormatField?, strong: Boolean) : this(compiledPattern, field, strong, null)
    override fun apply(output: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int =
        SimpleFormatterImpl.formatPrefixSuffix(compiledPattern!!, field, leftIndex, rightIndex, output)
    override fun getPrefixLength(): Int = SimpleFormatterImpl.getPrefixLength(compiledPattern!!)
    override fun getCodePointCount(): Int = SimpleFormatterImpl.getLength(compiledPattern!!, true)
    override fun isStrong(): Boolean = strong
    override fun containsField(field: FormatField?): Boolean { assertSimpleModifier(false); return false }
    override fun getParameters(): Modifier.Parameters? = parameters
    override fun strictEquals(other: Modifier?): Boolean = other is SimpleModifier &&
        compiledPattern!!.equals(other.compiledPattern) && field === other.field && strong == other.strong
    companion object {
        @JvmStatic fun formatTwoArgPattern(compiledPattern: String?, result: FormattedStringBuilder?, index: Int,
                                          h: PrefixInfixSuffixLengthHelper?, field: FormatField?) {
            val pattern = compiledPattern!!
            if (SimpleFormatterImpl.getArgumentLimit(pattern) != 2) throw ICUException()
            var offset = 1
            var length = 0
            var prefixLength = pattern[offset++].code
            if (prefixLength < 0x100) prefixLength = 0 else {
                prefixLength -= 0x100
                result!!.insert(index + length, pattern, offset, offset + prefixLength, field)
                offset += prefixLength; length += prefixLength; offset++
            }
            var infixLength = pattern[offset++].code
            if (infixLength < 0x100) infixLength = 0 else {
                infixLength -= 0x100
                result!!.insert(index + length, pattern, offset, offset + infixLength, field)
                offset += infixLength; length += infixLength; offset++
            }
            val suffixLength: Int
            if (offset == pattern.length) suffixLength = 0 else {
                suffixLength = pattern[offset++].code - 0x100
                result!!.insert(index + length, pattern, offset, offset + suffixLength, field)
                length += suffixLength
            }
            h!!.lengthPrefix = prefixLength
            h.lengthInfix = infixLength
            h.lengthSuffix = suffixLength
        }
    }
}

internal expect fun assertSimpleModifier(condition: Boolean)
