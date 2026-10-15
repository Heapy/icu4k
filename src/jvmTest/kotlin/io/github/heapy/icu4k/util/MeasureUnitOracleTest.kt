@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.util

import com.ibm.icu.util.MeasureUnit as JUnit
import com.ibm.icu.util.TimeUnit as JTime
import com.ibm.icu.util.Currency as JCurrency
import java.io.*
import kotlin.test.*

class MeasureUnitOracleTest {
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (e: Throwable) { e.javaClass.simpleName + if (e is NullPointerException) "" else ":${e.message}" }
    private fun describe(j: JUnit?): Any? = j?.let {
        listOf(it.type, it.subtype, it.identifier, it.toString(), outcome { it.hashCode() }, outcome { it.complexity?.name },
            outcome { it.prefix?.name }, outcome { it.dimensionality }, outcome { it.constantDenominator },
            outcome { it.splitToSingleUnits().map { u -> u.identifier } })
    }
    private fun describe(k: MeasureUnit?): Any? = k?.let {
        listOf(it.getType(), it.getSubtype(), it.getIdentifier(), it.toString(), outcome { it.hashCode() }, outcome { it.getComplexity()?.name },
            outcome { it.getPrefix()?.name }, outcome { it.getDimensionality() }, outcome { it.getConstantDenominator() },
            outcome { it.splitToSingleUnits().map { u -> u.getIdentifier() } })
    }
    @Test fun constantsCatalogAndDynamicCacheMatchSource() {
        val sourceFields = JUnit::class.java.fields.filter { JUnit::class.java.isAssignableFrom(it.type) }
        for (field in sourceFields) {
            val j = field.get(null) as JUnit
            val k = MeasureUnit::class.java.getField(field.name).get(null) as MeasureUnit
            assertEquals(describe(j), describe(k), field.name)
            assertEquals(j is JTime, k is TimeUnit)
            assertSame(k, MeasureUnit.internalGetInstance(j.type, j.subtype))
        }
        assertEquals(JUnit.getAvailableTypes(), MeasureUnit.getAvailableTypes())
        for (type in JUnit.getAvailableTypes() + setOf("unknown", null)) {
            val j = JUnit.getAvailable(type); val k = MeasureUnit.getAvailable(type)
            assertEquals(j.map { it.identifier }.toSet(), k.map { it.getIdentifier() }.toSet(), type)
        }
        val je = JUnit.getAvailable("unknown") as MutableSet<JUnit>
        val ke = MeasureUnit.getAvailable("unknown") as MutableSet<MeasureUnit>
        assertEquals(outcome { je.clear() }, outcome { ke.clear() })
        assertEquals(outcome { je.remove(JUnit.METER) }, outcome { ke.remove(MeasureUnit.METER) })
        assertEquals(outcome { je.addAll(emptyList()) }, outcome { ke.addAll(emptyList()) })
        assertEquals(outcome { je.iterator().remove() }, outcome { ke.iterator().remove() })
        assertEquals(JUnit.getAvailable().map { it.type to it.subtype }.toSet(), MeasureUnit.getAvailable().map { it.getType() to it.getSubtype() }.toSet())
        val jt = JUnit.getAvailableTypes(); val kt = MeasureUnit.getAvailableTypes()
        val ju = JUnit.getAvailable("length"); val ku = MeasureUnit.getAvailable("length")
        JUnit.internalGetInstance("length", "port-fixture-unit"); MeasureUnit.internalGetInstance("length", "port-fixture-unit")
        JUnit.internalGetInstance("portfixture", "port-fixture-unit"); MeasureUnit.internalGetInstance("portfixture", "port-fixture-unit")
        assertTrue("portfixture" in kt); assertEquals(jt, kt)
        assertEquals(ju.size, ku.size)
        assertTrue(ku.any { it.getSubtype() == "port-fixture-unit" })
        assertEquals(ju == JUnit.getAvailable("length"), ku == MeasureUnit.getAvailable("length"))
        assertFailsWith<UnsupportedOperationException> { (ku as MutableSet).clear() }
        for ((type, name) in listOf(null to "meter", "length" to null, "Length" to "meter", "length" to "UPPER", "" to "", "currency" to "any_code")) {
            assertEquals(outcome { describe(JUnit.internalGetInstance(type, name)) }, outcome { describe(MeasureUnit.internalGetInstance(type, name)) })
        }
        for (j in JTime.values()) assertSame(MeasureUnit.internalGetInstance(j.type, j.subtype), TimeUnit.values().first { it.getIdentifier() == j.identifier })
    }
    @Test fun identifierOperationsAndSourceEdgeCasesMatch() {
        val identifiers = listOf(null, "", "meter", "kilogram", "kilometer-per-hour", "foot-and-inch", "meter-per-100-second", "meter-per-1e6-second", "square-kilometer", "pow15-meter", "per-second", "meter-second-per-meter", "kilometer-meter", "USD", "generic", "not-a-unit")
        for (id in identifiers) {
            assertEquals(outcome { describe(JUnit.forIdentifier(id)) }, outcome { describe(MeasureUnit.forIdentifier(id)) }, id)
            val j = try { JUnit.forIdentifier(id) } catch (_: Exception) { continue }
            val k = MeasureUnit.forIdentifier(id)
            if (j == null || k == null) continue
            for (prefix in JUnit.MeasurePrefix.entries + listOf(null)) assertEquals(outcome { describe(j.withPrefix(prefix)) }, outcome { describe(k.withPrefix(prefix?.let { MeasureUnit.MeasurePrefix.valueOf(it.name) })) }, "$id/$prefix")
            for (power in listOf(-16, -3, -1, 0, 1, 2, 3, 15, 16, Int.MIN_VALUE)) {
                assertEquals(outcome { describe(j.withDimensionality(power)) }, outcome { describe(k.withDimensionality(power)) }, "$id/$power")
            }
            for (denom in listOf(-1L, 0L, 1L, 100L, Long.MAX_VALUE)) assertEquals(outcome { describe(j.withConstantDenominator(denom)) }, outcome { describe(k.withConstantDenominator(denom)) }, "$id/$denom")
            assertEquals(outcome { describe(j.reciprocal()) }, outcome { describe(k.reciprocal()) }, id)
            for (other in listOf(null, "second", "per-meter", "foot-and-inch", "meter-per-100-second")) {
                assertEquals(outcome { describe(j.product(JUnit.forIdentifier(other))) }, outcome { describe(k.product(MeasureUnit.forIdentifier(other))) }, "$id*$other")
            }
        }
    }
    private fun roundTrip(value: Any): Any = ByteArrayOutputStream().let { bytes ->
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
    }
    @Test fun serializationUsesCanonicalUnitFactories() {
        JUnit.internalGetInstance("test", "port-fixture")
        for (unit in listOf(MeasureUnit.METER, MeasureUnit.HOUR, Currency.getInstance("USD"), MeasureUnit.internalGetInstance("test", "port-fixture"))) assertSame(unit, roundTrip(unit))
        assertEquals(outcome { roundTrip(JUnit.forIdentifier("kilometer-per-100-second")) }, outcome { roundTrip(MeasureUnit.forIdentifier("kilometer-per-100-second")!!) })
    }
}
