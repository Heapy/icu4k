// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Extracted field identities for the upcoming ListFormatter implementation. */
class ListFormatField private constructor(name: String) : FormatField(name) {
    protected override fun readResolve(): Any {
        val name = getName()!!
        if (name == LITERAL!!.getName()) return LITERAL!!
        if (name == ELEMENT!!.getName()) return ELEMENT!!
        return invalidFormatField()
    }
    companion object {
        private const val serialVersionUID = -8071145668708265437L
        var LITERAL: ListFormatField? = ListFormatField("literal")
        var ELEMENT: ListFormatField? = ListFormatField("element")
    }
}
class ListSpanField private constructor(name: String) : FormatSpanField(name) {
    protected override fun readResolve(): Any = if (getName()!!.equals(LIST_SPAN.getName())) LIST_SPAN else invalidFormatField()
    companion object {
        private const val serialVersionUID = 3563544214705634403L
        val LIST_SPAN = ListSpanField("list-span")
    }
}
