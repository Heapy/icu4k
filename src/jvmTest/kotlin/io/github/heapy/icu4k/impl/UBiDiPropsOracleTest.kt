package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UBiDiProps as JavaBidi
import com.ibm.icu.impl.ICUBinary as JavaBinary
import com.ibm.icu.text.UnicodeSet
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class UBiDiPropsOracleTest {
    @Test fun allBidiShapingAndMirrorPropertiesMatchIcu() {
        val expected = JavaBidi.INSTANCE
        val actual = UBiDiProps.INSTANCE
        for (c in -1..0x110000) {
            if (expected.getClass(c) != actual.getClass(c)) fail("Bidi class U+${c.toString(16)}")
            if (expected.isMirrored(c) != actual.isMirrored(c)) fail("Mirrored U+${c.toString(16)}")
            if (expected.getMirror(c) != actual.getMirror(c)) fail("Mirror U+${c.toString(16)}")
            if (expected.isBidiControl(c) != actual.isBidiControl(c)) fail("Bidi control U+${c.toString(16)}")
            if (expected.isJoinControl(c) != actual.isJoinControl(c)) fail("Join control U+${c.toString(16)}")
            if (expected.getJoiningType(c) != actual.getJoiningType(c)) fail("Joining type U+${c.toString(16)}")
            if (expected.getJoiningGroup(c) != actual.getJoiningGroup(c)) fail("Joining group U+${c.toString(16)}")
            if (expected.getPairedBracketType(c) != actual.getPairedBracketType(c)) fail("Bracket type U+${c.toString(16)}")
            if (expected.getPairedBracket(c) != actual.getPairedBracket(c)) fail("Paired bracket U+${c.toString(16)}")
        }
        for (which in listOf(-1, UProperty.BIDI_CLASS, UProperty.JOINING_GROUP, UProperty.JOINING_TYPE, UProperty.BIDI_PAIRED_BRACKET_TYPE)) {
            assertEquals(expected.getMaxValue(which), actual.getMaxValue(which))
        }
        val expectedStarts = UnicodeSet().also { expected.addPropertyStarts(it) }
        val actualStarts = UnicodeSet().also { set -> actual.forEachPropertyStart { set.add(it) } }
        assertEquals(expectedStarts, actualStarts)
        val expectedBytes = JavaBinary.getRequiredData("ubidi.icu")
        val bytes = ByteArray(expectedBytes.remaining()).also { expectedBytes.get(it) }
        assertContentEquals(bytes, ICUData.getBytes("ubidi.icu"))
    }
}
