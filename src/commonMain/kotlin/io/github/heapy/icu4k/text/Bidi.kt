// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.UBiDiProps

/** ICU Unicode bidirectional paragraphs, lines and index mappings. Mutable, like ICU4J. */
open class Bidi(maxLength: Int, maxRunCount: Int) {
    @JvmField internal var paraBidi: Bidi? = null
    @JvmField internal val bdp: UBiDiProps
    @JvmField internal var text: CharArray? = null
    @JvmField internal var originalLength = 0
    @JvmField internal var length = 0
    @JvmField internal var resultLength = 0
    @JvmField internal var mayAllocateText = false
    @JvmField internal var mayAllocateRuns = false
    @JvmField internal var dirPropsMemory = ByteArray(1)
    @JvmField internal var levelsMemory = ByteArray(1)
    @JvmField internal var dirProps: ByteArray? = null
    @JvmField internal var levels: ByteArray? = null
    @JvmField internal var inverse = false
    @JvmField internal var reorderingMode = 0
    @JvmField internal var reorderingOptions = 0
    @JvmField internal var paragraphsLTR = false
    @JvmField internal var paraLevel: Byte = 0
    @JvmField internal var defaultParaLevel: Byte = 0
    @JvmField internal var prologue: String? = null
    @JvmField internal var epilogue: String? = null
    @JvmField internal var impTabPair: BidiImplicitTables.Pair? = null
    @JvmField internal var direction: Byte = 0
    @JvmField internal var flags = 0
    @JvmField internal var lastArabicPos = 0
    @JvmField internal var trailingWSStart = 0
    @JvmField internal var paraCount = 0
    @JvmField internal var paras_limit = IntArray(10)
    @JvmField internal var paras_level = ByteArray(10)
    @JvmField internal var runCount = 0
    @JvmField internal var runsMemory = arrayOfNulls<BidiRun>(0)
    @JvmField internal var runs: Array<BidiRun?>? = null
    @JvmField internal var simpleRuns = arrayOf<BidiRun?>(BidiRun())
    @JvmField internal var isolates: Array<BidiImplicitCore.Isolate?>? = null
    @JvmField internal var isolateCount = 0
    @JvmField internal var logicalToVisualRunsMap: IntArray? = null
    @JvmField internal var isGoodLogicalToVisualRunsMap = false
    @JvmField internal var customClassifier: BidiClassifier? = null
    @JvmField internal val insertPoints = BidiImplicitCore.InsertPoints()
    @JvmField internal var controlCount = 0
    private val state = State()
    private val lineCore = BidiLineCore(state)
    private val implicitCore = BidiImplicitCore(state)
    private val writer = object : BidiWriterInput {
        override val text get() = this@Bidi.text!!
        override val length get() = this@Bidi.length
        override val reorderingMode get() = this@Bidi.reorderingMode
        override val reorderingOptions get() = this@Bidi.reorderingOptions
        override val dirProps get() = this@Bidi.dirProps
        override fun countRuns() = this@Bidi.countRuns()
        override fun getVisualRun(index: Int) = this@Bidi.getVisualRun(index)
        override fun getInsertRemove(index: Int) = rawRun(index).insertRemove
        override fun isInverse() = this@Bidi.isInverse()
        override fun testDirPropFlagAt(flag: Int, index: Int) = ((1 shl bidiByteAt(this@Bidi.dirProps, index).toInt()) and flag) != 0
    }
    init {
        if (maxLength < 0 || maxRunCount < 0) throw IllegalArgumentException()
        bdp = UBiDiProps.INSTANCE
        if (maxLength > 0) {
            dirPropsMemory = memory("DirProps", dirPropsMemory, true, maxLength)
            levelsMemory = memory("Levels", levelsMemory, true, maxLength)
        } else mayAllocateText = true
        if (maxRunCount > 0) {
            if (maxRunCount > 1) runsMemory = runMemory(true, maxRunCount)
        } else mayAllocateRuns = true
    }
    constructor() : this(0, 0)
    constructor(paragraph: String?, flags: Int) : this(paragraph!!.toCharArray(), 0, null, 0, paragraph.length, flags)
    constructor(paragraph: FormatAttributedCharacterIterator?) : this() { setPara(paragraph) }
    constructor(text: CharArray?, textStart: Int, embeddings: ByteArray?, embStart: Int, paragraphLength: Int, flags: Int) : this() {
        val para = when (flags) { DIRECTION_RIGHT_TO_LEFT -> RTL; DIRECTION_DEFAULT_LEFT_TO_RIGHT -> LEVEL_DEFAULT_LTR; DIRECTION_DEFAULT_RIGHT_TO_LEFT -> LEVEL_DEFAULT_RTL; else -> LTR }
        val values = if (embeddings == null) null else ByteArray(paragraphLength).also { out ->
            for (i in 0 until paragraphLength) {
                var level = bidiByteAt(embeddings, i + embStart)
                if (level < 0) level = ((-level) or LEVEL_OVERRIDE.toInt()).toByte()
                out[i] = level
            }
        }
        if (textStart == 0 && paragraphLength == text!!.size) setPara(text, para, values)
        else {
            val copy = CharArray(paragraphLength)
            text!!.copyInto(copy, 0, textStart, textStart + paragraphLength)
            setPara(copy, para, values)
        }
    }
    private fun memory(label: String, old: ByteArray, allocate: Boolean, needed: Int): ByteArray {
        if (needed == old.size) return old
        if (!allocate) {
            if (needed <= old.size) return old
            throw BidiOutOfMemoryError("Failed to allocate memory for $label")
        }
        return try { ByteArray(needed) } catch (e: Exception) { throw BidiOutOfMemoryError("Failed to allocate memory for $label") }
    }
    private fun runMemory(allocate: Boolean, needed: Int): Array<BidiRun?> {
        if (needed == runsMemory.size) return runsMemory
        if (!allocate) {
            if (needed <= runsMemory.size) return runsMemory
            throw BidiOutOfMemoryError("Failed to allocate memory for Runs")
        }
        return try { arrayOfNulls(needed) } catch (e: Exception) { throw BidiOutOfMemoryError("Failed to allocate memory for Runs") }
    }
    private fun rawRun(index: Int) = bidiArrayAt(runs, index)!!
    private fun verifyValidPara() { if (this !== paraBidi) throw IllegalStateException() }
    private fun verifyValidParaOrLine() {
        val parent = paraBidi
        if (this === parent) return
        if (parent == null || parent !== parent.paraBidi) throw IllegalStateException()
    }
    private fun verifyRange(index: Int, start: Int, limit: Int) {
        if (index < start || index >= limit) throw IllegalArgumentException("Value $index is out of range $start to $limit")
    }
    internal fun getParaLevelAt(index: Int) = BidiPropertyCore.paraLevelAt(state, index)
    open fun setInverse(value: Boolean) { inverse = value; reorderingMode = if (value) 4 else 0 }
    open fun isInverse(): Boolean = inverse
    open fun setReorderingMode(mode: Int) { if (mode !in 0..6) return; reorderingMode = mode; inverse = mode == 4 }
    open fun getReorderingMode(): Int = reorderingMode
    open fun setReorderingOptions(options: Int) { reorderingOptions = BidiReorderingCore.normalizeReorderingOptions(options) }
    open fun getReorderingOptions(): Int = reorderingOptions
    open fun setContext(prologue: String?, epilogue: String?) {
        this.prologue = prologue?.takeIf { it.isNotEmpty() }; this.epilogue = epilogue?.takeIf { it.isNotEmpty() }
    }
    private fun setParaSuccess() { prologue = null; epilogue = null; paraBidi = this }
    open fun setPara(text: String?, paraLevel: Byte, embeddingLevels: ByteArray?) {
        setPara(text?.toCharArray() ?: CharArray(0), paraLevel, embeddingLevels)
    }
    open fun setPara(chars: CharArray?, paraLevel: Byte, embeddingLevels: ByteArray?) {
        if (paraLevel < LEVEL_DEFAULT_LTR) verifyRange(paraLevel.toInt(), 0, MAX_EXPLICIT_LEVEL + 1)
        val input = chars ?: CharArray(0)
        if (reorderingMode == 3) { setParaRunsOnly(input, paraLevel); return }
        paraBidi = null; text = input
        length = input.size; originalLength = length; resultLength = length
        this.paraLevel = paraLevel; direction = (paraLevel.toInt() and 1).toByte(); paraCount = 1
        dirProps = ByteArray(0); levels = ByteArray(0); runs = arrayOfNulls(0)
        isGoodLogicalToVisualRunsMap = false; insertPoints.size = 0; insertPoints.confirmed = 0
        defaultParaLevel = if (isDefault(paraLevel)) paraLevel else 0
        if (length == 0) {
            if (isDefault(paraLevel)) { this.paraLevel = (this.paraLevel.toInt() and 1).toByte(); defaultParaLevel = 0 }
            flags = 1 shl (paraLevel.toInt() and 1); runCount = 0; paraCount = 0
            setParaSuccess(); return
        }
        runCount = -1
        dirPropsMemory = memory("DirProps", dirPropsMemory, mayAllocateText, length); dirProps = dirPropsMemory
        BidiPropertyCore(state).scan()
        trailingWSStart = length
        if (embeddingLevels == null) {
            levelsMemory = memory("Levels", levelsMemory, mayAllocateText, length); levels = levelsMemory
            direction = BidiExplicitCore(state).resolve()
        } else { levels = embeddingLevels; direction = BidiExplicitCore(state).check() }
        implicitCore.prepareIsolates()
        when (direction.toInt()) {
            0, 1 -> trailingWSStart = 0
            else -> {
                when (reorderingMode) {
                    0 -> impTabPair = BidiImplicitTables.impTab_DEFAULT
                    1 -> impTabPair = BidiImplicitTables.impTab_NUMBERS_SPECIAL
                    2 -> impTabPair = BidiImplicitTables.impTab_GROUP_NUMBERS_WITH_R
                    3 -> throw BidiInternalError("Internal ICU error in setPara")
                    4 -> impTabPair = BidiImplicitTables.impTab_INVERSE_NUMBERS_AS_L
                    5 -> impTabPair = if ((reorderingOptions and 1) != 0) BidiImplicitTables.impTab_INVERSE_LIKE_DIRECT_WITH_MARKS else BidiImplicitTables.impTab_INVERSE_LIKE_DIRECT
                    6 -> impTabPair = if ((reorderingOptions and 1) != 0) BidiImplicitTables.impTab_INVERSE_FOR_NUMBERS_SPECIAL_WITH_MARKS else BidiImplicitTables.impTab_INVERSE_FOR_NUMBERS_SPECIAL
                }
                implicitCore.resolveRuns(embeddingLevels != null)
            }
        }
        implicitCore.addDefaultInverseMarks()
        if ((reorderingOptions and 2) != 0) resultLength -= controlCount else resultLength += insertPoints.size
        setParaSuccess()
    }
    private inner class State : BidiLineCore.Input {
        val owner get() = this@Bidi
        override var text: CharArray
            get() = this@Bidi.text!!
            set(value) { this@Bidi.text = value }
        override var originalLength: Int
            get() = this@Bidi.originalLength
            set(value) { this@Bidi.originalLength = value }
        override var length: Int
            get() = this@Bidi.length
            set(value) { this@Bidi.length = value }
        override var dirProps: ByteArray
            get() = this@Bidi.dirProps!!
            set(value) { this@Bidi.dirProps = value }
        override var levels: ByteArray
            get() = this@Bidi.levels!!
            set(value) { this@Bidi.levels = value }
        override var reorderingMode: Int
            get() = this@Bidi.reorderingMode
            set(value) { this@Bidi.reorderingMode = value }
        override var reorderingOptions: Int
            get() = this@Bidi.reorderingOptions
            set(value) { this@Bidi.reorderingOptions = value }
        override var trailingWSStart: Int
            get() = this@Bidi.trailingWSStart
            set(value) { this@Bidi.trailingWSStart = value }
        override var direction: Byte
            get() = this@Bidi.direction
            set(value) { this@Bidi.direction = value }
        override var resultLength: Int
            get() = this@Bidi.resultLength
            set(value) { this@Bidi.resultLength = value }
        override var runCount: Int
            get() = this@Bidi.runCount
            set(value) { this@Bidi.runCount = value }
        override var runs: Array<BidiRun?>?
            get() = this@Bidi.runs
            set(value) { this@Bidi.runs = value }
        override var paraCount: Int
            get() = this@Bidi.paraCount
            set(value) { this@Bidi.paraCount = value }
        override var paraLimits: IntArray
            get() = this@Bidi.paras_limit
            set(value) { this@Bidi.paras_limit = value }
        override var paraLevels: ByteArray
            get() = this@Bidi.paras_level
            set(value) { this@Bidi.paras_level = value }
        override var paraLevel: Byte
            get() = this@Bidi.paraLevel
            set(value) { this@Bidi.paraLevel = value }
        override var flags: Int
            get() = this@Bidi.flags
            set(value) { this@Bidi.flags = value }
        override var isolateCount: Int
            get() = this@Bidi.isolateCount
            set(value) { this@Bidi.isolateCount = value }
        override var lastArabicPos: Int
            get() = this@Bidi.lastArabicPos
            set(value) { this@Bidi.lastArabicPos = value }
        override var controlCount: Int
            get() = this@Bidi.controlCount
            set(value) { this@Bidi.controlCount = value }
        override var isolates: Array<BidiImplicitCore.Isolate?>?
            get() = this@Bidi.isolates
            set(value) { this@Bidi.isolates = value }
        override val defaultParaLevel: Byte get() = this@Bidi.defaultParaLevel
        override val orderParagraphsLTR: Boolean get() = this@Bidi.paragraphsLTR
        override val prologue: String? get() = this@Bidi.prologue
        override val epilogue: String? get() = this@Bidi.epilogue
        override val impTabPair: BidiImplicitTables.Pair? get() = this@Bidi.impTabPair
        override val insertPoints: BidiImplicitCore.InsertPoints get() = this@Bidi.insertPoints
        override val simpleRuns: Array<BidiRun?> get() = this@Bidi.simpleRuns
        override val runsMemory: Array<BidiRun?> get() = this@Bidi.runsMemory
        override fun getCustomizedClass(c: Int) = this@Bidi.getCustomizedClass(c)
        override fun getParaLevelAt(index: Int) = this@Bidi.getParaLevelAt(index)
        override fun getRunsMemory(length: Int) { this@Bidi.runsMemory = runMemory(mayAllocateRuns, length) }
        override fun allocateDirProps(length: Int): ByteArray {
            this@Bidi.dirPropsMemory = memory("DirProps", this@Bidi.dirPropsMemory, mayAllocateText, length)
            return this@Bidi.dirPropsMemory
        }
        override fun allocateLevels(length: Int): ByteArray {
            this@Bidi.levelsMemory = memory("Levels", this@Bidi.levelsMemory, mayAllocateText, length)
            return this@Bidi.levelsMemory
        }
        override fun linkParent(parent: BidiLineCore.Input) { paraBidi = (parent as State).owner }
    }

