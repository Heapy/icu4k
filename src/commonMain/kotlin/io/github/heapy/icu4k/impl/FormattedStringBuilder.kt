// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.FormatField
import io.github.heapy.icu4k.text.NumberFormatField

/** Centered UTF-16 buffer with a field identity attached to every code unit. */
open class FormattedStringBuilder(capacity: Int = 40) : CharSequence, Appendable {
    internal var chars = CharArray(capacity)
    internal var fields = arrayOfNulls<Any>(capacity)
    internal var zero = capacity / 2
    private var size = 0
    internal var appendOffset = 0
    internal var appendableField: Any? = null
    constructor(source: FormattedStringBuilder) : this(0) { copyFrom(source) }
    fun copyFrom(source: FormattedStringBuilder) {
        chars = source.chars.copyOf(); fields = source.fields.copyOf(); zero = source.zero; size = source.size
    }
    override val length: Int get() = size
    override fun get(index: Int): Char = fsbCharAt(chars, zero + index)
    fun fieldAt(index: Int): Any? = fsbFieldAt(fields, zero + index)
    fun codePointCount(): Int {
        var count = 0; var i = 0
        while (i < size) { val c = get(i++); if (c.isHighSurrogate() && i < size && get(i).isLowSurrogate()) i++; count++ }
        return count
    }
    fun getFirstCodePoint(): Int = if (size == 0) -1 else codePointAt(0)
    fun getLastCodePoint(): Int = if (size == 0) -1 else codePointBefore(size)
    fun codePointAt(index: Int): Int = fsbCodePointAt(chars, zero + index, zero + size)
    fun codePointBefore(index: Int): Int = fsbCodePointBefore(chars, zero + index, zero)
    fun clear(): FormattedStringBuilder { zero = chars.size / 2; size = 0; return this }
    fun setAppendIndex(index: Int) { appendOffset = size - index }
    fun appendChar16(codeUnit: Char, field: Any?): Int = insertChar16(size - appendOffset, codeUnit, field)
    fun insertChar16(index: Int, codeUnit: Char, field: Any?): Int {
        val position = prepareForInsert(index, 1)
        chars[position] = codeUnit; fields[position] = field
        return 1
    }
    fun appendCodePoint(codePoint: Int, field: Any?): Int = insertCodePoint(size - appendOffset, codePoint, field)
    fun insertCodePoint(index: Int, codePoint: Int, field: Any?): Int {
        val count = if (codePoint >= 0x10000) 2 else 1
        val position = prepareForInsert(index, count)
        if (codePoint !in 0..0x10ffff) throw IllegalArgumentException("Not a valid Unicode code point: 0x${codePoint.toUInt().toString(16).uppercase()}")
        if (count == 1) chars[position] = codePoint.toChar() else {
            chars[position] = ((codePoint ushr 10) + 0xd7c0).toChar()
            chars[position + 1] = ((codePoint and 0x3ff) + 0xdc00).toChar()
        }
        fields[position] = field
        if (count == 2) fields[position + 1] = field
        return count
    }
    fun append(sequence: CharSequence, field: Any?): Int = insert(size - appendOffset, sequence, field)
    fun insert(index: Int, sequence: CharSequence, field: Any?): Int = when (sequence.length) {
        0 -> 0
        1 -> insertCodePoint(index, sequence[0].code, field)
        else -> insert(index, sequence, 0, sequence.length, field)
    }
    fun insert(index: Int, sequence: CharSequence, start: Int, end: Int, field: Any?): Int {
        val count = end - start
        val position = prepareForInsert(index, count)
        for (i in 0 until count) { chars[position + i] = sequence[start + i]; fields[position + i] = field }
        return count
    }
    fun splice(startThis: Int, endThis: Int, sequence: CharSequence, startOther: Int, endOther: Int, field: Any?): Int {
        val otherLength = endOther - startOther
        val count = otherLength - (endThis - startThis)
        val position = if (count > 0) prepareForInsert(startThis, count) else remove(startThis, -count)
        for (i in 0 until otherLength) { chars[position + i] = sequence[startOther + i]; fields[position + i] = field }
        return count
    }
    fun append(chars: CharArray, fields: Array<out Any?>?): Int = insert(size - appendOffset, chars, fields)
    fun insert(index: Int, chars: CharArray, fields: Array<out Any?>?): Int {
        val count = chars.size
        if (count == 0) return 0
        val position = prepareForInsert(index, count)
        for (i in 0 until count) { this.chars[position + i] = fsbCharAt(chars, i); this.fields[position + i] = fields?.let { fsbFieldAt(it, i) } }
        return count
    }
    fun append(other: FormattedStringBuilder): Int = insert(size - appendOffset, other)
    fun insert(index: Int, other: FormattedStringBuilder): Int {
        if (this === other) throw IllegalArgumentException("Cannot call insert/append on myself")
        val count = other.size
        if (count == 0) return 0
        val position = prepareForInsert(index, count)
        for (i in 0 until count) { chars[position + i] = other[i]; fields[position + i] = other.fieldAt(i) }
        return count
    }
    private fun prepareForInsert(indexIn: Int, count: Int): Int {
        val index = if (indexIn == -1) size else indexIn
        if (index == 0 && zero - count >= 0) { zero -= count; size += count; return zero }
        if (index == size && zero + size + count < chars.size) { size += count; return zero + size - count }
        val oldCapacity = chars.size
        val oldZero = zero
        if (size + count > oldCapacity) {
            val capacity = (size + count) * 2
            val newZero = capacity / 2 - (size + count) / 2
            val newChars = CharArray(capacity)
            val newFields = arrayOfNulls<Any>(capacity)
            chars.copyInto(newChars, newZero, oldZero, oldZero + index)
            chars.copyInto(newChars, newZero + index + count, oldZero + index, oldZero + size)
            fields.copyInto(newFields, newZero, oldZero, oldZero + index)
            fields.copyInto(newFields, newZero + index + count, oldZero + index, oldZero + size)
            chars = newChars; fields = newFields; zero = newZero; size += count
        } else {
            val newZero = oldCapacity / 2 - (size + count) / 2
            chars.copyInto(chars, newZero, oldZero, oldZero + size)
            chars.copyInto(chars, newZero + index + count, newZero + index, newZero + size)
            fields.copyInto(fields, newZero, oldZero, oldZero + size)
            fields.copyInto(fields, newZero + index + count, newZero + index, newZero + size)
            zero = newZero; size += count
        }
        return zero + index
    }
    private fun remove(index: Int, count: Int): Int {
        val position = index + zero
        chars.copyInto(chars, position, position + count, zero + size)
        fields.copyInto(fields, position, position + count, zero + size)
        size -= count
        return position
    }
    @Deprecated("Use subString when returning publicly.")
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = FormattedStringBuilder(this).also {
        it.zero = zero + startIndex; it.size = endIndex - startIndex
    }
    fun subString(start: Int, end: Int): String {
        if (start < 0 || end > size || end < start) throw IndexOutOfBoundsException()
        return chars.concatToString(zero + start, zero + end)
    }
    override fun toString(): String = chars.concatToString(zero, zero + size)
    fun toDebugString(): String = buildString {
        append("<FormattedStringBuilder [").append(this@FormattedStringBuilder.toString()).append("] [")
        for (i in zero until zero + size) append(if (fields[i] == null) 'n' else debugFields[fields[i]] ?: '?')
        append("]>")
    }
    fun toCharArray(): CharArray = chars.copyOfRange(zero, zero + size)
    fun toFieldArray(): Array<Any?> = fields.copyOfRange(zero, zero + size)
    fun setAppendableField(field: Any?) { appendableField = field }
    override fun append(value: CharSequence?): Appendable { insert(size - appendOffset, value!!, appendableField); return this }
    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable { insert(size - appendOffset, value!!, startIndex, endIndex, appendableField); return this }
    override fun append(value: Char): Appendable { insertChar16(size - appendOffset, value, appendableField); return this }
    fun contentEquals(chars: CharArray, fields: Array<out Any?>): Boolean {
        if (chars.size != size || fields.size != size) return false
        for (i in 0 until size) if (this.chars[zero + i] != fsbCharAt(chars, i) || unwrapField(this.fields[zero + i]) !== unwrapField(fsbFieldAt(fields, i))) return false
        return true
    }
    fun contentEquals(other: FormattedStringBuilder): Boolean {
        if (size != other.size) return false
        for (i in 0 until size) if (get(i) != other[i] || unwrapField(fieldAt(i)) !== unwrapField(other.fieldAt(i))) return false
        return true
    }
    override fun equals(other: Any?): Boolean = throw UnsupportedOperationException("Don't call #hashCode() or #equals() on a mutable.")
    override fun hashCode(): Int = throw UnsupportedOperationException("Don't call #hashCode() or #equals() on a mutable.")
    fun interface FieldWrapper { fun unwrap(): FormatField? }
    companion object {
        val EMPTY = FormattedStringBuilder()
        fun unwrapField(field: Any?): FormatField? = when (field) {
            null -> null
            is FieldWrapper -> field.unwrap()
            is FormatField -> field
            else -> throw AssertionError("Not a field: $field")
        }
        private fun pair(high: Char, low: Char): Int = ((high.code - 0xd800) shl 10) + low.code - 0xdc00 + 0x10000
        private val debugFields = mapOf<Any, Char>(NumberFormatField.SIGN to '-', NumberFormatField.INTEGER to 'i',
            NumberFormatField.FRACTION to 'f', NumberFormatField.EXPONENT to 'e', NumberFormatField.EXPONENT_SIGN to '+',
            NumberFormatField.EXPONENT_SYMBOL to 'E', NumberFormatField.DECIMAL_SEPARATOR to '.', NumberFormatField.GROUPING_SEPARATOR to ',',
            NumberFormatField.PERCENT to '%', NumberFormatField.PERMILLE to '‰', NumberFormatField.CURRENCY to '$',
            NumberFormatField.MEASURE_UNIT to 'u', NumberFormatField.COMPACT to 'C')
    }
}
internal expect fun fsbCharAt(values: CharArray, index: Int): Char
internal expect fun fsbFieldAt(values: Array<out Any?>, index: Int): Any?

internal expect fun fsbCodePointAt(values: CharArray, index: Int, limit: Int): Int
internal expect fun fsbCodePointBefore(values: CharArray, index: Int, start: Int): Int
