// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.text.FormatField
import kotlin.jvm.JvmField

open class ConstantMultiFieldModifier(prefix: FormattedStringBuilder?, suffix: FormattedStringBuilder?,
    private val overwrite: Boolean, private val strong: Boolean, private val parameters: Modifier.Parameters?
) : Modifier {
    @JvmField protected val prefixChars: CharArray = prefix!!.toCharArray()
    @JvmField protected val suffixChars: CharArray = suffix!!.toCharArray()
    @JvmField protected val prefixFields: Array<Any?> = prefix!!.toFieldArray()
    @JvmField protected val suffixFields: Array<Any?> = suffix!!.toFieldArray()
    constructor(prefix: FormattedStringBuilder?, suffix: FormattedStringBuilder?, overwrite: Boolean, strong: Boolean) :
        this(prefix, suffix, overwrite, strong, null)
    override fun apply(output: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int {
        var length = output!!.insert(leftIndex, prefixChars, prefixFields)
        if (overwrite) length += output.splice(leftIndex + length, rightIndex + length, "", 0, 0, null)
        length += output.insert(rightIndex + length, suffixChars, suffixFields)
        return length
    }
    override fun getPrefixLength(): Int = prefixChars.size
    override fun getCodePointCount(): Int = modifierCodePointCount(prefixChars) + modifierCodePointCount(suffixChars)
    override fun isStrong(): Boolean = strong
    override fun containsField(field: FormatField?): Boolean = prefixFields.any { it === field } || suffixFields.any { it === field }
    override fun getParameters(): Modifier.Parameters? = parameters
    override fun strictEquals(other: Modifier?): Boolean = other is ConstantMultiFieldModifier &&
        prefixChars.contentEquals(other.prefixChars) && prefixFields.contentEquals(other.prefixFields) &&
        suffixChars.contentEquals(other.suffixChars) && suffixFields.contentEquals(other.suffixFields) &&
        overwrite == other.overwrite && strong == other.strong
    override fun toString(): String {
        val temp = FormattedStringBuilder()
        apply(temp, 0, 0)
        val prefixLength = getPrefixLength()
        return "<ConstantMultiFieldModifier prefix:'${temp.subSequence(0, prefixLength)}' suffix:'${temp.subSequence(prefixLength, temp.length)}'>"
    }
}