    private fun setParaRunsOnly(input: CharArray, initialParaLevel: Byte) {
        reorderingMode = 0
        val inputLength = input.size
        if (inputLength == 0) { setPara(input, initialParaLevel, null); reorderingMode = 3; return }
        val savedOptions = reorderingOptions
        if ((savedOptions and 1) > 0) reorderingOptions = (reorderingOptions and 1.inv()) or 2
        var para = (initialParaLevel.toInt() and 1).toByte()
        setPara(input, para, null)
        val savedLevels = ByteArray(length)
        getLevels().copyInto(savedLevels, 0, 0, length)
        val savedTrailing = trailingWSStart
        val visualText = writeReordered(DO_MIRRORING.toInt())
        val visualMap = getVisualMap()
        reorderingOptions = savedOptions
        val savedLength = length
        val savedDirection = direction
        reorderingMode = 5
        para = (para.toInt() xor 1).toByte()
        setPara(visualText, para, null)
        lineCore.getRuns()
        var added = 0
        val oldCount = runCount
        var visualStart = 0
        for (i in 0 until oldCount) {
            val runLength = rawRun(i).limit - visualStart
            if (runLength >= 2) {
                val logical = rawRun(i).start
                for (j in logical + 1 until logical + runLength) {
                    val index = bidiIntAt(visualMap, j); val previous = bidiIntAt(visualMap, j - 1)
                    if (absolute(index - previous) != 1 || bidiByteAt(savedLevels, index) != bidiByteAt(savedLevels, previous)) ++added
                }
            }
            visualStart += runLength
        }
        if (added > 0) {
            state.getRunsMemory(oldCount + added)
            if (runCount == 1) { bidiArrayAt(runsMemory, 0); runsMemory[0] = rawRun(0) }
            else runs!!.copyInto(runsMemory, 0, 0, runCount)
            runs = runsMemory; runCount += added
            for (i in oldCount until runCount) if (bidiArrayAt(runs, i) == null) runs!![i] = BidiRun(0, 0, 0)
        }
        for (i in oldCount - 1 downTo 0) {
            var target = i + added
            val runLength = if (i == 0) rawRun(0).limit else rawRun(i).limit - rawRun(i - 1).limit
            val logical = rawRun(i).start
            val odd = rawRun(i).level.toInt() and 1
            if (runLength < 2) {
                if (added > 0) rawRun(target).copyFrom(rawRun(i))
                val position = bidiIntAt(visualMap, logical)
                rawRun(target).start = position
                rawRun(target).level = (bidiByteAt(savedLevels, position).toInt() xor odd).toByte()
                continue
            }
            var start: Int
            val limit: Int
            val step: Int
            if (odd > 0) { start = logical; limit = logical + runLength - 1; step = 1 }
            else { start = logical + runLength - 1; limit = logical; step = -1 }
            var j = start
            while (j != limit) {
                val index = bidiIntAt(visualMap, j); val next = bidiIntAt(visualMap, j + step)
                if (absolute(index - next) != 1 || bidiByteAt(savedLevels, index) != bidiByteAt(savedLevels, next)) {
                    val position = minOf(bidiIntAt(visualMap, start), index)
                    rawRun(target).start = position
                    rawRun(target).level = (bidiByteAt(savedLevels, position).toInt() xor odd).toByte()
                    rawRun(target).limit = rawRun(i).limit
                    rawRun(i).limit -= absolute(j - start) + 1
                    val insertRemove = rawRun(i).insertRemove and 10
                    rawRun(target).insertRemove = insertRemove
                    rawRun(i).insertRemove = rawRun(i).insertRemove and insertRemove.inv()
                    start = j + step; --added; --target
                }
                j += step
            }
            if (added > 0) rawRun(target).copyFrom(rawRun(i))
            val position = minOf(bidiIntAt(visualMap, start), bidiIntAt(visualMap, limit))
            rawRun(target).start = position
            rawRun(target).level = (bidiByteAt(savedLevels, position).toInt() xor odd).toByte()
        }
        paraLevel = (paraLevel.toInt() xor 1).toByte()
        text = input; length = savedLength; originalLength = inputLength
        direction = savedDirection; levels = savedLevels; trailingWSStart = savedTrailing
        if (runCount > 1) direction = MIXED
        reorderingMode = 3
    }
    private fun absolute(value: Int) = if (value < 0) -value else value

