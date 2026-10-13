package io.github.heapy.icu4k.lang

import kotlin.test.*

class UScriptMetadataTest {
    @Test fun sampleUsageAndFlagsAreAvailableOnCommonTargets() {
        assertEquals("L", UScript.getSampleString(UScript.LATIN))
        assertEquals(UScript.ScriptUsage.RECOMMENDED, UScript.getUsage(UScript.LATIN))
        assertTrue(UScript.isCased(UScript.LATIN))
        assertTrue(UScript.isRightToLeft(UScript.ARABIC))
        assertTrue(UScript.breaksBetweenLetters(UScript.THAI))
        assertFalse(UScript.breaksBetweenLetters(UScript.LATIN))
        assertEquals("", UScript.getSampleString(-1))
        assertEquals(UScript.ScriptUsage.NOT_ENCODED, UScript.getUsage(Int.MAX_VALUE))
    }
}
