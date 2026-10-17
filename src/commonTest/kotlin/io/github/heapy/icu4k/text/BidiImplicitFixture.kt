// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal class BidiImplicitFixture(
    val base: BidiPropertyFixture,
    override var epilogue: String? = null,
) : BidiImplicitCore.Input, BidiPropertyCore.Input by base {
    override var impTabPair: BidiImplicitTables.Pair? = selectPair(base.reorderingMode, base.reorderingOptions)
    override val insertPoints = BidiImplicitCore.InsertPoints()
    override var isolates: Array<BidiImplicitCore.Isolate?>? = null
    override var trailingWSStart = base.length
    companion object {
        fun selectPair(mode: Int, options: Int): BidiImplicitTables.Pair? = when (mode) {
            0 -> BidiImplicitTables.impTab_DEFAULT
            1 -> BidiImplicitTables.impTab_NUMBERS_SPECIAL
            2 -> BidiImplicitTables.impTab_GROUP_NUMBERS_WITH_R
            4 -> BidiImplicitTables.impTab_INVERSE_NUMBERS_AS_L
            5 -> if ((options and 1) != 0) BidiImplicitTables.impTab_INVERSE_LIKE_DIRECT_WITH_MARKS else BidiImplicitTables.impTab_INVERSE_LIKE_DIRECT
            6 -> if ((options and 1) != 0) BidiImplicitTables.impTab_INVERSE_FOR_NUMBERS_SPECIAL_WITH_MARKS else BidiImplicitTables.impTab_INVERSE_FOR_NUMBERS_SPECIAL
            else -> null // Runs-only uses the forward/inverse engines through its wrapper.
        }
    }
}

/** Fresh nonempty setPara state through the kernel phases; facade lifecycle is separate. */
internal fun BidiImplicitFixture.resolveFresh(embeddings: ByteArray?) {
    base.levels = ByteArray(0)
    trailingWSStart = 0
    BidiPropertyCore(base).scan()
    trailingWSStart = base.length
    val direction = if (embeddings == null) {
        base.levels = ByteArray(base.length)
        BidiExplicitCore(base).resolve()
    } else {
        base.levels = embeddings
        BidiExplicitCore(base).check()
    }
    val core = BidiImplicitCore(this)
    core.prepareIsolates()
    if (direction.toInt() == 2) core.resolveRuns(embeddings != null) else trailingWSStart = 0
    core.addDefaultInverseMarks()
}
