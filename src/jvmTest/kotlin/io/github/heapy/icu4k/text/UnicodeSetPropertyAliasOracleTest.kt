package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class UnicodeSetPropertyAliasOracleTest {
    private fun compare(p: String, v: String?) {
        val js = JavaSet().add("old"); val ks = UnicodeSet().add("old")
        val j = runCatching { js.applyPropertyAlias(p, v) }
        val k = runCatching { ks.applyPropertyAlias(p, v) }
        assertEquals(j.isSuccess, k.isSuccess, "$p=$v")
        if (j.isSuccess) {
            assertEquals(js.getRangeCount(), ks.getRangeCount(), "$p=$v count")
            for (i in 0 until js.getRangeCount()) { assertEquals(js.getRangeStart(i), ks.getRangeStart(i)); assertEquals(js.getRangeEnd(i), ks.getRangeEnd(i)) }
            assertEquals(js.strings().toSet(), ks.strings().toSet())
        } else assertTrue(k.exceptionOrNull() is IllegalArgumentException, "$p=$v")
    }
    @Test fun supportedAliasFormsAndFailuresMatchOriginal() {
        for (p in 0 until UProperty.BINARY_LIMIT) compare(UCharacter.getPropertyName(p, 1)!!, "Yes")
        for (p in UProperty.INT_START until UProperty.INT_LIMIT) {
            for (value in listOf(0, UCharacter.getIntPropertyMaxValue(p))) {
                UCharacter.getPropertyValueName(p, value, 1)?.let { compare(UCharacter.getPropertyName(p, 1)!!, it) }
            }
        }
        for ((p, v) in listOf("gc" to "L", "General_Category" to "Lu", "ccc" to "254", "lccc" to "256", "ccc" to "nonsense", "nv" to "0.5", "nv" to "0x1p-1D", "nv" to "NaN", "nv" to "Infinity", "nv" to "1.2x", "age" to "1.1", "age" to "17.0", "age" to "0", "name" to "LATIN  CAPITAL\tLETTER A", "name" to "<control-0000>", "name" to "not a name", "na1" to "A", "scx" to "Latn", "Identifier_Type" to "Recommended", "bad" to "bad", "sc" to "")) compare(p, v)
        for (p in listOf("L", "Latin", "Alphabetic", "ASCII", "A_n-y", "Assigned", "bad", "age")) {
            compare(p, ""); compare(p, null)
        }
    }
}
