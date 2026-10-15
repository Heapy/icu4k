// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.FormatField
import kotlin.jvm.JvmField

open class ConstantAffixModifier(prefix: String?, suffix: String?, private val field: FormatField?, private val strong: Boolean) : Modifier {
    private val prefix = prefix ?: ""
    private val suffix = suffix ?: ""
    constructor() : this("", "", null, false)
    override fun apply(output: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int {
        var length = output!!.insert(rightIndex, suffix, field)
        length += output.insert(leftIndex, prefix, field)
        return length
    }
    override fun getPrefixLength(): Int = prefix.length
    override fun getCodePointCount(): Int = modifierCodePointCount(prefix) + modifierCodePointCount(suffix)
    override fun isStrong(): Boolean = strong
    override fun containsField(field: FormatField?): Boolean { assertConstantAffixModifier(false); return false }
    override fun getParameters(): Modifier.Parameters? = null
    override fun strictEquals(other: Modifier?): Boolean = other is ConstantAffixModifier &&
        prefix == other.prefix && suffix == other.suffix && field === other.field && strong == other.strong
    override fun toString(): String = "<ConstantAffixModifier prefix:'$prefix' suffix:'$suffix'>"
    companion object { @JvmField val EMPTY = ConstantAffixModifier() }
}

internal expect fun assertConstantAffixModifier(condition: Boolean)