    open fun setPara(paragraph: FormatAttributedCharacterIterator?) {
        val iterator = paragraph!!
        val runDirection = iterator.getAttribute(BidiAttributes.RUN_DIRECTION) as Boolean?
        val para = if (runDirection == null) LEVEL_DEFAULT_LTR else if (!runDirection) LTR else RTL
        var supplied: ByteArray? = null
        val len = iterator.getEndIndex() - iterator.getBeginIndex()
        val embeddings = ByteArray(len)
        val chars = CharArray(len)
        var i = 0
        var c = iterator.first()
        while (c != FORMAT_CHARACTER_DONE) {
            bidiCharAt(chars, i); chars[i] = c
            val value = iterator.getAttribute(BidiAttributes.BIDI_EMBEDDING) as Int?
            if (value != null) {
                val level = value.toByte()
                if (level.toInt() != 0) {
                    supplied = embeddings
                    embeddings[i] = if (level < 0) ((-level) or LEVEL_OVERRIDE.toInt()).toByte() else level
                }
            }
            c = iterator.next(); ++i
        }
        bidiShapeAttributed(iterator.getAttribute(BidiAttributes.NUMERIC_SHAPING), chars, len)
        setPara(chars, para, supplied)
    }
    open fun orderParagraphsLTR(value: Boolean) { paragraphsLTR = value }
    open fun isOrderParagraphsLTR(): Boolean = paragraphsLTR
    open fun getDirection(): Byte { verifyValidParaOrLine(); return direction }
    open fun getTextAsString(): String { verifyValidParaOrLine(); return text!!.concatToString() }
    open fun getText(): CharArray { verifyValidParaOrLine(); return text!! }
    open fun getLength(): Int { verifyValidParaOrLine(); return originalLength }
    open fun getProcessedLength(): Int { verifyValidParaOrLine(); return length }
    open fun getResultLength(): Int { verifyValidParaOrLine(); return resultLength }
    open fun getParaLevel(): Byte { verifyValidParaOrLine(); return paraLevel }
    open fun countParagraphs(): Int { verifyValidParaOrLine(); return paraCount }
    open fun getParagraphByIndex(index: Int): BidiRun {
        verifyValidParaOrLine(); verifyRange(index, 0, paraCount)
        val parent = paraBidi!!
        val start = if (index == 0) 0 else bidiIntAt(parent.paras_limit, index - 1)
        return BidiRun().also { it.start = start; it.limit = bidiIntAt(parent.paras_limit, index); it.level = getParaLevelAt(start) }
    }
    open fun getParagraph(index: Int): BidiRun {
        verifyValidParaOrLine(); val parent = paraBidi!!; verifyRange(index, 0, parent.length)
        var paragraph = 0
        while (index >= bidiIntAt(parent.paras_limit, paragraph)) ++paragraph
        return getParagraphByIndex(paragraph)
    }
    open fun getParagraphIndex(index: Int): Int {
        verifyValidParaOrLine(); val parent = paraBidi!!; verifyRange(index, 0, parent.length)
        var paragraph = 0
        while (index >= bidiIntAt(parent.paras_limit, paragraph)) ++paragraph
        return paragraph
    }
    open fun setCustomClassifier(classifier: BidiClassifier?) { customClassifier = classifier }
    open fun getCustomClassifier(): BidiClassifier? = customClassifier
    open fun getCustomizedClass(c: Int): Int = BidiPropertyCore.customizedClass(c, customClassifier)
    open fun setLine(start: Int, limit: Int): Bidi {
        verifyValidPara(); verifyRange(start, 0, limit); verifyRange(limit, 0, length + 1)
        if (getParagraphIndex(start) != getParagraphIndex(limit - 1)) throw IllegalArgumentException()
        val line = Bidi()
        lineCore.setLine(start, limit, line.state)
        return line
    }
    open fun getLevelAt(index: Int): Byte { verifyValidParaOrLine(); verifyRange(index, 0, length); return lineCore.getLevelAt(index) }
    open fun getLevels(): ByteArray { verifyValidParaOrLine(); return if (length <= 0) ByteArray(0) else lineCore.getLevels() }
    open fun getLogicalRun(position: Int): BidiRun { verifyValidParaOrLine(); verifyRange(position, 0, length); return lineCore.getLogicalRun(position) }
    open fun countRuns(): Int { verifyValidParaOrLine(); lineCore.getRuns(); return runCount }
    open fun getVisualRun(index: Int): BidiRun { verifyValidParaOrLine(); lineCore.getRuns(); verifyRange(index, 0, runCount); return lineCore.getVisualRun(index) }
    open fun getVisualIndex(index: Int): Int { verifyValidParaOrLine(); verifyRange(index, 0, length); return lineCore.getVisualIndex(index) }
    open fun getLogicalIndex(index: Int): Int {
        verifyValidParaOrLine(); verifyRange(index, 0, resultLength)
        if (insertPoints.size == 0 && controlCount == 0) {
            if (direction == LTR) return index
            if (direction == RTL) return length - index - 1
        }
        lineCore.getRuns(); return lineCore.getLogicalIndex(index)
    }
    open fun getLogicalMap(): IntArray { countRuns(); return if (length <= 0) IntArray(0) else lineCore.getLogicalMap() }
    open fun getVisualMap(): IntArray { countRuns(); return if (resultLength <= 0) IntArray(0) else lineCore.getVisualMap() }
    open fun createLineBidi(start: Int, limit: Int): Bidi = setLine(start, limit)
    open fun isMixed(): Boolean = !isLeftToRight() && !isRightToLeft()
    open fun isLeftToRight(): Boolean = getDirection() == LTR && (paraLevel.toInt() and 1) == 0
    open fun isRightToLeft(): Boolean = getDirection() == RTL && (paraLevel.toInt() and 1) == 1
    open fun baseIsLeftToRight(): Boolean = getParaLevel() == LTR
    open fun getBaseLevel(): Int = getParaLevel().toInt()
    open fun getRunCount(): Int = countRuns()
    private fun getLogicalToVisualRunsMap() {
        if (isGoodLogicalToVisualRunsMap) return
        val count = countRuns()
        if (logicalToVisualRunsMap == null || logicalToVisualRunsMap!!.size < count) logicalToVisualRunsMap = IntArray(count)
        val keys = LongArray(count) { (rawRun(it).start.toLong() shl 32) + it }
        keys.sort()
        for (i in 0 until count) logicalToVisualRunsMap!![i] = (keys[i] and 0xffffffffL).toInt()
        isGoodLogicalToVisualRunsMap = true
    }
    open fun getRunLevel(index: Int): Int {
        verifyValidParaOrLine(); lineCore.getRuns(); verifyRange(index, 0, runCount); getLogicalToVisualRunsMap()
        return rawRun(bidiIntAt(logicalToVisualRunsMap, index)).level.toInt()
    }
    open fun getRunStart(index: Int): Int {
        verifyValidParaOrLine(); lineCore.getRuns(); verifyRange(index, 0, runCount); getLogicalToVisualRunsMap()
        return rawRun(bidiIntAt(logicalToVisualRunsMap, index)).start
    }
    open fun getRunLimit(index: Int): Int {
        verifyValidParaOrLine(); lineCore.getRuns(); verifyRange(index, 0, runCount); getLogicalToVisualRunsMap()
        val visual = bidiIntAt(logicalToVisualRunsMap, index)
        val size = if (visual == 0) rawRun(visual).limit else rawRun(visual).limit - rawRun(visual - 1).limit
        return rawRun(visual).start + size
    }
    open fun writeReordered(options: Int): String { verifyValidParaOrLine(); return if (length == 0) "" else BidiWriterCore.writeReordered(writer, options) }
    companion object {
        const val LEVEL_DEFAULT_LTR: Byte = 126
        const val LEVEL_DEFAULT_RTL: Byte = 127
        const val MAX_EXPLICIT_LEVEL: Byte = 125
        const val LEVEL_OVERRIDE: Byte = -128
        const val MAP_NOWHERE = -1
        const val LTR: Byte = 0
        const val RTL: Byte = 1
        const val MIXED: Byte = 2
        const val NEUTRAL: Byte = 3
        const val KEEP_BASE_COMBINING: Short = 1
        const val DO_MIRRORING: Short = 2
        const val INSERT_LRM_FOR_NUMERIC: Short = 4
        const val REMOVE_BIDI_CONTROLS: Short = 8
        const val OUTPUT_REVERSE: Short = 16
        const val REORDER_DEFAULT: Short = 0
        const val REORDER_NUMBERS_SPECIAL: Short = 1
        const val REORDER_GROUP_NUMBERS_WITH_R: Short = 2
        const val REORDER_RUNS_ONLY: Short = 3
        const val REORDER_INVERSE_NUMBERS_AS_L: Short = 4
        const val REORDER_INVERSE_LIKE_DIRECT: Short = 5
        const val REORDER_INVERSE_FOR_NUMBERS_SPECIAL: Short = 6
        const val OPTION_DEFAULT = 0
        const val OPTION_INSERT_MARKS = 1
        const val OPTION_REMOVE_CONTROLS = 2
        const val OPTION_STREAMING = 4
        @Deprecated("ICU internal API") const val CLASS_DEFAULT = 23
        const val DIRECTION_LEFT_TO_RIGHT = 0
        const val DIRECTION_RIGHT_TO_LEFT = 1
        const val DIRECTION_DEFAULT_LEFT_TO_RIGHT = 126
        const val DIRECTION_DEFAULT_RIGHT_TO_LEFT = 127
        private fun isDefault(level: Byte) = (level.toInt() and 126) == 126
        @JvmStatic fun getBaseDirection(paragraph: CharSequence?): Byte = BidiReorderingCore.baseDirection(paragraph)
        @JvmStatic fun reorderLogical(levels: ByteArray?): IntArray? = BidiReorderingCore.reorderLogical(levels)
        @JvmStatic fun reorderVisual(levels: ByteArray?): IntArray? = BidiReorderingCore.reorderVisual(levels)
        @JvmStatic fun invertMap(map: IntArray?): IntArray? = BidiReorderingCore.invertMap(map)
        @JvmStatic fun requiresBidi(text: CharArray?, start: Int, limit: Int): Boolean = BidiReorderingCore.requiresBidi(text, start, limit)
        @JvmStatic fun <T> reorderVisually(levels: ByteArray?, levelStart: Int, objects: Array<T>?, objectStart: Int, count: Int) = BidiReorderingCore.reorderVisually(levels, levelStart, objects, objectStart, count)
        @JvmStatic fun writeReverse(text: String?, options: Int): String = BidiWriterCore.reverse(text, options)
    }
}
