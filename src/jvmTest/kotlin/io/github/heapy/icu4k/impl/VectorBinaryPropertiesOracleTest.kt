package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.random.Random
import kotlin.test.*

class VectorBinaryPropertiesOracleTest {
    @Test fun storedBitsMatchOriginalBinaryDispatcher() {
        val properties = listOf(UProperty.ALPHABETIC, UProperty.ASCII_HEX_DIGIT, UProperty.DASH, UProperty.DEFAULT_IGNORABLE_CODE_POINT, UProperty.DEPRECATED, UProperty.DIACRITIC, UProperty.EXTENDER, UProperty.GRAPHEME_BASE, UProperty.GRAPHEME_EXTEND, UProperty.GRAPHEME_LINK, UProperty.HEX_DIGIT, UProperty.HYPHEN, UProperty.ID_CONTINUE, UProperty.ID_START, UProperty.IDEOGRAPHIC, UProperty.IDS_BINARY_OPERATOR, UProperty.IDS_TRINARY_OPERATOR, UProperty.LOGICAL_ORDER_EXCEPTION, UProperty.MATH, UProperty.NONCHARACTER_CODE_POINT, UProperty.QUOTATION_MARK, UProperty.RADICAL, UProperty.TERMINAL_PUNCTUATION, UProperty.UNIFIED_IDEOGRAPH, UProperty.WHITE_SPACE, UProperty.XID_CONTINUE, UProperty.XID_START, UProperty.S_TERM, UProperty.VARIATION_SELECTOR, UProperty.PATTERN_SYNTAX, UProperty.PATTERN_WHITE_SPACE, UProperty.PREPENDED_CONCATENATION_MARK)
        val random = Random(348)
        for (which in properties) for (cp in listOf(-1, 0, 65, 0xd800, 0xffff, 0x10ffff, 0x110000, Int.MAX_VALUE) + List(20000) { random.nextInt(0x110000) }) {
            assertEquals(JavaCharacter.hasBinaryProperty(cp, which), UCharacterProperty.INSTANCE.hasVectorBinaryProperty(cp, which), "$which $cp")
        }
    }
}
