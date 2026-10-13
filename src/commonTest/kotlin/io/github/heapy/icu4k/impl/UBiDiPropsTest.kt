package io.github.heapy.icu4k.impl

import kotlin.test.*

class UBiDiPropsTest {
    @Test fun loadsBidiAndJoiningProperties() {
        val props = UBiDiProps.INSTANCE
        assertEquals(0, props.getClass('A'.code))
        assertEquals(1, props.getClass(0x05d0))
        assertEquals(13, props.getClass(0x0627))
        assertTrue(props.isMirrored('('.code))
        assertEquals(')'.code, props.getMirror('('.code))
        assertEquals(1, props.getPairedBracketType('('.code))
        assertEquals(')'.code, props.getPairedBracket('('.code))
        assertEquals('A'.code, props.getMirror('A'.code))
        assertTrue(props.isBidiControl(0x202e))
        assertTrue(props.isJoinControl(0x200d))
        assertEquals(0, props.getJoiningGroup('A'.code))
    }
}
