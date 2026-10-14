package io.github.heapy.icu4k.text

actual open class FormatField actual protected constructor(private val name: String?) {
    actual protected open fun getName(): String? = name
    actual protected open fun readResolve(): Any = this
    actual final override fun equals(other: Any?): Boolean = this === other
    actual final override fun hashCode(): Int = super.hashCode()
    override fun toString(): String = "${this::class.simpleName}($name)"
}
internal actual fun invalidFormatField(): Nothing = throw IllegalStateException("An invalid object.")
