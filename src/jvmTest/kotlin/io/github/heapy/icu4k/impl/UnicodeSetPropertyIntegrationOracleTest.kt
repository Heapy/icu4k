package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCaseProps as JavaCases
import com.ibm.icu.impl.UBiDiProps as JavaBidi
import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.random.Random
import kotlin.test.*

class UnicodeSetPropertyIntegrationOracleTest {
    private fun compare(j: JavaSet, k: UnicodeSet) {
        assertEquals(j.size(), k.size()); assertEquals(j.rangeCount, k.getRangeCount())
        for (i in 0 until j.rangeCount) { assertEquals(j.getRangeStart(i), k.getRangeStart(i)); assertEquals(j.getRangeEnd(i), k.getRangeEnd(i)) }
        assertEquals(j.strings().toList(), k.strings().toList())
    }
    @Test fun caseClosuresAppendToExistingSets() {
        val random = Random(767)
        for (cp in (0..0x10ffff).filter { UCaseProps.INSTANCE.getType(it) != UCaseProps.NONE } + List(1000) { random.nextInt(0x110000) }) {
            val j = JavaSet(0, 1).add("existing")
            val k = UnicodeSet(0, 1).add("existing")
            JavaCases.INSTANCE.addCaseClosure(cp, j); UCaseProps.INSTANCE.addCaseClosure(cp, k)
            compare(j, k)
            j.clear(); k.clear()
            JavaCases.INSTANCE.addSimpleCaseClosure(cp, j); UCaseProps.INSTANCE.addSimpleCaseClosure(cp, k)
            compare(j, k)
        }
        for (str in listOf(null, "", "s", "ss", "ffi", "i\u0307", "st", "\u0565\u0582", "unknown")) {
            val j = JavaSet().add("existing"); val k = UnicodeSet().add("existing")
            assertEquals(JavaCases.INSTANCE.addStringCaseClosure(str, j), UCaseProps.INSTANCE.addStringCaseClosure(str, k))
            compare(j, k)
        }
    }
    @Test fun allCaseAndBidiPropertyBoundariesMatch() {
        val j = JavaSet().add("existing"); val k = UnicodeSet().add("existing")
        JavaCases.INSTANCE.addPropertyStarts(j); UCaseProps.INSTANCE.addPropertyStarts(k); compare(j, k)
        j.clear(); k.clear()
        JavaBidi.INSTANCE.addPropertyStarts(j); UBiDiProps.INSTANCE.addPropertyStarts(k); compare(j, k)
    }
}
