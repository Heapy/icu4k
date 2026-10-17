// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Raw paragraph state: construction follows the nonempty setPara preparation. */
internal class BidiPropertyFixture(
    text: String,
    level: Int,
    override val reorderingMode: Int,
    override val reorderingOptions: Int,
    override val orderParagraphsLTR: Boolean,
    override var prologue: String? = null,
    private val classifier: (Int) -> Int = { BidiPropertyCore.customizedClass(it, null) },
) : BidiPropertyCore.Input {
    override val text = text.toCharArray()
    override val originalLength = text.length
    override var length = text.length
    override val dirProps = ByteArray(text.length)
    override var levels = ByteArray(text.length)
    override var paraCount = 1
    override var paraLevel = level.toByte()
    override val defaultParaLevel = (if ((level and 126) == 126) level else 0).toByte()
    override var paraLimits = IntArray(10)
    override var paraLevels = ByteArray(10)
    override var flags = 0
    override var isolateCount = 0
    override var lastArabicPos = 0
    override var controlCount = 0
    override fun getCustomizedClass(c: Int): Int = classifier(c)
    override fun getParaLevelAt(index: Int): Byte = BidiPropertyCore.paraLevelAt(this, index)
}
