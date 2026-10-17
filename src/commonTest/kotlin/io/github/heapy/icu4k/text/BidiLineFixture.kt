// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

internal class BidiLineFixture : BidiLineCore.Input {
    override var text = CharArray(0)
    override var originalLength = 0
    override var length = 0
    override var dirProps = ByteArray(0)
    override var levels = ByteArray(0)
    override var reorderingMode = 0
    override var reorderingOptions = 0
    override var trailingWSStart = 0
    override var direction: Byte = 0
    override var resultLength = 0
    override var runCount = 0
    override var runs: Array<BidiRun?>? = null
    override var simpleRuns = arrayOf<BidiRun?>(BidiRun())
    override var runsMemory = arrayOfNulls<BidiRun>(0)
    var dirPropsMemory = ByteArray(1)
    var levelsMemory = ByteArray(1)
    override var paraCount = 0
    override var paraLimits = IntArray(10)
    override var paraLevels = ByteArray(10)
    override var paraLevel: Byte = 0
    override var defaultParaLevel: Byte = 0
    override var orderParagraphsLTR = false
    override var flags = 0
    override var isolateCount = 0
    override var lastArabicPos = 0
    override var controlCount = 0
    override var prologue: String? = null
    override var epilogue: String? = null
    override var impTabPair: BidiImplicitTables.Pair? = null
    override val insertPoints = BidiImplicitCore.InsertPoints()
    override var isolates: Array<BidiImplicitCore.Isolate?>? = null
    var parent: BidiLineCore.Input? = null
    var inverse = false
    var allocateRuns: ((Int, Array<BidiRun?>) -> Array<BidiRun?>)? = null
    override fun getRunsMemory(length: Int) {
        runsMemory = allocateRuns?.invoke(length, runsMemory) ?: if (runsMemory.size == length) runsMemory else arrayOfNulls(length)
    }
    override fun allocateDirProps(length: Int): ByteArray {
        if (dirPropsMemory.size != length) dirPropsMemory = ByteArray(length)
        return dirPropsMemory
    }
    override fun allocateLevels(length: Int): ByteArray {
        if (levelsMemory.size != length) levelsMemory = ByteArray(length)
        return levelsMemory
    }
    override fun linkParent(parent: BidiLineCore.Input) { this.parent = parent }
    override fun getCustomizedClass(c: Int) = BidiPropertyCore.customizedClass(c, null)
    override fun getParaLevelAt(index: Int) = BidiPropertyCore.paraLevelAt(this, index)
    fun writerInput(): BidiWriterInput {
        val state = this
        val core = BidiLineCore(this)
        return object : BidiWriterInput {
            override val text get() = state.text
            override val length get() = state.length
            override val reorderingMode get() = state.reorderingMode
            override val reorderingOptions get() = state.reorderingOptions
            override val dirProps get() = state.dirProps
            override fun countRuns(): Int { core.getRuns(); return state.runCount }
            override fun getVisualRun(index: Int) = core.getVisualRun(index)
            override fun getInsertRemove(index: Int) = bidiArrayAt(state.runs, index)!!.insertRemove
            override fun isInverse() = state.inverse
            override fun testDirPropFlagAt(flag: Int, index: Int) = ((1 shl bidiByteAt(state.dirProps, index).toInt()) and flag) != 0
        }
    }
}

internal fun lineFixtureFromResolved(state: BidiImplicitFixture): BidiLineFixture {
    val b = state.base
    return BidiLineFixture().apply {
        text = b.text; originalLength = b.originalLength; length = b.length
        dirProps = b.dirProps; levels = b.levels
        dirPropsMemory = dirProps; levelsMemory = levels
        reorderingMode = b.reorderingMode; reorderingOptions = b.reorderingOptions
        trailingWSStart = state.trailingWSStart
        direction = BidiExplicitCore(b).directionFromFlags()
        resultLength = b.originalLength + if ((b.reorderingOptions and 2) != 0) -b.controlCount else state.insertPoints.size
        runCount = -1; runs = arrayOfNulls(0)
        paraCount = b.paraCount; paraLimits = b.paraLimits; paraLevels = b.paraLevels
        paraLevel = b.paraLevel; defaultParaLevel = b.defaultParaLevel
        orderParagraphsLTR = b.orderParagraphsLTR; flags = b.flags
        isolateCount = b.isolateCount; lastArabicPos = b.lastArabicPos; controlCount = b.controlCount
        inverse = b.reorderingMode == 4
        insertPoints.size = state.insertPoints.size; insertPoints.confirmed = state.insertPoints.confirmed
        insertPoints.points = state.insertPoints.points
        parent = this
    }
}
