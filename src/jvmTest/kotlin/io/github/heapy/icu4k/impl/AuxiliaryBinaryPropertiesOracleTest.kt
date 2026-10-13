package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class AuxiliaryBinaryPropertiesOracleTest {
    @Test fun auxiliaryEntriesMatchAllUnicodeCodePoints() {
        val properties = listOf(UProperty.LOWERCASE, UProperty.SOFT_DOTTED, UProperty.UPPERCASE, UProperty.CASE_SENSITIVE, UProperty.CASED, UProperty.CASE_IGNORABLE, UProperty.CHANGES_WHEN_LOWERCASED, UProperty.CHANGES_WHEN_UPPERCASED, UProperty.CHANGES_WHEN_TITLECASED, UProperty.CHANGES_WHEN_CASEMAPPED, UProperty.BIDI_CONTROL, UProperty.BIDI_MIRRORED, UProperty.JOIN_CONTROL, UProperty.REGIONAL_INDICATOR, UProperty.IDS_UNARY_OPERATOR, UProperty.ID_COMPAT_MATH_START, UProperty.ID_COMPAT_MATH_CONTINUE, UProperty.MODIFIER_COMBINING_MARK)
        for (which in properties) for (cp in -1..0x110000) assertEquals(JavaCharacter.hasBinaryProperty(cp, which), UCharacterProperty.INSTANCE.hasAuxiliaryBinaryProperty(cp, which), "$which $cp")
    }
}
