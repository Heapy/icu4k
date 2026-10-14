package io.github.heapy.icu4k.text

actual open class FormatField actual protected constructor(name: String?) : FormatAttribute(name) {
    actual protected override fun getName(): String? = super.getName()
    actual protected override fun readResolve(): Any = this
}
internal actual fun invalidFormatField(): Nothing = throw IllegalStateException("An invalid object.")
