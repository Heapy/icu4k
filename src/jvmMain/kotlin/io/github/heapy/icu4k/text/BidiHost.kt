// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal actual val bidiRunDirectionAttribute: FormatAttribute get() = java.awt.font.TextAttribute.RUN_DIRECTION
internal actual val bidiEmbeddingAttribute: FormatAttribute get() = java.awt.font.TextAttribute.BIDI_EMBEDDING
internal actual val bidiNumericShapingAttribute: FormatAttribute get() = java.awt.font.TextAttribute.NUMERIC_SHAPING
internal actual fun bidiShapeAttributed(value: Any?, text: CharArray, count: Int) {
    if (value is BidiNumericShaper) value.shape(text, 0, count)
    else (value as java.awt.font.NumericShaper?)?.shape(text, 0, count)
}
internal actual typealias BidiOutOfMemoryError = java.lang.OutOfMemoryError
internal actual typealias BidiInternalError = java.lang.InternalError
