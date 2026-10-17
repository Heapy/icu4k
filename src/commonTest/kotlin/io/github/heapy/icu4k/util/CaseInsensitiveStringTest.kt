// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.test.*

class CaseInsensitiveStringTest {
    @Test fun unicodeKeysRetainOriginalSpelling() {
        for((a,b)in listOf("Straße" to "STRASSE","İ" to "i\u0307","Σςσ" to "σσσ","ﬃ" to "ffi","𐐀" to "𐐨")) {
            val key=CaseInsensitiveString(a);val equal=CaseInsensitiveString(b)
            assertEquals(key,equal);assertEquals(key.hashCode(),equal.hashCode())
            assertEquals(a,key.getString());assertEquals(a,key.toString())
            assertEquals("value",mapOf(key to "value")[equal])
            assertFalse(key.equals(a))
        }
    }
    @Test fun nullSourceValue() {
        val key=CaseInsensitiveString(null)
        assertNull(key.getString());assertNull(key.toString())
        assertTrue(key==key);assertFalse(key.equals(null));assertFalse(key.equals(""))
        assertFailsWith<NullPointerException>{key.hashCode()}
        assertFailsWith<NullPointerException>{key.equals(CaseInsensitiveString("a"))}
    }
}
