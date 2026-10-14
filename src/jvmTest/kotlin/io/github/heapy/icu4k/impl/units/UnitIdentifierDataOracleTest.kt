@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.units

import io.github.heapy.icu4k.impl.IllegalIcuArgumentException
import io.github.heapy.icu4k.util.MeasurePrefix
import io.github.heapy.icu4k.util.UnitComplexity
import kotlin.test.*
import com.ibm.icu.impl.units.UnitsData as OriginalData
import com.ibm.icu.impl.units.UnitAliases as OriginalAliases
import com.ibm.icu.impl.IllegalIcuArgumentException as OriginalException

class UnitIdentifierDataOracleTest {
    @Test fun fullIdentifierCategoriesAndAliasesMatch() {
        assertContentEquals(OriginalData.getSimpleUnits(), UnitIdentifierData.getSimpleUnits())
        for (index in OriginalData.getSimpleUnits().indices) assertEquals(OriginalData.getCategoryIndexOfSimpleUnit(index), UnitIdentifierData.getCategoryIndexOfSimpleUnit(index))
        val originalCategoryClass = Class.forName("com.ibm.icu.impl.units.UnitsData\$Categories")
        fun field(name: String): Any? = originalCategoryClass.getDeclaredField(name).also { it.isAccessible = true }.get(null)
        @Suppress("UNCHECKED_CAST")
        assertContentEquals(field("indexToCategory") as Array<String>, UnitIdentifierData.getCategoryNames())
        assertEquals(field("baseUnitToIndex"), UnitIdentifierData.getBaseUnitToCategoryIndex())
        val original = OriginalAliases(); val actual = UnitAliases()
        assertEquals(original.aliases.map { it.alias to it.replacement }, actual.getAliases().map { it.alias to it.replacement })
        assertEquals(OriginalData.getAliases().map { it.alias to it.replacement }, UnitIdentifierData.getAliases().map { it.alias to it.replacement })
        for (index in OriginalData.getAliases().indices) assertEquals(OriginalData.getReplacementFromAliasIndex(index), UnitIdentifierData.getReplacementFromAliasIndex(index))
        assertNull(actual.getReplacement(null))
        for (prefix in com.ibm.icu.util.MeasureUnit.MeasurePrefix.values()) {
            val port = MeasurePrefix.valueOf(prefix.name)
            assertEquals(listOf(prefix.ordinal, prefix.identifier, prefix.base, prefix.power), listOf(port.ordinal, port.getIdentifier(), port.getBase(), port.getPower()))
        }
        assertEquals(com.ibm.icu.util.MeasureUnit.Complexity.values().map { it.name }, UnitComplexity.entries.map { it.name })
    }
    @Test fun exceptionsConstructorsCauseStateAndSerialVersionMatch() {
        val cause = IllegalStateException("cause")
        val pairs = listOf(
            OriginalException("message") to IllegalIcuArgumentException("message"),
            OriginalException(null as String?) to IllegalIcuArgumentException(null as String?),
            OriginalException(cause) to IllegalIcuArgumentException(cause),
            OriginalException(null as Throwable?) to IllegalIcuArgumentException(null as Throwable?),
            OriginalException("message", cause) to IllegalIcuArgumentException("message", cause),
            OriginalException("message", null) to IllegalIcuArgumentException("message", null))
        for ((j, k) in pairs) {
            assertEquals(j.message, k.message); assertSame(j.cause, k.cause)
            for (next in listOf(cause, null)) {
                fun apply(exception: Throwable): Any? = try { exception.initCause(next); null } catch (failure: Exception) { failure.javaClass.simpleName to failure.message }
                assertEquals(apply(j), apply(k)); assertSame(j.cause, k.cause)
            }
        }
        assertEquals(java.io.ObjectStreamClass.lookup(OriginalException::class.java).serialVersionUID, java.io.ObjectStreamClass.lookup(IllegalIcuArgumentException::class.java).serialVersionUID)
    }
}
