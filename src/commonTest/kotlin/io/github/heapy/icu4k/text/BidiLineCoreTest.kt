// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiLineCoreTest {
    @Test fun mode0Level0() = check(0, 0, 0)
    @Test fun mode0Level1() = check(0, 1, 1)
    @Test fun mode0Level125() = check(0, 125, 2)
    @Test fun mode0Level126() = check(0, 126, 3)
    @Test fun mode0Level127() = check(0, 127, 4)
    @Test fun mode1Level0() = check(1, 0, 5)
    @Test fun mode1Level1() = check(1, 1, 6)
    @Test fun mode1Level125() = check(1, 125, 7)
    @Test fun mode1Level126() = check(1, 126, 8)
    @Test fun mode1Level127() = check(1, 127, 9)
    @Test fun mode2Level0() = check(2, 0, 10)
    @Test fun mode2Level1() = check(2, 1, 11)
    @Test fun mode2Level125() = check(2, 125, 12)
    @Test fun mode2Level126() = check(2, 126, 13)
    @Test fun mode2Level127() = check(2, 127, 14)
    @Test fun mode4Level0() = check(4, 0, 15)
    @Test fun mode4Level1() = check(4, 1, 16)
    @Test fun mode4Level125() = check(4, 125, 17)
    @Test fun mode4Level126() = check(4, 126, 18)
    @Test fun mode4Level127() = check(4, 127, 19)
    @Test fun mode5Level0() = check(5, 0, 20)
    @Test fun mode5Level1() = check(5, 1, 21)
    @Test fun mode5Level125() = check(5, 125, 22)
    @Test fun mode5Level126() = check(5, 126, 23)
    @Test fun mode5Level127() = check(5, 127, 24)
    @Test fun mode6Level0() = check(6, 0, 25)
    @Test fun mode6Level1() = check(6, 1, 26)
    @Test fun mode6Level125() = check(6, 125, 27)
    @Test fun mode6Level126() = check(6, 126, 28)
    @Test fun mode6Level127() = check(6, 127, 29)
    private fun check(mode: Int, level: Int, fixture: Int) {
        val expected = longArrayOf(
-5711960469364842743L,
7965920409445222274L,
4742850072148251392L,
-2034010708288406830L,
-2034010708288406830L,
-4094435262491221896L,
-1098813318665418013L,
-4491705133715288173L,
-4497859163807177080L,
-4497859163807177080L,
2563459812714216002L,
-3452637885043831207L,
6384287365923435909L,
-8396224947945380859L,
-8396224947945380859L,
4785446511570962279L,
2191783514494659129L,
-7109820959631552731L,
-2402812507560491500L,
-2402812507560491500L,
-2786337752552035192L,
5698244722947707765L,
8604742489159578745L,
8025602025678153439L,
8025602025678153439L,
-4506224991741286225L,
6300057178393768905L,
2223875347507027789L,
8079868786492943136L,
8079868786492943136L,
)
        val texts = listOf("abc", "אבג", "a(אב)12 ب٣ z", "a\r\nאב\na\u2029ب", "a\u2067אב \u2066x\u2069z\u2069 q",
            "\u202da(אב)\u202cب", "😀𞤀𞸀" + charArrayOf('\ud800', 'X', '\udc00').concatToString(),
            "א a   \n", "a\u200c\u200d\u200e\u200fא\u202c ", "ا 12 a 34 אב 56 a .".repeat(4), "aא".repeat(15))
        val hash = Hash()
        for (options in listOf(0, 1, 2, 4)) for (text in texts) {
            val state = BidiImplicitFixture(BidiPropertyFixture(text, level, mode, options, false))
            if (!hash.call(0) { state.resolveFresh(null) }) continue
            hash.all(lineFixtureFromResolved(state))
        }
        assertEquals(expected[fixture], hash.value, "mode=$mode level=$level")
    }
    @Test fun coldControlIndexLookupDoesNotInventRunInitialization() {
        val resolved = BidiImplicitFixture(BidiPropertyFixture("a\u200d", 0, 0, 2, false))
        resolved.resolveFresh(null)
        val state = lineFixtureFromResolved(resolved)
        val core = BidiLineCore(state)
        assertFailsWith<IndexOutOfBoundsException> { core.getVisualIndex(0) }
        assertEquals(-1, core.getVisualIndex(1))
        assertEquals(-1, state.runCount)
        core.getRuns()
        assertEquals(0, core.getVisualIndex(0))
        assertContentEquals(intArrayOf(0, -1), core.getLogicalMap())
        assertContentEquals(intArrayOf(0), core.getVisualMap())
    }
    private data class Frozen(val state: String, val expected: Long)
    @Test fun sourceRunsOnlyFramesKeepOriginalMapsAndSliceFailures() {
        // ASCII records from ICU4J 78.3 after public REORDER_RUNS_ONLY setPara.
        // These include original-length/short-dirProps states and lone units.
        val cases = listOf(
Frozen("0061206705d005d10020206600782069007a206900200071|12|12|8|1|0|0|2|12|6|0|3|2|0|0|8,0,0,0,0,0,0,0,0,0|1,0,0,0,0,0,0,0,0,0|0,0,0,9,1,1,9,0|0,0,1,1,1,1,2,1,2,0,0,0|11,1,0,0;10,2,1,0;2,5,0,0;0,6,0,0;8,7,2,0;6,8,2,0|11,1,0,0;10,2,1,0;2,5,0,0;0,6,0,0;8,7,2,0;6,8,2,0|0,0,0,0|2|0|0|", -6090170193916372503L),
Frozen("0061002805d005d10029003100320020062806630020007a|12|12|12|1|0|0|2|12|9|0|3|0|0|0|12,0,0,0,0,0,0,0,0,0|1,0,0,0,0,0,0,0,0,0|0,10,1,1,10,5,13,9,2,2,9,0|0,0,1,1,0,2,2,1,1,2,0,0|11,1,0,0;10,2,1,0;5,4,2,0;7,6,0,0;9,7,2,0;4,8,1,0;2,10,0,0;1,11,1,0;0,12,0,0|11,1,0,0;10,2,1,0;5,4,2,0;7,6,0,0;9,7,2,0;4,8,1,0;2,10,0,0;1,11,1,0;0,12,0,0|0,0,0,0|2|0|0|", -4897638961106485610L),
Frozen("062700200031003200200061002000330034002005d005d1002000350036002000610020002e|19|19|19|1|0|0|2|19|12|0|3|1|0|0|19,0,0,0,0,0,0,0,0,0|1,0,0,0,0,0,0,0,0,0|2,2,9,13,9,0,9,2,2,9,2,2,9,1,1,9,0,9,6|1,1,2,2,0,0,0,0,0,0,1,1,1,2,2,0,0,0,0|17,2,1,0;16,3,0,0;15,4,1,0;10,7,0,0;13,9,2,0;9,10,1,0;7,12,0,0;6,13,1,0;5,14,0,0;4,15,1,0;0,17,0,0;2,19,2,0|17,2,1,0;16,3,0,0;15,4,1,0;10,7,0,0;13,9,2,0;9,10,1,0;7,12,0,0;6,13,1,0;5,14,0,0;4,15,1,0;0,17,0,0;2,19,2,0|0,0,0,0|2|0|0|", -4912020707645469012L),
Frozen("d83dde00d83add00d83bde00d8000058dc00|9|9|9|1|1|0|2|9|3|0|3|0|0|0|9,0,0,0,0,0,0,0,0,0|0,0,0,0,0,0,0,0,0,0|0,0,0,18,13,18,1,18,10|1,1,1,1,1,1,2,2,2|6,3,2,0;2,7,0,0;0,9,1,0|6,3,2,0;2,7,0,0;0,9,1,0|0,0,0,0|2|0|0|", 4144047493194081133L),
Frozen("0061000a05d005d1000a006120290628|8|7|7|3|0|0|2|7|6|0|3|4|0|0|2,5,7,0,0,0,0,0,0,0|1,1,1,0,0,0,0,0,0,0|0,7,1,1,7,0,7|0,0,1,1,0,0,0|6,1,1,0;5,2,0,0;4,3,1,0;2,5,0,0;1,6,1,0;0,7,0,0|6,1,1,0;5,2,0,0;4,3,1,0;2,5,0,0;1,6,1,0;0,7,0,0|0,0,0,0|2|0|0|", 299324398941261332L),
)
        for ((record, expected) in cases) {
            val hash = Hash()
            hash.all(decode(record))
            assertEquals(expected, hash.value)
        }
    }
    private fun decode(record: String): BidiLineFixture {
        val f = record.split('|')
        fun ints(value: String) = if (value.isEmpty()) IntArray(0) else value.split(',').map { it.toInt() }.toIntArray()
        fun bytes(value: String) = ints(value).map { it.toByte() }.toByteArray()
        fun runs(value: String): Array<BidiRun?>? = if (value == "-") null else if (value.isEmpty()) arrayOfNulls(0) else value.split(';').map {
            if (it == "n") null else ints(it).let { v -> BidiRun(v[0], v[1], v[2].toByte()).also { r -> r.insertRemove = v[3] } }
        }.toTypedArray()
        return BidiLineFixture().apply {
            text = CharArray(f[0].length / 4) { f[0].substring(it * 4, it * 4 + 4).toInt(16).toChar() }
            originalLength = f[1].toInt(); length = f[2].toInt(); resultLength = f[3].toInt(); paraCount = f[4].toInt()
            paraLevel = f[5].toByte(); defaultParaLevel = f[6].toByte(); direction = f[7].toByte(); trailingWSStart = f[8].toInt()
            runCount = f[9].toInt(); controlCount = f[10].toInt(); reorderingMode = f[11].toInt(); reorderingOptions = f[12].toInt()
            inverse = f[13] == "1"; orderParagraphsLTR = f[14] == "1"
            paraLimits = ints(f[15]); paraLevels = bytes(f[16]); dirProps = bytes(f[17]); levels = bytes(f[18])
            dirPropsMemory = dirProps; levelsMemory = levels
            runsMemory = runs(f[20])!!; simpleRuns = runs(f[21])!!
            runs = when (f[22]) { "2" -> runsMemory; "1" -> simpleRuns; else -> runs(f[19]) }
            insertPoints.size = f[23].toInt(); insertPoints.confirmed = f[24].toInt()
            insertPoints.points = if (f[25].isEmpty()) arrayOfNulls(0) else f[25].split(';').map {
                if (it == "n") null else ints(it).let { v -> BidiImplicitCore.Point(v[0], v[1]) }
            }.toTypedArray()
            parent = this
        }
    }
    private class Hash {
        var value = -3750763034362895579L
        private fun mix(v: Int) { value = (value xor v.toLong()) * 1099511628211L }
        private fun run(r: BidiRun) { mix(r.start); mix(r.limit); mix(r.level.toInt()); mix(r.insertRemove) }
        fun call(kind: Int, state: BidiLineFixture? = null, action: () -> Any?): Boolean {
            try {
                val v = action()
                mix(0)
                when (kind) {
                    1 -> mix((v as Byte).toInt())
                    2 -> mix(v as Int)
                    3 -> run(v as BidiRun)
                    4 -> { val a = v as ByteArray; mix(a.size); for (b in a) mix(b.toInt()); mix(if (a === state!!.levels) 1 else 0) }
                    5 -> { val a = v as IntArray; mix(a.size); for (i in a) mix(i) }
                    6 -> { val s = v as String; mix(s.length); for (c in s) mix(c.code) }
                }
                return true
            } catch (e: IndexOutOfBoundsException) { mix(1) }
            catch (e: NullPointerException) { mix(2) }
            catch (e: IllegalStateException) { mix(3); mix(e.message!!.hashCode()) }
            catch (e: IllegalArgumentException) { mix(4); mix(e.message?.hashCode() ?: 0) }
            return false
        }
        private fun snapshot(p: BidiLineFixture) {
            for (v in listOf(p.length, p.originalLength, p.resultLength, p.paraCount, p.paraLevel.toInt(), p.direction.toInt(), p.trailingWSStart, p.runCount, p.controlCount, p.reorderingMode, p.reorderingOptions)) mix(v)
            mix(p.levels.size); for (b in p.levels) mix(b.toInt())
            for (a in listOf(p.runs, p.runsMemory, p.simpleRuns)) {
                if (a == null) mix(-1) else {
                    mix(a.size)
                    for (r in a) { mix(if (r == null) 0 else 1); if (r != null) run(r) }
                }
            }
            mix(if (p.runs === p.runsMemory) 1 else 0); mix(if (p.runs === p.simpleRuns) 1 else 0)
        }
        private fun exercise(p: BidiLineFixture) {
            val core = BidiLineCore(p)
            val ok = call(0) { core.getRuns() }
            snapshot(p)
            if (!ok) return
            for (r in 0 until p.runCount) call(3) { core.getVisualRun(r) }
            for (i in 0 until p.length) {
                call(1) { core.getLevelAt(i) }; call(3) { core.getLogicalRun(i) }; call(2) { core.getVisualIndex(i) }
            }
            for (i in 0 until p.resultLength) call(2) { core.getLogicalIndex(i) }
            call(5) { core.getLogicalMap() }; call(5) { core.getVisualMap() }
            call(4, p) { core.getLevels() }; call(4, p) { core.getLevels() }; snapshot(p)
            for (o in listOf(0, 1, 2, 3, 4, 8, 10, 16, 18, 20, 31)) call(6) { BidiWriterCore.writeReordered(p.writerInput(), o) }
            snapshot(p)
        }
        fun all(p: BidiLineFixture) {
            exercise(p)
            if (p.length == 0) return
            for ((start, limit) in listOf(0 to p.length, 0 to 1, p.length - 1 to p.length)) {
                val line = BidiLineFixture()
                if (!call(0) { BidiLineCore(p).setLine(start, limit, line) }) continue
                assertSame(p, line.parent)
                exercise(line)
            }
        }
    }
}
