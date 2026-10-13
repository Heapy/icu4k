package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UScript as JavaScript
import java.util.BitSet
import kotlin.random.Random
import kotlin.test.*

class UScriptOracleTest {
    @Test fun scriptsAndExtensionsMatchOriginal() {
        val random = Random(173)
        val bits = BitSet(); val set = mutableSetOf<Int>()
        for (cp in -1..0x110000) {
            assertEquals(JavaScript.getScriptExtensions(cp, bits), UScript.getScriptExtensions(cp, set), "$cp result")
            assertEquals(bits.stream().toArray().toSet(), set, "$cp extensions")
            if (cp in 0..0x10ffff) assertEquals(JavaScript.getScript(cp), UScript.getScript(cp))
            val script = random.nextInt(-1, UScript.CODE_LIMIT + 1)
            assertEquals(JavaScript.hasScript(cp, script), UScript.hasScript(cp, script))
        }
        for (script in 0 until UScript.CODE_LIMIT) {
            assertEquals(JavaScript.getName(script), UScript.getName(script))
            assertEquals(JavaScript.getShortName(script), UScript.getShortName(script))
            val name = UScript.getName(script) ?: continue
            assertEquals(JavaScript.getCodeFromName(name), UScript.getCodeFromName(name))
        }
    }
}
