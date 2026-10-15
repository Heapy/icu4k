package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class MacroPropsCloneTest {
    private class Derived : MacroProps() { val extra = Any(); var scalar = 8 }
    @Test fun clonePreservesSubtypeAndExtraFields() {
        val original = Derived()
        val copy = original.clone()
        assertTrue(copy is Derived); assertNotSame(original, copy)
        assertSame(original.extra, copy.extra); assertEquals(8, copy.scalar)
        copy.scalar = 99
        assertEquals(8, original.scalar)
    }
}
