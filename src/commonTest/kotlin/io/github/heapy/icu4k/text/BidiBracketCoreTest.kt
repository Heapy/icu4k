// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter
import kotlin.test.*

class BidiBracketCoreTest {
    // Keep all 56 source fingerprints in smaller Node test cases.
    @Test fun intermediateBracketStatesMode0() = verifyIntermediateBracketStates(0)
    @Test fun intermediateBracketStatesMode1() = verifyIntermediateBracketStates(1)
    @Test fun intermediateBracketStatesMode2() = verifyIntermediateBracketStates(2)
    @Test fun intermediateBracketStatesMode3() = verifyIntermediateBracketStates(3)
    @Test fun intermediateBracketStatesMode4() = verifyIntermediateBracketStates(4)
    @Test fun intermediateBracketStatesMode5() = verifyIntermediateBracketStates(5)
    @Test fun intermediateBracketStatesMode6() = verifyIntermediateBracketStates(6)
    private fun verifyIntermediateBracketStates(mode: Int) {
        val texts = listOf("", "abc", "אבג", "a(אב)z", "א(a)ב", "ا[12(א)]a", "a((ב))z", "א[(a)ב]c",
            "a\u2329ב\u3009", "א\u3008a\u232a", "[\u0300a]\u0300", "((א)(a))", "(אב[cd]12)",
            "(".repeat(100) + "אב12a" + ")".repeat(100), "a\u0000(ב)")
        // ICU4J 78.3 private bracket methods, captured independently after each step.
        val expected = longArrayOf(
1117229689340764729L,
865448851241457797L,
4757113553184830051L,
9067480341441856167L,
2837172658043793700L,
5453756187620757801L,
-8068511278394732050L,
-5154362312487159917L,
-1520860521474118639L,
7534417676631191601L,
-7021248098520048553L,
6825447756638367615L,
2933740286358104257L,
7649057843850887668L,
2348928413347396723L,
5411425276518113062L,
1117229689340764729L,
865448851241457797L,
4757113553184830051L,
9067480341441856167L,
2837172658043793700L,
5453756187620757801L,
-8068511278394732050L,
-5154362312487159917L,
1117229689340764729L,
865448851241457797L,
4757113553184830051L,
9067480341441856167L,
2837172658043793700L,
5453756187620757801L,
-8068511278394732050L,
-5154362312487159917L,
1117229689340764729L,
865448851241457797L,
4757113553184830051L,
9067480341441856167L,
2837172658043793700L,
5453756187620757801L,
-8068511278394732050L,
-5154362312487159917L,
1117229689340764729L,
865448851241457797L,
4757113553184830051L,
9067480341441856167L,
2837172658043793700L,
5453756187620757801L,
-8068511278394732050L,
-5154362312487159917L,
-1520860521474118639L,
7534417676631191601L,
-7021248098520048553L,
6825447756638367615L,
2933740286358104257L,
7649057843850887668L,
2348928413347396723L,
5411425276518113062L,
)
        var fixture = mode * 8
        for (level in listOf(0, 1, 2, 3, -128, -127, -126, -125)) {
            var hash = -3750763034362895579L
            fun mix(value: Int) { hash = (hash xor value.toLong()) * 1099511628211L }
            for (text in texts) {
                val input = object : BidiBracketCore.Input {
                    override val text = text.toCharArray()
                    override val dirProps = ByteArray(text.length) { UCharacter.getDirection(text[it].code).toByte() }
                    override val levels = ByteArray(text.length) { level.toByte() }
                    override val reorderingMode = mode
                    override fun getParaLevelAt(index: Int) = level.toByte()
                }
                val core = BidiBracketCore(input)
                fun snapshot() {
                    mix(input.dirProps.size)
                    for (v in input.dirProps) mix(v.toInt())
                    for (v in input.levels) mix(v.toInt())
                    mix(core.isoRunLast); mix(if (core.isNumbersSpecial) 1 else 0)
                    mix(core.openings.size)
                    for (o in core.openings) {
                        mix(if (o == null) 0 else 1)
                        if (o != null) {
                            mix(o.position); mix(o.match); mix(o.contextPos); mix(o.flags.toInt()); mix(o.contextDir.toInt())
                        }
                    }
                    mix(core.isoRuns.size)
                    for (r in core.isoRuns) {
                        mix(if (r == null) 0 else 1)
                        if (r != null) {
                            mix(r.contextPos); mix(r.start.toInt()); mix(r.limit.toInt()); mix(r.level.toInt())
                            mix(r.lastStrong.toInt()); mix(r.lastBase.toInt()); mix(r.contextDir.toInt())
                        }
                    }
                }
                core.init(); snapshot()
                for (i in text.indices) { core.processChar(i); snapshot() }
            }
            assertEquals(expected[fixture++], hash, "mode=$mode level=$level")
        }
    }
    @Test fun pairedBracketPropertyConstantsMatchData() {
        assertEquals(UCharacter.BidiPairedBracketType.NONE, UCharacter.getIntPropertyValue('a'.code, io.github.heapy.icu4k.lang.UProperty.BIDI_PAIRED_BRACKET_TYPE))
        assertEquals(UCharacter.BidiPairedBracketType.OPEN, UCharacter.getIntPropertyValue('('.code, io.github.heapy.icu4k.lang.UProperty.BIDI_PAIRED_BRACKET_TYPE))
        assertEquals(UCharacter.BidiPairedBracketType.CLOSE, UCharacter.getIntPropertyValue(')'.code, io.github.heapy.icu4k.lang.UProperty.BIDI_PAIRED_BRACKET_TYPE))
    }
}
