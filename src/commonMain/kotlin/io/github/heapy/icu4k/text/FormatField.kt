package io.github.heapy.icu4k.text

/** Platform formatting-field identity; JVM uses java.text.Format.Field. */
expect open class FormatField protected constructor(name: String?) {
    protected open fun getName(): String?
    protected open fun readResolve(): Any
    final override fun equals(other: Any?): Boolean
    final override fun hashCode(): Int
}
internal expect fun invalidFormatField(): Nothing
