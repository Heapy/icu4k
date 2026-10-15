// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*

class UnicodeSetInvalidCodePointTest {
    @Test fun signedHexDiagnosticsRetainSignAndMinimumPadding() {
        for ((codePoint, hex) in listOf(-1 to "-000001", -2 to "-000002", Int.MIN_VALUE to "-80000000", 0x110000 to "110000", Int.MAX_VALUE to "7FFFFFFF")) {
            val expected = "Invalid code point U+$hex"
            assertEquals(expected, assertFailsWith<IllegalArgumentException> { UnicodeSet().contains(codePoint) }.message)
            assertEquals(expected, assertFailsWith<IllegalArgumentException> { UnicodeSet().add(codePoint) }.message)
            assertEquals(expected, assertFailsWith<IllegalArgumentException> { UnicodeSet().remove(codePoint) }.message)
            assertEquals(expected, assertFailsWith<IllegalArgumentException> { UnicodeSet().complement(codePoint) }.message)
        }
    }
}
