package io.github.heapy.icu4k.text

/** Attribute identity for portable formatted-text iterators. */
expect open class FormatAttribute protected constructor(name: String?) {
    protected open fun getName(): String?
    protected open fun readResolve(): Any
    final override fun equals(other: Any?): Boolean
    final override fun hashCode(): Int
}
