package io.github.heapy.icu4k.text

/** Common replacement for java.text.ParsePosition used by ICU parsers. */
class ParsePosition(var index: Int) {
    var errorIndex: Int = -1
    override fun equals(other: Any?): Boolean = other is ParsePosition && index == other.index && errorIndex == other.errorIndex
    override fun hashCode(): Int = (errorIndex shl 16) or index
    override fun toString(): String = "ParsePosition[index=$index,errorIndex=$errorIndex]"
}
