// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class BidiImplicitCoreTest {
    @Test fun mode0Level0() = check(0, 0, 0)
    @Test fun mode0Level1() = check(0, 1, 1)
    @Test fun mode0Level2() = check(0, 2, 2)
    @Test fun mode0Level125() = check(0, 125, 3)
    @Test fun mode0Level126() = check(0, 126, 4)
    @Test fun mode0Level127() = check(0, 127, 5)
    @Test fun mode1Level0() = check(1, 0, 6)
    @Test fun mode1Level1() = check(1, 1, 7)
    @Test fun mode1Level2() = check(1, 2, 8)
    @Test fun mode1Level125() = check(1, 125, 9)
    @Test fun mode1Level126() = check(1, 126, 10)
    @Test fun mode1Level127() = check(1, 127, 11)
    @Test fun mode2Level0() = check(2, 0, 12)
    @Test fun mode2Level1() = check(2, 1, 13)
    @Test fun mode2Level2() = check(2, 2, 14)
    @Test fun mode2Level125() = check(2, 125, 15)
    @Test fun mode2Level126() = check(2, 126, 16)
    @Test fun mode2Level127() = check(2, 127, 17)
    @Test fun mode4Level0() = check(4, 0, 18)
    @Test fun mode4Level1() = check(4, 1, 19)
    @Test fun mode4Level2() = check(4, 2, 20)
    @Test fun mode4Level125() = check(4, 125, 21)
    @Test fun mode4Level126() = check(4, 126, 22)
    @Test fun mode4Level127() = check(4, 127, 23)
    @Test fun mode5Level0() = check(5, 0, 24)
    @Test fun mode5Level1() = check(5, 1, 25)
    @Test fun mode5Level2() = check(5, 2, 26)
    @Test fun mode5Level125() = check(5, 125, 27)
    @Test fun mode5Level126() = check(5, 126, 28)
    @Test fun mode5Level127() = check(5, 127, 29)
    @Test fun mode6Level0() = check(6, 0, 30)
    @Test fun mode6Level1() = check(6, 1, 31)
    @Test fun mode6Level2() = check(6, 2, 32)
    @Test fun mode6Level125() = check(6, 125, 33)
    @Test fun mode6Level126() = check(6, 126, 34)
    @Test fun mode6Level127() = check(6, 127, 35)
    private fun check(mode: Int, level: Int, fixture: Int) {
        // Captured independently from public ICU4J 78.3 setPara, including
        // classifier calls and every partially mutated field on source failures.
        val expected = longArrayOf(
-6020244736645680718L,
6871363134778463507L,
5500378530559481528L,
-733975365526905581L,
-4753185064342751555L,
-6578463819613399659L,
-739905510697746967L,
-515628895954399088L,
2455841703852706544L,
1342581722936344324L,
-6419254417880932021L,
8470122272648402467L,
3551687411105316337L,
6362288589662398654L,
-3140700286672902703L,
2343975727675154639L,
-902807009109850079L,
6053143242539069736L,
-8037304260561513465L,
-3177338212164327320L,
-2031433532705168982L,
-8048813973320294063L,
8345109370154718385L,
-6398046275895442678L,
-5413963238235117903L,
7152794908519227463L,
-5754781779185415170L,
-957386480740851586L,
5732541925992464294L,
2145788902628970333L,
5292239464486499146L,
-6163483928345407998L,
7091033405882863859L,
1391243731979326973L,
5549512326495762163L,
-6061920275824701005L,
)
        val texts = listOf("abc", "אבג", "a(אב)12 ب٣ z", "ا 12 א 34 ا 56 a", "a\r\nאב\na\u2029ب",
            "a\u202bא(12)\u202c z", "\u202da(אב)\u202cب", "a\u2067אב \u2066x\u2069z\u2069 q", "\u2069א", "a\u2068א\u2069",
            "\u0300א\u0301 12", "😀𞤀𞸀\ud800X\udc00", "א a   \n", "a\u200c\u200d\u200e\u200fא\u202c ",
            "\u2067".repeat(140) + "aא" + "\u2069".repeat(145), "ا 12 a 34 אב 56 a .".repeat(8))
        val prologues = listOf(null, "", "a", "א", "ا\n1", "😀𞤀", "\ud800א\udc00")
        val epilogues = listOf("", null, "a", "א", "12", "٣", "\nא", "😀𞸀", "\u0000\u2068")
        var hash = -3750763034362895579L
        fun mix(value: Int) { hash = (hash xor value.toLong()) * 1099511628211L }
        for (options in listOf(0, 1, 2, 3, 4, 6)) for ((t, text) in texts.withIndex()) for (profile in 0..4) {
            val index = t + mode + level + options + profile
            val base = BidiPropertyFixture(text, level, mode, BidiReorderingCore.normalizeReorderingOptions(options), mode % 2 == 0,
                prologues[index % prologues.size]?.takeIf { it.isNotEmpty() }) { mix(it); BidiPropertyCore.customizedClass(it, null) }
            val state = BidiImplicitFixture(base, epilogues[index % epilogues.size]?.takeIf { it.isNotEmpty() })
            val embeddings = if (profile == 0) null else ByteArray(if (profile == 3) text.length / 2 else text.length) {
                if (profile == 2) (if (it % 2 == 0) 3 else 131).toByte() else 0
            }.also { if (profile == 4) it[it.lastIndex] = 126 }
            try { state.resolveFresh(embeddings); mix(0) }
            catch (e: IndexOutOfBoundsException) { mix(1) }
            catch (e: NullPointerException) { mix(2) }
            catch (e: IllegalArgumentException) { mix(3); mix(e.message!!.hashCode()) }
            for (v in listOf(base.length, base.paraCount, base.flags, base.paraLevel.toInt(), base.lastArabicPos, base.controlCount, base.isolateCount, state.trailingWSStart)) mix(v)
            mix(base.paraLimits.size); for (v in base.paraLimits) mix(v)
            for (a in listOf(base.paraLevels, base.dirProps, base.levels)) { mix(a.size); for (v in a) mix(v.toInt()) }
            val points = state.insertPoints
            mix(points.size); mix(points.confirmed); mix(points.points.size)
            for (point in points.points) {
                mix(if (point == null) 0 else 1)
                if (point != null) { mix(point.pos); mix(point.flag) }
            }
            val isolates = state.isolates
            if (isolates == null) mix(-1) else {
                mix(isolates.size)
                for (isolate in isolates) {
                    mix(if (isolate == null) 0 else 1)
                    if (isolate != null) { mix(isolate.startON); mix(isolate.start1); mix(isolate.stateImp.toInt()); mix(isolate.state.toInt()) }
                }
            }
        }
        assertEquals(expected[fixture], hash, "mode=$mode level=$level")
    }
    @Test fun retainedIsolateStateKeepsSourceFailureKind() {
        val first = BidiImplicitFixture(BidiPropertyFixture("a\u2067א\u2069b", 0, 0, 0, false))
        first.resolveFresh(null)
        val old = first.isolates
        assertNotNull(old)
        val reused = BidiImplicitFixture(BidiPropertyFixture("\u2069אabc", 0, 5, 1, false))
        reused.isolates = old
        assertFailsWith<IndexOutOfBoundsException> { reused.resolveFresh(null) }
        assertSame(old, reused.isolates)
        assertEquals(-1, reused.isolateCount)
        val fresh = BidiImplicitFixture(BidiPropertyFixture("\u2069אabc", 0, 5, 1, false))
        assertFailsWith<NullPointerException> { fresh.resolveFresh(null) }
    }
    @Test fun insertionStorageRetainsSourceGrowthAndStaleSlots() {
        val first = BidiImplicitFixture(BidiPropertyFixture("ا 12 a 34 אב 56 a .".repeat(8), 0, 5, 1, false))
        first.resolveFresh(null)
        assertEquals(16, first.insertPoints.size)
        assertEquals(20, first.insertPoints.points.size)
        val old = first.insertPoints.points
        val contents = old.map { it?.let { it.pos to it.flag } }
        val reused = BidiImplicitFixture(BidiPropertyFixture("abc", 0, 5, 1, false))
        reused.insertPoints.points = old
        reused.resolveFresh(null)
        assertSame(old, reused.insertPoints.points)
        assertEquals(contents, reused.insertPoints.points.map { it?.let { it.pos to it.flag } })
        assertEquals(0, reused.insertPoints.size)
        assertEquals(0, reused.insertPoints.confirmed)
    }
    @Test fun optionNormalizationPreservesUnknownBits() {
        assertEquals(2, BidiReorderingCore.normalizeReorderingOptions(3))
        assertEquals(6, BidiReorderingCore.normalizeReorderingOptions(7))
        assertEquals(-2, BidiReorderingCore.normalizeReorderingOptions(-1))
        assertEquals(Int.MIN_VALUE, BidiReorderingCore.normalizeReorderingOptions(Int.MIN_VALUE))
    }
}
