package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.Normalizer2Impl as JavaImpl
import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class CanonicalDataOracleTest {
    @Test fun everyStarterAndStartSetMatchesOriginal() {
        val j = JavaImpl().load("nfc.nrm").ensureCanonIterData()
        val k = Normalizer2Impl().load("nfc.nrm").ensureCanonIterData()
        val js = JavaSet(); val ks = UnicodeSet()
        for (c in 0..0x10ffff) {
            if (j.isCanonSegmentStarter(c) != k.isCanonSegmentStarter(c)) fail("starter $c")
            js.clear().add(0x10ffff); ks.clear().add(0x10ffff)
            val expected = j.getCanonStartSet(c, js)
            val actual = k.getCanonStartSet(c, ks)
            if (expected != actual) fail("start set $c")
            assertEquals(js.getRangeCount(), ks.getRangeCount(), "ranges $c")
            for (r in 0 until js.getRangeCount()) {
                assertEquals(js.getRangeStart(r), ks.getRangeStart(r), "start $c $r")
                assertEquals(js.getRangeEnd(r), ks.getRangeEnd(r), "end $c $r")
            }
        }
        js.clear(); ks.clear()
        j.addCanonIterPropertyStarts(js); k.addCanonIterPropertyStarts(ks)
        assertEquals(js.toPattern(true), ks.toPattern(true))
        for (c in listOf(-1, 0x110000, Int.MIN_VALUE, Int.MAX_VALUE)) assertEquals(j.isCanonSegmentStarter(c), k.isCanonSegmentStarter(c))
    }
}
