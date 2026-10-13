package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.Freezable
import kotlin.test.*

class UnicodeSetFreezableTest {
    @Test fun genericFreezableRetainsIndependentThawedCopies() {
        val set = UnicodeSet().add(65).add("abc")
        val contract: Freezable<UnicodeSet> = set
        assertFalse(contract.isFrozen())
        assertSame(set, contract.freeze())
        assertTrue(contract.isFrozen())
        assertSame(set, contract.freeze())
        val copy = contract.cloneAsThawed()
        assertFalse(copy.isFrozen())
        copy.remove(65).add("xyz")
        assertTrue(set.contains(65))
        assertFalse(set.contains("xyz"))
        assertFailsWith<UnsupportedOperationException> { set.add("xyz") }
    }
}
