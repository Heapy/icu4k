package io.github.heapy.icu4k.text

/** FieldPosition platform boundary; JVM uses java.text.FieldPosition directly. */
expect open class FormatFieldPosition {
    constructor(field: Int)
    constructor(attribute: FormatField?)
    constructor(attribute: FormatField?, fieldID: Int)
    open fun getFieldAttribute(): FormatField?
    open fun getField(): Int
    open fun getBeginIndex(): Int
    open fun getEndIndex(): Int
    open fun setBeginIndex(index: Int)
    open fun setEndIndex(index: Int)
}
