package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UScript as JavaScript
import kotlin.test.*

class UScriptMetadataOracleTest {
    @Test fun everyScriptMetadataEntryMatchesIcu() {
        for (script in (-2..UScript.CODE_LIMIT + 2).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(JavaScript.getSampleString(script), UScript.getSampleString(script))
            assertEquals(JavaScript.getUsage(script).name, UScript.getUsage(script).name)
            assertEquals(JavaScript.isRightToLeft(script), UScript.isRightToLeft(script))
            assertEquals(JavaScript.breaksBetweenLetters(script), UScript.breaksBetweenLetters(script))
            assertEquals(JavaScript.isCased(script), UScript.isCased(script))
        }
    }
}
