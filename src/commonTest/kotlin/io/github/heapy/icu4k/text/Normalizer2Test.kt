package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.ICUBinary
import kotlin.test.*

class Normalizer2Test {
    @Test fun standardFormsAndQuickChecks() {
        val nfc = Normalizer2.getNFCInstance()
        assertSame(nfc, Normalizer2.getInstance(null, "nfc", Normalizer2.Mode.COMPOSE))
        assertEquals("é", nfc.normalize("e\u0301"))
        assertEquals("e\u0301", Normalizer2.getNFDInstance().normalize("é"))
        assertEquals("ffi", Normalizer2.getNFKCInstance().normalize("ﬃ"))
        assertEquals("ffi", Normalizer2.getNFKDInstance().normalize("ﬃ"))
        assertEquals("strasse", Normalizer2.getNFKCCasefoldInstance().normalize("Straße"))
        assertEquals("straße", Normalizer2.getNFKCSimpleCasefoldInstance().normalize("Straße"))
        assertSame(Normalizer.MAYBE, nfc.quickCheck("e\u0301"))
        assertFalse(nfc.isNormalized("e\u0301"))
        assertSame(Normalizer.YES, nfc.quickCheck("é"))
        assertSame(Normalizer.NO, Normalizer2.getNFDInstance().quickCheck("é"))
        assertEquals(230, nfc.getCombiningClass(0x301))
        assertEquals(0xe9, nfc.composePair(101, 0x301))
    }
    @Test fun destinationsAliasingAndCustomData() {
        val nfc = Normalizer2.getNFCInstance()
        val dest = StringBuilder("old")
        assertSame(dest, nfc.normalize("e\u0301", dest))
        assertEquals("é", dest.toString())
        val appended = StringBuilder("prefix:")
        val output = object : Appendable {
            override fun append(value: CharSequence?): Appendable { appended.append(value); return this }
            override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable { appended.append(value, startIndex, endIndex); return this }
            override fun append(value: Char): Appendable { appended.append(value); return this }
        }
        assertSame(output, nfc.normalize("e\u0301", output))
        assertEquals("prefix:é", appended.toString())
        for (noop in listOf(nfc, Norm2AllModes.NOOP_NORMALIZER2)) {
            assertFailsWith<IllegalArgumentException> { noop.normalize(dest, dest) }
            assertFailsWith<IllegalArgumentException> { noop.normalize(dest, dest as Appendable) }
            assertFailsWith<IllegalArgumentException> { noop.normalizeSecondAndAppend(dest, dest) }
            assertFailsWith<IllegalArgumentException> { noop.append(dest, dest) }
        }
        val first = StringBuilder("e")
        assertSame(first, nfc.normalizeSecondAndAppend(first, "\u0301"))
        assertEquals("é", first.toString())
        val custom = Normalizer2.getInstance(ICUBinary.getRequiredData("nfc.nrm"), "test-custom-nfc", Normalizer2.Mode.COMPOSE)
        assertEquals("é", custom.normalize("e\u0301"))
        assertSame(custom, Normalizer2.getInstance(null, "test-custom-nfc", Normalizer2.Mode.COMPOSE))
    }
}
