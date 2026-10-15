package io.github.heapy.icu4k.impl.number

import kotlin.test.*

class DecimalFormatPropertiesCloneTest {
    private class Derived : DecimalFormatProperties() { val extra = Any() }
    @Test fun shallowClonePreservesRuntimeSubtypeAndExtraFields() {
        val original = Derived().also { it.setPositivePrefix("prefix") }
        val copy = original.clone()
        assertTrue(copy is Derived); assertNotSame(original, copy); assertSame(original.extra, copy.extra)
        assertEquals(original, copy)
        copy.setPositivePrefix("changed")
        assertEquals("prefix", original.getPositivePrefix())
    }
}
