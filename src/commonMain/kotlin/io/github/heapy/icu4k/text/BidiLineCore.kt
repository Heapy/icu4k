// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacterDirection as D

/** Resolved paragraph/line levels, visual runs and logical/visual index maps. */
internal class BidiLineCore(private val input: Input) {
    internal interface Input : BidiImplicitCore.Input {
        override var text: CharArray
        override var originalLength: Int
        override var dirProps: ByteArray
        override var levels: ByteArray
        override var reorderingMode: Int
        override var reorderingOptions: Int
        override var trailingWSStart: Int
        var direction: Byte
        var resultLength: Int
        var runCount: Int
        var runs: Array<BidiRun?>?
        val simpleRuns: Array<BidiRun?>
        val runsMemory: Array<BidiRun?>
        fun getRunsMemory(length: Int)
        fun allocateDirProps(length: Int): ByteArray
        fun allocateLevels(length: Int): ByteArray
        fun linkParent(parent: Input)
    }
    private fun run(index: Int): BidiRun = bidiArrayAt(input.runs, index)!!
    private fun level(index: Int) = bidiByteAt(input.levels, index)
    private fun property(index: Int) = bidiByteAt(input.dirProps, index).toInt()
    private fun isControl(index: Int) = BidiReorderingCore.isBidiControlChar(bidiCharAt(input.text, index).code)
    fun setTrailingWSStart() {
        val props = input.dirProps
        val levels = input.levels
        var start = input.length
        val paraLevel = input.paraLevel
        if (bidiByteAt(props, start - 1).toInt() == D.BLOCK_SEPARATOR) { input.trailingWSStart = start; return }
        while (start > 0 && (flag(bidiByteAt(props, start - 1).toInt()) and MASK_WS) != 0) --start
        while (start > 0 && bidiByteAt(levels, start - 1) == paraLevel) --start
        input.trailingWSStart = start
    }
    /** Populate a freshly constructed line state; link its parent only on success. */
    fun setLine(start: Int, limit: Int, line: Input): Input {
        val length = limit - start
        line.length = length; line.originalLength = length; line.resultLength = length
        line.text = CharArray(length)
        input.text.copyInto(line.text, 0, start, start + length)
        line.paraLevel = input.getParaLevelAt(start)
        line.paraCount = input.paraCount
        line.runs = arrayOfNulls(0)
        line.reorderingMode = input.reorderingMode; line.reorderingOptions = input.reorderingOptions
        if (input.controlCount > 0) {
            for (j in start until limit) if (isControl(j)) ++line.controlCount
            line.resultLength -= line.controlCount
        }
        line.dirProps = line.allocateDirProps(length)
        input.dirProps.copyInto(line.dirProps, 0, start, start + length)
        line.levels = line.allocateLevels(length)
        input.levels.copyInto(line.levels, 0, start, start + length)
        line.runCount = -1
        if (input.direction.toInt() != 2) {
            line.direction = input.direction
            line.trailingWSStart = when {
                input.trailingWSStart <= start -> 0
                input.trailingWSStart < limit -> input.trailingWSStart - start
                else -> length
            }
        } else {
            BidiLineCore(line).setTrailingWSStart()
            val trailing = line.trailingWSStart
            if (trailing == 0) line.direction = (line.paraLevel.toInt() and 1).toByte()
            else {
                val parity = (bidiByteAt(line.levels, 0).toInt() and 1).toByte()
                if (trailing < length && (line.paraLevel.toInt() and 1) != parity.toInt()) line.direction = 2
                else {
                    var i = 1
                    while (true) {
                        if (i == trailing) { line.direction = parity; break }
                        if ((bidiByteAt(line.levels, i).toInt() and 1) != parity.toInt()) { line.direction = 2; break }
                        ++i
                    }
                }
            }
            when (line.direction.toInt()) {
                0 -> { line.paraLevel = ((line.paraLevel + 1) and 1.inv()).toByte(); line.trailingWSStart = 0 }
                1 -> { line.paraLevel = (line.paraLevel.toInt() or 1).toByte(); line.trailingWSStart = 0 }
            }
        }
        line.linkParent(input)
        return line
    }
    fun getLevelAt(index: Int): Byte = if (input.direction.toInt() != 2 || index >= input.trailingWSStart) input.getParaLevelAt(index) else level(index)
    fun getLevels(): ByteArray {
        val start = input.trailingWSStart
        val length = input.length
        if (start != length) {
            input.levels.fill(input.paraLevel, start, length)
            input.trailingWSStart = length
        }
        return if (length < input.levels.size) input.levels.copyOfRange(0, length) else input.levels
    }
    fun getLogicalRun(position: Int): BidiRun {
        val result = BidiRun()
        getRuns()
        val count = input.runCount
        var visualStart = 0
        var logicalLimit = 0
        var current = run(0)
        for (i in 0 until count) {
            current = run(i)
            logicalLimit = current.start + current.limit - visualStart
            if (position >= current.start && position < logicalLimit) break
            visualStart = current.limit
        }
        result.start = current.start; result.limit = logicalLimit; result.level = current.level
        return result
    }
    fun getVisualRun(index: Int): BidiRun {
        val start = run(index).start
        val level = run(index).level
        val limit = if (index > 0) start + run(index).limit - run(index - 1).limit else start + run(0).limit
        return BidiRun(start, limit, level)
    }
    private fun getSingleRun(level: Byte) {
        input.runs = input.simpleRuns; input.runCount = 1
        bidiArrayAt(input.runs, 0)
        input.runs!![0] = BidiRun(0, input.length, level)
    }
    private fun reorderLine(initialMin: Byte, initialMax: Byte) {
        if (initialMax <= (initialMin.toInt() or 1)) return
        val min = (initialMin + 1).toByte()
        var max = initialMax
        val runs = input.runs
        val levels = input.levels
        var count = input.runCount
        if (input.trailingWSStart < input.length) --count
        max = (max - 1).toByte()
        while (max >= min) {
            var first = 0
            while (true) {
                while (first < count && bidiByteAt(levels, bidiArrayAt(runs, first)!!.start) < max) ++first
                if (first >= count) break
                var limit = first
                do { ++limit } while (limit < count && bidiByteAt(levels, bidiArrayAt(runs, limit)!!.start) >= max)
                var end = limit - 1
                while (first < end) {
                    val temp = bidiArrayAt(runs, first)
                    runs!![first] = bidiArrayAt(runs, end); runs[end] = temp
                    ++first; --end
                }
                if (limit == count) break else first = limit + 1
            }
            max = (max - 1).toByte()
        }
        if ((min.toInt() and 1) == 0) {
            var first = 0
            if (input.trailingWSStart == input.length) --count
            while (first < count) {
                val temp = bidiArrayAt(runs, first)
                runs!![first] = bidiArrayAt(runs, count); runs[count] = temp
                ++first; --count
            }
        }
    }
    fun getRunFromLogicalIndex(index: Int): Int {
        var visualStart = 0
        for (i in 0 until input.runCount) {
            val current = run(i)
            val length = current.limit - visualStart
            val start = current.start
            if (index >= start && index < start + length) return i
            visualStart += length
        }
        throw IllegalStateException("Internal ICU error in getRunFromLogicalIndex")
    }
    fun getRuns() {
        if (input.runCount >= 0) return
        if (input.direction.toInt() != 2) getSingleRun(input.paraLevel)
        else {
            val length = input.length
            var limit = input.trailingWSStart
            var count = 0
            var previous: Byte = -1
            for (i in 0 until limit) {
                if (level(i) != previous) { ++count; previous = level(i) }
            }
            if (count == 1 && limit == length) getSingleRun(level(0))
            else {
                var min: Byte = 126; var max: Byte = 0
                if (limit < length) ++count
                input.getRunsMemory(count)
                val runs = input.runsMemory
                var runIndex = 0
                var i = 0
                do {
                    val start = i
                    previous = level(i)
                    if (previous < min) min = previous
                    if (previous > max) max = previous
                    do { ++i } while (i < limit && level(i) == previous)
                    bidiArrayAt(runs, runIndex)
                    runs[runIndex++] = BidiRun(start, i - start, previous)
                } while (i < limit)
                if (limit < length) {
                    bidiArrayAt(runs, runIndex)
                    runs[runIndex] = BidiRun(limit, length - limit, input.paraLevel)
                    if (input.paraLevel < min) min = input.paraLevel
                }
                input.runs = runs; input.runCount = count
                reorderLine(min, max)
                limit = 0
                for (j in 0 until count) {
                    val current = bidiArrayAt(runs, j)!!
                    current.level = level(current.start)
                    current.limit += limit
                    limit = current.limit
                }
                if (runIndex < count) bidiArrayAt(runs, if ((input.paraLevel.toInt() and 1) != 0) 0 else runIndex)!!.level = input.paraLevel
            }
        }
        val points = input.insertPoints
        for (i in 0 until points.size) {
            val point = bidiArrayAt(points.points, i)!!
            val index = getRunFromLogicalIndex(point.pos)
            run(index).insertRemove = run(index).insertRemove or point.flag
        }
        if (input.controlCount > 0) for (i in 0 until input.length) if (isControl(i)) {
            val index = getRunFromLogicalIndex(i)
            --run(index).insertRemove
        }
    }
    fun getVisualIndex(logicalIndex: Int): Int {
        var visual = -1
        when (input.direction.toInt()) {
            0 -> visual = logicalIndex
            1 -> visual = input.length - logicalIndex - 1
            else -> {
                getRuns()
                var start = 0
                var found = false
                for (i in 0 until input.runCount) {
                    val current = run(i)
                    val length = current.limit - start
                    val offset = logicalIndex - current.start
                    if (offset >= 0 && offset < length) {
                        visual = if (current.isEvenRun()) start + offset else start + length - offset - 1
                        found = true; break
                    }
                    start += length
                }
                if (!found) return -1
            }
        }
        if (input.insertPoints.size > 0) {
            var start = 0; var marks = 0; var i = 0
            while (true) {
                val current = run(i)
                val length = current.limit - start
                val flags = current.insertRemove
                if ((flags and 5) > 0) ++marks
                if (visual < current.limit) return visual + marks
                if ((flags and 10) > 0) ++marks
                ++i; start += length
            }
        } else if (input.controlCount > 0) {
            if (isControl(logicalIndex)) return -1
            var start = 0; var controls = 0; var i = 0
            while (true) {
                val current = run(i)
                val length = current.limit - start
                val flags = current.insertRemove
                if (visual >= current.limit) { controls -= flags; ++i; start += length; continue }
                if (flags == 0) return visual - controls
                val from = if (current.isEvenRun()) current.start else logicalIndex + 1
                val until = if (current.isEvenRun()) logicalIndex else current.start + length
                for (j in from until until) if (isControl(j)) ++controls
                return visual - controls
            }
        }
        return visual
    }
    fun getLogicalIndex(initialVisual: Int): Int {
        var visual = initialVisual
        val runs = input.runs
        val count = input.runCount
        if (input.insertPoints.size > 0) {
            var marks = 0; var start = 0; var i = 0
            while (true) {
                val current = bidiArrayAt(runs, i)!!
                val length = current.limit - start
                val flags = current.insertRemove
                if ((flags and 5) > 0) { if (visual <= start + marks) return -1; ++marks }
                if (visual < current.limit + marks) { visual -= marks; break }
                if ((flags and 10) > 0) { if (visual == start + length + marks) return -1; ++marks }
                ++i; start += length
            }
        } else if (input.controlCount > 0) {
            var controls = 0; var start = 0; var i = 0
            while (true) {
                val current = bidiArrayAt(runs, i)!!
                val length = current.limit - start
                val flags = current.insertRemove
                if (visual >= current.limit - controls + flags) { controls -= flags; ++i; start += length; continue }
                if (flags == 0) { visual += controls; break }
                val from = current.start
                val even = current.isEvenRun()
                val end = from + length - 1
                for (j in 0 until length) {
                    if (isControl(if (even) from + j else end - j)) ++controls
                    if (visual + controls == start + j) break
                }
                visual += controls
                break
            }
        }
        var i: Int
        if (count <= 10) { i = 0; while (visual >= bidiArrayAt(runs, i)!!.limit) ++i }
        else {
            var begin = 0; var limit = count
            while (true) {
                i = (begin + limit) ushr 1
                if (visual >= bidiArrayAt(runs, i)!!.limit) begin = i + 1
                else if (i == 0 || visual >= bidiArrayAt(runs, i - 1)!!.limit) break
                else limit = i
            }
        }
        val current = bidiArrayAt(runs, i)!!
        return if (current.isEvenRun()) {
            if (i > 0) visual -= bidiArrayAt(runs, i - 1)!!.limit
            current.start + visual
        } else current.start + current.limit - visual - 1
    }
    fun getLogicalMap(): IntArray {
        val map = IntArray(input.length)
        if (input.length > input.resultLength) map.fill(-1)
        var visualStart = 0
        for (j in 0 until input.runCount) {
            val current = run(j)
            var logical = current.start
            val limit = current.limit
            if (current.isEvenRun()) do { put(map, logical++, visualStart++) } while (visualStart < limit)
            else {
                logical += limit - visualStart
                do { put(map, --logical, visualStart++) } while (visualStart < limit)
            }
        }
        if (input.insertPoints.size > 0) {
            var marks = 0; visualStart = 0
            for (i in 0 until input.runCount) {
                val current = run(i)
                val length = current.limit - visualStart
                if ((current.insertRemove and 5) > 0) ++marks
                if (marks > 0) for (j in current.start until current.start + length) put(map, j, bidiIntAt(map, j) + marks)
                if ((current.insertRemove and 10) > 0) ++marks
                visualStart += length
            }
        } else if (input.controlCount > 0) {
            var controls = 0; visualStart = 0
            for (i in 0 until input.runCount) {
                val current = run(i)
                val length = current.limit - visualStart
                val flags = current.insertRemove
                if (controls - flags != 0) {
                    val start = current.start; val limit = start + length
                    if (flags == 0) for (j in start until limit) put(map, j, bidiIntAt(map, j) - controls)
                    else for (j in 0 until length) {
                        val k = if (current.isEvenRun()) start + j else limit - j - 1
                        if (isControl(k)) { ++controls; put(map, k, -1); continue }
                        put(map, k, bidiIntAt(map, k) - controls)
                    }
                }
                visualStart += length
            }
        }
        return map
    }
    fun getVisualMap(): IntArray {
        val allocation = maxOf(input.length, input.resultLength)
        val map = IntArray(allocation)
        var visualStart = 0; var index = 0
        for (j in 0 until input.runCount) {
            val current = run(j)
            var logical = current.start
            val limit = current.limit
            if (current.isEvenRun()) {
                do { put(map, index++, logical++) } while (++visualStart < limit)
            } else {
                logical += limit - visualStart
                do { put(map, index++, --logical) } while (++visualStart < limit)
            }
        }
        if (input.insertPoints.size > 0) {
            var marks = 0
            for (i in 0 until input.runCount) {
                if ((run(i).insertRemove and 5) > 0) ++marks
                if ((run(i).insertRemove and 10) > 0) ++marks
            }
            var k = input.resultLength
            var i = input.runCount - 1
            while (i >= 0 && marks > 0) {
                val current = run(i)
                if ((current.insertRemove and 10) > 0) { put(map, --k, -1); --marks }
                visualStart = if (i > 0) run(i - 1).limit else 0
                var j = current.limit - 1
                while (j >= visualStart && marks > 0) { put(map, --k, bidiIntAt(map, j)); --j }
                if ((current.insertRemove and 5) > 0) { put(map, --k, -1); --marks }
                --i
            }
        } else if (input.controlCount > 0) {
            visualStart = 0
            var k = 0
            for (i in 0 until input.runCount) {
                val current = run(i)
                val length = current.limit - visualStart
                val flags = current.insertRemove
                if (flags == 0 && k == visualStart) k += length
                else if (flags == 0) for (j in visualStart until current.limit) put(map, k++, bidiIntAt(map, j))
                else {
                    val start = current.start; val end = start + length - 1
                    for (j in 0 until length) {
                        val m = if (current.isEvenRun()) start + j else end - j
                        if (!isControl(m)) put(map, k++, m)
                    }
                }
                visualStart += length
            }
        }
        return if (allocation == input.resultLength) map else map.copyOfRange(0, input.resultLength)
    }
    private companion object {
        fun flag(property: Int) = 1 shl property
        val MASK_WS = flag(D.BLOCK_SEPARATOR) or flag(D.SEGMENT_SEPARATOR) or flag(D.WHITE_SPACE_NEUTRAL) or
            flag(D.BOUNDARY_NEUTRAL) or flag(D.LEFT_TO_RIGHT_EMBEDDING) or flag(D.LEFT_TO_RIGHT_OVERRIDE) or
            flag(D.RIGHT_TO_LEFT_EMBEDDING) or flag(D.RIGHT_TO_LEFT_OVERRIDE) or flag(D.POP_DIRECTIONAL_FORMAT) or
            flag(D.LEFT_TO_RIGHT_ISOLATE.toInt()) or flag(D.RIGHT_TO_LEFT_ISOLATE.toInt()) or flag(D.FIRST_STRONG_ISOLATE.toInt()) or flag(D.POP_DIRECTIONAL_ISOLATE.toInt())
        fun put(map: IntArray, index: Int, value: Int) { bidiIntAt(map, index); map[index] = value }
    }
}
internal expect fun bidiIntAt(data: IntArray?, index: Int): Int
