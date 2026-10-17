// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

private class BidiAttribute(name: String) : FormatAttribute(name)
internal actual val bidiRunDirectionAttribute: FormatAttribute = BidiAttribute("run_direction")
internal actual val bidiEmbeddingAttribute: FormatAttribute = BidiAttribute("bidi_embedding")
internal actual val bidiNumericShapingAttribute: FormatAttribute = BidiAttribute("numeric_shaping")
internal actual fun bidiShapeAttributed(value: Any?, text: CharArray, count: Int) {
    (value as BidiNumericShaper?)?.shape(text, 0, count)
}
internal actual open class BidiOutOfMemoryError actual constructor(message: String) : Error(message)
internal actual open class BidiInternalError actual constructor(message: String) : Error(message)
