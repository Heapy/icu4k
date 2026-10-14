// © 2018 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.reflect.KClass

/** Mutable iteration state with class, field-identity or field/value constraints. */
open class ConstrainedFieldPosition {
    private enum class Constraint { NONE, CLASS, FIELD, VALUE }
    private var constraint: Constraint? = null
    private var classConstraint: KClass<*>? = null
    private var field: FormatField? = null
    private var value: Any? = null
    private var start = 0
    private var limit = 0
    private var context = 0L
    init { reset() }
    open fun reset() {
        constraint = Constraint.NONE; classConstraint = Any::class; field = null; value = null
        start = 0; limit = 0; context = 0
    }
    open fun constrainField(field: FormatField?) {
        if (field == null) throw IllegalArgumentException("Cannot constrain on null field")
        constraint = Constraint.FIELD; classConstraint = Any::class; this.field = field; value = null
    }
    open fun constrainClass(classConstraint: KClass<*>?) {
        if (classConstraint == null) throw IllegalArgumentException("Cannot constrain on null field class")
        constraint = Constraint.CLASS; this.classConstraint = classConstraint; field = null; value = null
    }
    @Deprecated("This API is for technology preview and might change or be removed.")
    open fun constrainFieldAndValue(field: FormatField?, fieldValue: Any?) {
        constraint = Constraint.VALUE; classConstraint = Any::class; this.field = field; value = fieldValue
    }
    open fun getField(): FormatField? = field
    open fun getFieldValue(): Any? = value
    open fun getStart(): Int = start
    open fun getLimit(): Int = limit
    open fun getInt64IterationContext(): Long = context
    open fun setInt64IterationContext(context: Long) { this.context = context }
    open fun setState(field: FormatField?, value: Any?, start: Int, limit: Int) {
        // Source constraints are assertion-only here; production mutation is unconditional.
        this.field = field; this.value = value; this.start = start; this.limit = limit
    }
    open fun matchesField(field: FormatField?, fieldValue: Any?): Boolean {
        if (field == null) throw IllegalArgumentException("field must not be null")
        return when (constraint!!) {
            Constraint.NONE -> true
            Constraint.CLASS -> classConstraint!!.isInstance(field)
            Constraint.FIELD -> this.field === field
            Constraint.VALUE -> this.field === field && (value === fieldValue || (value != null && value!!.equals(fieldValue)))
        }
    }
    override fun toString(): String = "CFPos[$start-$limit $field]"
}
