// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacterDirection as D
import kotlin.test.*

// Keep lone surrogate fixtures as Char values: cached JS string literals can
// emit '?' instead, silently changing both inputs and expected output strings.
class BidiPropertyCoreTest {
    @Test fun sourcePreparationAndExplicitMode0() = checkMode(0)
    @Test fun sourcePreparationAndExplicitMode1() = checkMode(1)
    @Test fun sourcePreparationAndExplicitMode2() = checkMode(2)
    @Test fun sourcePreparationAndExplicitMode3() = checkMode(3)
    @Test fun sourcePreparationAndExplicitMode4() = checkMode(4)
    @Test fun sourcePreparationAndExplicitMode5() = checkMode(5)
    @Test fun sourcePreparationAndExplicitMode6() = checkMode(6)
    private fun checkMode(mode: Int) {
        // ICU4J 78.3 private getDirProps + resolveExplicitLevels. Includes errors
        // and their partially mutated state, and every classifier callback.
        val expected = longArrayOf(
7617505901701005564L,
-3134046948722697766L,
-7568432664376620160L,
-8297563587484158185L,
3921954447413296047L,
-2391536446930182788L,
2329319873394296081L,
)
        val texts = listOf("a", "אב", "ابت", "12(אב) a", "😀𞤀𞸀" + charArrayOf('\ud800', '\udc00', '\ud800', 'X', '\udc00').concatToString(), "a\r\nאב\na\u2029ب",
            "\u2068(אב)\u2069a", "\u2068(abc)\u2069א", "\u2068\u2066א\u2069a\u2069",
            "\u2069\u2068\u2067א\u2069\u2069", "\u2068".repeat(140) + "א" + "\u2069".repeat(145),
            "\u2068".repeat(140) + "a", "a\nא\nب\r\n".repeat(30), "a\u200c\u200d\u200e\u200f\u202a\u202c\u2066\u2069א\n")
        val prologues = listOf(null, "", "א\na", "a\nא", "אב", "١٢", "😀𞤀" + charArrayOf('\ud800', 'א', '\udc00').concatToString())
        var hash = -3750763034362895579L
        fun mix(value: Int) { hash = (hash xor value.toLong()) * 1099511628211L }
        for (level in listOf(0, 1, 2, 125, 126, 127)) for (options in listOf(0, 1, 2, 3, 4, 6)) for ((t, text) in texts.withIndex()) {
            val state = BidiPropertyFixture(text, level, mode, options, mode % 2 == 0, prologues[(t + mode + level + options) % prologues.size]) {
                mix(it); BidiPropertyCore.customizedClass(it, null)
            }
            fun snapshot() {
                for (v in listOf(state.length, state.paraCount, state.flags, state.paraLevel.toInt(), state.lastArabicPos, state.controlCount, state.isolateCount)) mix(v)
                mix(state.paraLimits.size); for (v in state.paraLimits) mix(v)
                for (a in listOf(state.paraLevels, state.dirProps, state.levels)) { mix(a.size); for (v in a) mix(v.toInt()) }
            }
            var succeeded = false
            try { BidiPropertyCore(state).scan(); mix(0); succeeded = true } catch (e: IndexOutOfBoundsException) { mix(1) }
            snapshot()
            if (succeeded) {
                // setPara allocates the level buffer using processed length.
                if (state.levels.size != state.length) state.levels = ByteArray(state.length)
                try { val result = BidiExplicitCore(state).resolve(); mix(0); mix(result.toInt()) } catch (e: IndexOutOfBoundsException) { mix(1) }
                snapshot()
            }
        }
        assertEquals(expected[mode], hash, "mode $mode")
    }
    @Test fun prologueReadsObserveClassifierContextMutation() {
        val calls = mutableListOf<Int>()
        lateinit var state: BidiPropertyFixture
        state = BidiPropertyFixture("12", 126, 0, 0, false, "אלף\nabc") {
            calls += it
            if (calls.size == 1) state.prologue = "b"
            BidiPropertyCore.customizedClass(it, null)
        }
        BidiPropertyCore(state).scan()
        assertEquals(listOf('א'.code, '1'.code, '2'.code), calls)
        assertEquals(1.toByte(), state.paraLevel)
        lateinit var nulled: BidiPropertyFixture
        nulled = BidiPropertyFixture("12", 126, 0, 0, false, "אלף\nabc") {
            nulled.prologue = null
            BidiPropertyCore.customizedClass(it, null)
        }
        assertFailsWith<NullPointerException> { BidiPropertyCore(nulled).scan() }
        assertEquals(0, nulled.flags)
        assertEquals(-1, nulled.lastArabicPos)
    }
    @Test fun inputMutationRetainsBackwardSurrogatePairingAndWriteFailure() {
        val calls = mutableListOf<Int>()
        lateinit var state: BidiPropertyFixture
        state = BidiPropertyFixture(charArrayOf('a', '\udc00').concatToString(), 126, 0, 0, false) {
            calls += it
            if (calls.size == 1) state.text[0] = '\ud800'
            BidiPropertyCore.customizedClass(it, null)
        }
        assertEquals(listOf(0x61, 0xdc00), state.text.map { it.code })
        assertFailsWith<IndexOutOfBoundsException> { BidiPropertyCore(state).scan() }
        assertEquals(listOf('a'.code, 0x10000), calls)
        assertEquals(126.toByte(), state.paraLevel)
        assertEquals(1, state.flags)
        assertContentEquals(byteArrayOf(0, 0), state.dirProps)
    }
    @Test fun customizedClassesRetainSentinelAndNegativeValues() {
        assertEquals(D.RIGHT_TO_LEFT, BidiPropertyCore.customizedClass('א'.code, null))
        for (value in listOf(-257, -1, 0, 22, 23, 24, 256)) {
            val classifier = object : BidiClassifier(null) { override fun classify(c: Int) = value }
            assertEquals(when { value == 23 -> D.RIGHT_TO_LEFT; value >= 23 -> D.OTHER_NEUTRAL; else -> value }, BidiPropertyCore.customizedClass('א'.code, classifier))
        }
    }
}
