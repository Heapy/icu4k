// © 2019 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.*

/** Source list-based field iteration used by date intervals and relative dates. */
internal object FormattedValueFieldPositionIteratorImpl {
    private class FieldWithValue(val field: FormatField?, val value: Int) : FormatField(field!!.toString()) {
        private companion object { private const val serialVersionUID = -3850076447157793465L }
    }

    fun nextPosition(attributes: List<FormatFieldPosition>, position: ConstrainedFieldPosition): Boolean {
        val count = attributes.size
        var i = position.getInt64IterationContext().toInt()
        while (i < count) {
            // Keep Java List.get bounds checking on JS as well, including negative contexts.
            if (i < 0) throw IndexOutOfBoundsException("Index: $i, Size: $count")
            val item = attributes[i]
            var field = item.getFieldAttribute()
            var value: Any? = null
            if (field is FieldWithValue) { value = field.value; field = field.field }
            if (position.matchesField(field, value)) {
                position.setState(field, value, item.getBeginIndex(), item.getEndIndex())
                break
            }
            i++
        }
        position.setInt64IterationContext((if (i == count) i else i + 1).toLong())
        return i < count
    }

    fun toCharacterIterator(text: CharSequence, attributes: List<FormatFieldPosition>): FormatAttributedCharacterIterator =
        attributedCharacterIterator(text.toString(), attributes.map { item ->
            val field = item.getFieldAttribute()
            if (field is FieldWithValue) FormatAttributeRange(field.field!!, field.value, item.getBeginIndex(), item.getEndIndex())
            else FormatAttributeRange(field!!, field, item.getBeginIndex(), item.getEndIndex())
        })

    fun addOverlapSpans(attributes: MutableList<FormatFieldPosition>, spanField: FormatField?, firstIndex: Int) {
        var firstStart = Int.MAX_VALUE
        var firstLimit = 0
        var secondStart = Int.MAX_VALUE
        var secondLimit = 0
        val count = attributes.size
        for (i in 0 until count) {
            val first = attributes[i]
            for (j in i + 1 until count) {
                val second = attributes[j]
                if (first.getFieldAttribute() !== second.getFieldAttribute()) continue
                firstStart = minOf(firstStart, first.getBeginIndex())
                firstLimit = maxOf(firstLimit, first.getEndIndex())
                secondStart = minOf(secondStart, second.getBeginIndex())
                secondLimit = maxOf(secondLimit, second.getEndIndex())
                break
            }
        }
        if (firstStart != Int.MAX_VALUE) {
            attributes.add(FormatFieldPosition(FieldWithValue(spanField, firstIndex)).also {
                it.setBeginIndex(firstStart); it.setEndIndex(firstLimit)
            })
            attributes.add(FormatFieldPosition(FieldWithValue(spanField, 1 - firstIndex)).also {
                it.setBeginIndex(secondStart); it.setEndIndex(secondLimit)
            })
        }
    }

    fun sort(attributes: MutableList<FormatFieldPosition>) {
        val count = attributes.size
        while (true) {
            var sorted = true
            for (i in 0 until count - 1) {
                val first = attributes[i]
                val second = attributes[i + 1]
                val comparison = when {
                    first.getBeginIndex() != second.getBeginIndex() -> second.getBeginIndex() - first.getBeginIndex()
                    first.getEndIndex() != second.getEndIndex() -> first.getEndIndex() - second.getEndIndex()
                    first.getFieldAttribute() !== second.getFieldAttribute() -> when {
                        first.getFieldAttribute() is FieldWithValue && second.getFieldAttribute() !is FieldWithValue -> 1
                        second.getFieldAttribute() is FieldWithValue && first.getFieldAttribute() !is FieldWithValue -> -1
                        else -> first.hashCode() - second.hashCode()
                    }
                    else -> 0
                }
                if (comparison < 0) { sorted = false; attributes[i] = second; attributes[i + 1] = first }
            }
            if (sorted) return
        }
    }
}
