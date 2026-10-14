package io.github.heapy.icu4k.text

actual open class FormatFieldPosition {
    private val attribute: FormatField?
    private val field: Int
    private var begin = 0
    private var end = 0
    actual constructor(field: Int) { this.field = field; attribute = null }
    actual constructor(attribute: FormatField?) : this(attribute, -1)
    actual constructor(attribute: FormatField?, fieldID: Int) { this.attribute = attribute; field = fieldID }
    actual open fun getFieldAttribute(): FormatField? = attribute
    actual open fun getField(): Int = field
    actual open fun getBeginIndex(): Int = begin
    actual open fun getEndIndex(): Int = end
    actual open fun setBeginIndex(index: Int) { begin = index }
    actual open fun setEndIndex(index: Int) { end = index }
    override fun equals(other: Any?): Boolean = other is FormatFieldPosition &&
        attribute === other.attribute && field == other.field && begin == other.begin && end == other.end
    override fun hashCode(): Int = (field shl 24) or (begin shl 16) or end
    override fun toString(): String = "${this::class.simpleName}[field=$field,attribute=$attribute,beginIndex=$begin,endIndex=$end]"
}
