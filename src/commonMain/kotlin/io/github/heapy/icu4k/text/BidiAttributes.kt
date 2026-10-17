// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Portable keys; on JVM these are the corresponding java.awt.font.TextAttribute instances. */
object BidiAttributes {
    val RUN_DIRECTION: FormatAttribute get() = bidiRunDirectionAttribute
    val BIDI_EMBEDDING: FormatAttribute get() = bidiEmbeddingAttribute
    val NUMERIC_SHAPING: FormatAttribute get() = bidiNumericShapingAttribute
    const val RUN_DIRECTION_LTR = false
    const val RUN_DIRECTION_RTL = true
}

/** Portable attributed numeric-shaping callback; JVM also accepts java.awt.font.NumericShaper. */
fun interface BidiNumericShaper {
    fun shape(text: CharArray, start: Int, count: Int)
}
internal expect val bidiRunDirectionAttribute: FormatAttribute
internal expect val bidiEmbeddingAttribute: FormatAttribute
internal expect val bidiNumericShapingAttribute: FormatAttribute
internal expect fun bidiShapeAttributed(value: Any?, text: CharArray, count: Int)
internal expect open class BidiOutOfMemoryError : Error { constructor(message: String) }
internal expect open class BidiInternalError : Error { constructor(message: String) }
