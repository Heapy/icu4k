package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class NormalizationPropertiesOracleTest {
    @Test fun allScalarAndSurrogateValuesMatchOriginal() {
        val p = UCharacterProperty.INSTANCE
        val ints = listOf(UProperty.CANONICAL_COMBINING_CLASS, UProperty.LEAD_CANONICAL_COMBINING_CLASS, UProperty.TRAIL_CANONICAL_COMBINING_CLASS,
            UProperty.NFD_QUICK_CHECK, UProperty.NFKD_QUICK_CHECK, UProperty.NFC_QUICK_CHECK, UProperty.NFKC_QUICK_CHECK)
        val binaries = listOf(UProperty.FULL_COMPOSITION_EXCLUSION, UProperty.NFD_INERT, UProperty.NFKD_INERT, UProperty.NFC_INERT, UProperty.NFKC_INERT,
            UProperty.CHANGES_WHEN_CASEFOLDED, UProperty.CHANGES_WHEN_NFKC_CASEFOLDED)
        for (which in ints) {
            assertEquals(JavaCharacter.getIntPropertyMaxValue(which), p.getNormalizationIntPropertyMaxValue(which))
            for (c in 0..0x10ffff) {
                val expected = JavaCharacter.getIntPropertyValue(c, which)
                val actual = p.getNormalizationIntPropertyValue(c, which)
                if (expected != actual) fail("int property=$which c=$c expected=$expected actual=$actual")
            }
        }
        for (which in binaries) for (c in 0..0x10ffff) {
            val expected = JavaCharacter.hasBinaryProperty(c, which)
            val actual = p.hasNormalizationBinaryProperty(c, which)
            if (expected != actual) fail("binary property=$which c=$c expected=$expected actual=$actual")
        }
    }
}
