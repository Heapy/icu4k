package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UCharacter as JavaCharacter
import kotlin.test.*

class UCharacterOracleTest {
    @Test fun characterPredicatesMatchIcuOverAllUnicode() {
        val predicates = listOf<Pair<(Int) -> Boolean, (Int) -> Boolean>>(
            JavaCharacter::isDefined to UCharacter::isDefined,
            JavaCharacter::isDigit to UCharacter::isDigit,
            JavaCharacter::isISOControl to UCharacter::isISOControl,
            JavaCharacter::isLetter to UCharacter::isLetter,
            JavaCharacter::isLetterOrDigit to UCharacter::isLetterOrDigit,
            JavaCharacter::isLowerCase to UCharacter::isLowerCase,
            JavaCharacter::isUpperCase to UCharacter::isUpperCase,
            JavaCharacter::isTitleCase to UCharacter::isTitleCase,
            JavaCharacter::isWhitespace to UCharacter::isWhitespace,
            JavaCharacter::isSpaceChar to UCharacter::isSpaceChar,
            JavaCharacter::isIdentifierIgnorable to UCharacter::isIdentifierIgnorable,
            JavaCharacter::isUnicodeIdentifierStart to UCharacter::isUnicodeIdentifierStart,
            JavaCharacter::isUnicodeIdentifierPart to UCharacter::isUnicodeIdentifierPart,
            JavaCharacter::isSupplementary to UCharacter::isSupplementary,
            JavaCharacter::isBMP to UCharacter::isBMP,
            JavaCharacter::isPrintable to UCharacter::isPrintable,
            JavaCharacter::isBaseForm to UCharacter::isBaseForm,
            { cp: Int -> JavaCharacter.isLegal(cp) } to { cp: Int -> UCharacter.isLegal(cp) },
        )
        for ((index, pair) in predicates.withIndex()) for (cp in -1..0x110000) {
            if (pair.first(cp) != pair.second(cp)) fail("Predicate $index U+${cp.toString(16)}")
        }
        for (cp in -1..0x110000) {
            if (JavaCharacter.toString(cp) != UCharacter.toString(cp)) fail("String U+${cp.toString(16)}")
            if (JavaCharacter.getDirectionality(cp) != UCharacter.getDirectionality(cp)) fail("Direction U+${cp.toString(16)}")
        }
    }

    @Test fun digitsMatchIcuForEveryRadixAndBoundary() {
        val digits = (0..0xffff).filter { JavaCharacter.digit(it) >= 0 || it in 'A'.code..'Z'.code || it in 'a'.code..'z'.code || it in 0xff21..0xff5a }
        for (cp in digits + listOf(-1, 0x110000, 0x1d7ce, 0x1f600)) for (radix in -1..38) {
            assertEquals(JavaCharacter.digit(cp, radix), UCharacter.digit(cp, radix))
        }
    }
}
