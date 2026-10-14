@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.util.MeasurePrefix
import kotlin.test.*

class UnitIdentifierDataTest {
    @Test fun tablesCategoriesAliasesAndPrefixes() {
        val units = UnitIdentifierData.getSimpleUnits()
        assertTrue("gram" in units); assertFalse("kilogram" in units)
        val categories = UnitIdentifierData.getCategoryNames()
        assertEquals("length", categories[UnitIdentifierData.getCategoryIndexOfSimpleUnit(units.indexOf("meter"))])
        assertEquals("mass", categories[UnitIdentifierData.getCategoryIndexOfSimpleUnit(units.indexOf("gram"))])
        val aliases = UnitAliases()
        assertTrue(aliases.getAliases().isNotEmpty())
        for (alias in aliases.getAliases()) assertEquals(alias.replacement, aliases.getReplacement(alias.alias))
        assertNull(aliases.getReplacement("not-a-unit")); assertNull(aliases.getReplacement(null))
        assertFailsWith<UnsupportedOperationException> { (aliases.getAliases() as MutableList).clear() }
        assertEquals(30, MeasurePrefix.QUETTA.getPower()); assertEquals(-30, MeasurePrefix.QUECTO.getPower())
        assertEquals(1024, MeasurePrefix.KIBI.getBase()); assertEquals("", MeasurePrefix.ONE.getIdentifier())
    }
    @Test fun causeInitializationRetainsJavaStateRules() {
        val exception = IllegalIcuArgumentException("bad unit")
        val cause = IllegalStateException("cause")
        assertNull(exception.cause)
        assertSame(exception, exception.initCause(cause)); assertSame(cause, exception.cause)
        val overwritten = assertFailsWith<IllegalStateException> { exception.initCause(null) }
        assertSame(exception, overwritten.cause)
        val self = IllegalIcuArgumentException("self")
        assertSame(self, assertFailsWith<IllegalArgumentException> { self.initCause(self) }.cause)
        assertNull(self.cause); assertSame(self, self.initCause(null))
        assertFailsWith<IllegalStateException> { IllegalIcuArgumentException(null as Throwable?).initCause(cause) }
    }
}
