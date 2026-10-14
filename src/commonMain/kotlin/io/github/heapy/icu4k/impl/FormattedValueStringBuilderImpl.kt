// © 2019 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*

/** Common field/span iteration over the attributed UTF-16 buffer. */
object FormattedValueStringBuilderImpl {
    class SpanFieldPlaceholder : FormattedStringBuilder.FieldWrapper {
        var spanField: FormatSpanField? = null
        var normalField: FormatField? = null
        var value: Any? = null
        var start = 0
        var length = 0
        override fun unwrap(): FormatField? = normalField
    }
    fun findSpan(self: FormattedStringBuilder, value: Any?): Int {
        for (i in self.zero until self.zero + self.length) {
            val placeholder = fsbFieldAt(self.fields, i) as? SpanFieldPlaceholder ?: continue
            if (placeholder.value!!.equals(value)) return i - self.zero
        }
        return -1
    }
    fun applySpanRange(self: FormattedStringBuilder, spanField: FormatSpanField?, value: Any?, start: Int, end: Int) {
        for (i in start + self.zero until end + self.zero) {
            val old = fsbFieldAt(self.fields, i)
            val placeholder = SpanFieldPlaceholder()
            placeholder.spanField = spanField
            placeholder.normalField = old as FormatField?
            placeholder.value = value
            placeholder.start = start
            placeholder.length = end - start
            self.fields[i] = placeholder
        }
    }
    fun nextFieldPosition(self: FormattedStringBuilder, position: FormatFieldPosition): Boolean {
        var field = position.getFieldAttribute()
        if (field == null) field = when (position.getField()) {
            0 -> NumberFormatField.INTEGER
            1 -> NumberFormatField.FRACTION
            else -> return false
        }
        if (field !is NumberFormatField) throw IllegalArgumentException(
            "You must pass an instance of io.github.heapy.icu4k.text.NumberFormatField as your FieldPosition attribute.  You passed: ${formatFieldClassDescription(field)}")
        val constrained = ConstrainedFieldPosition()
        constrained.constrainField(field)
        constrained.setState(field, null, position.getBeginIndex(), position.getEndIndex())
        if (nextPosition(self, constrained, null)) {
            position.setBeginIndex(constrained.getStart()); position.setEndIndex(constrained.getLimit())
            return true
        }
        if (field === NumberFormatField.FRACTION && position.getEndIndex() == 0) {
            var inside = false
            var i = self.zero
            while (i < self.zero + self.length) {
                val current = fsbFieldAt(self.fields, i)
                if (isIntOrGroup(current) || current === NumberFormatField.DECIMAL_SEPARATOR) inside = true else if (inside) break
                i++
            }
            position.setBeginIndex(i - self.zero); position.setEndIndex(i - self.zero)
        }
        return false
    }
    private class NullField private constructor(name: String) : FormatField(name) {
        companion object { private const val serialVersionUID = 1L; val END = NullField("end") }
    }
    fun nextPosition(self: FormattedStringBuilder, position: ConstrainedFieldPosition, numericField: FormatField?): Boolean {
        var fieldStart = -1
        var current: Any? = null
        var previousSpan = position.getLimit() > 0 && position.getField() is FormatSpanField && position.getStart() < position.getLimit()
        var previousNumeric = numericField != null && position.getField() === numericField
        var previousInteger = position.getField() === NumberFormatField.INTEGER
        var i = self.zero + position.getLimit()
        while (i <= self.zero + self.length) {
            var field: Any? = if (i < self.zero + self.length) fsbFieldAt(self.fields, i) else NullField.END
            if (current != null) {
                if (current !== field) {
                    var end = i - self.zero
                    if (isTrimmable(current)) end = trimBack(self, end)
                    if (end <= fieldStart) { fieldStart = -1; current = null; continue }
                    var start = fieldStart
                    if (isTrimmable(current)) start = trimFront(self, start)
                    position.setState(current as FormatField, null, start, end)
                    return true
                }
                i++
                continue
            }
            if (i > self.zero && previousSpan) {
                val placeholder = fsbFieldAt(self.fields, i - 1) as SpanFieldPlaceholder
                if (placeholder.normalField === ListFormatField.ELEMENT) {
                    if (position.matchesField(ListFormatField.ELEMENT, null)) {
                        fieldStart = i - self.zero - placeholder.length
                        position.setState(ListFormatField.ELEMENT, null, fieldStart, fieldStart + placeholder.length)
                        return true
                    }
                } else {
                    i -= placeholder.length
                    field = (fsbFieldAt(self.fields, i) as SpanFieldPlaceholder).normalField
                }
            }
            if (position.matchesField(NumberFormatField.INTEGER, null) && i > self.zero && !previousInteger && !previousNumeric &&
                isIntOrGroup(fsbFieldAt(self.fields, i - 1)) && !isIntOrGroup(field)) {
                var j = i - 1
                while (j >= self.zero && isIntOrGroup(fsbFieldAt(self.fields, j))) j--
                position.setState(NumberFormatField.INTEGER, null, j - self.zero + 1, i - self.zero)
                return true
            }
            if (numericField != null && position.matchesField(numericField, null) && i > self.zero && !previousNumeric &&
                isNumeric(fsbFieldAt(self.fields, i - 1)) && !isNumeric(field)) {
                var j = i - 1
                while (j >= self.zero && isNumeric(fsbFieldAt(self.fields, j))) j--
                position.setState(numericField, null, j - self.zero + 1, i - self.zero)
                return true
            }
            val placeholder = field as? SpanFieldPlaceholder
            if (placeholder != null) field = placeholder.normalField
            if (placeholder != null && (placeholder.start == -1 || placeholder.start == i - self.zero)) {
                if (position.matchesField(placeholder.spanField, placeholder.value)) {
                    fieldStart = i - self.zero
                    position.setState(placeholder.spanField, placeholder.value, fieldStart, fieldStart + placeholder.length)
                    return true
                } else if (placeholder.normalField === ListFormatField.ELEMENT) {
                    if (position.matchesField(ListFormatField.ELEMENT, null)) {
                        fieldStart = i - self.zero
                        position.setState(ListFormatField.ELEMENT, null, fieldStart, fieldStart + placeholder.length)
                        return true
                    } else i += placeholder.length - 1
                }
            } else if (field === NumberFormatField.INTEGER) field = null
            else if (field == null || field === NullField.END) { /* no field */ }
            else if (position.matchesField(field as FormatField, null)) { fieldStart = i - self.zero; current = field }
            previousSpan = false; previousNumeric = false; previousInteger = false
            i++
        }
        position.setState(position.getField(), position.getFieldValue(), self.length, self.length)
        return false
    }
    private fun isIntOrGroup(field: Any?): Boolean {
        val normal = FormattedStringBuilder.unwrapField(field)
        return normal === NumberFormatField.INTEGER || normal === NumberFormatField.GROUPING_SEPARATOR
    }
    private fun isNumeric(field: Any?): Boolean = FormattedStringBuilder.unwrapField(field).let { it == null || it is NumberFormatField }
    private fun isTrimmable(field: Any): Boolean = field !== NumberFormatField.GROUPING_SEPARATOR && field !is ListFormatField
    private fun trimBack(self: FormattedStringBuilder, limit: Int): Int = StaticUnicodeSets.get(StaticUnicodeSets.Key.DEFAULT_IGNORABLES).spanBack(self, limit, UnicodeSet.SpanCondition.CONTAINED)
    private fun trimFront(self: FormattedStringBuilder, start: Int): Int = StaticUnicodeSets.get(StaticUnicodeSets.Key.DEFAULT_IGNORABLES).span(self, start, UnicodeSet.SpanCondition.CONTAINED)
}
internal expect fun formatFieldClassDescription(field: FormatField): String
