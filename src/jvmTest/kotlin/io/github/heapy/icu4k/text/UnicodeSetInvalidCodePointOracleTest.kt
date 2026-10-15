// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import kotlin.test.*
import com.ibm.icu.text.UnicodeSet as Source

class UnicodeSetInvalidCodePointOracleTest {
    @Test fun signedInvalidCodePointMessagesMatchAcrossMutationAndQueries() {
        for (c in listOf(Int.MIN_VALUE, -0x110000, -2, -1, 0x110000, Int.MAX_VALUE)) {
            assertEquals(message { Source().contains(c) }, message { UnicodeSet().contains(c) })
            assertEquals(message { Source().add(c) }, message { UnicodeSet().add(c) })
            assertEquals(message { Source().remove(c) }, message { UnicodeSet().remove(c) })
            assertEquals(message { Source().complement(c) }, message { UnicodeSet().complement(c) })
        }
    }
    private fun message(block: () -> Any?): String? = assertFailsWith<IllegalArgumentException> { block() }.message
}
