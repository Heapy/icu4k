package io.github.heapy.icu4k.text

/** Platform formatting-field identity; JVM uses java.text.Format.Field. */
expect open class FormatField protected constructor(name: String?) : FormatAttribute {
    protected override fun getName(): String?
    protected override fun readResolve(): Any
}
internal expect fun invalidFormatField(): Nothing
