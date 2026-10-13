package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UPropertyAliases as JavaAliases
import kotlin.random.Random
import kotlin.test.*

class UPropertyAliasesOracleTest {
    @Test fun namesValuesAndLooseLookupsMatchOriginalData() {
        val j = JavaAliases.INSTANCE; val k = UPropertyAliases.INSTANCE
        val properties = (0..100) + (0x1000..0x1040) + (0x2000..0x2001) + (0x3000..0x3000) + (0x4000..0x4020) + listOf(-1, 0x7000, Int.MAX_VALUE)
        fun compareResults(expected: () -> String?, actual: () -> String?) {
            val e = runCatching(expected); val a = runCatching(actual)
            assertEquals(e.getOrNull(), a.getOrNull()); assertEquals(e.isSuccess, a.isSuccess)
            if (e.isFailure) assertEquals(e.exceptionOrNull()?.message, a.exceptionOrNull()?.message)
        }
        for (property in properties) {
            for (choice in -1..4) compareResults({ j.getPropertyName(property, choice) }, { k.getPropertyName(property, choice) })
            for (choice in 0..1) {
                val name = runCatching { j.getPropertyName(property, choice) }.getOrNull() ?: continue
                for (alias in listOf(name, name.uppercase(), name.map { "$it-_ " }.joinToString(""))) assertEquals(j.getPropertyEnum(alias), k.getPropertyEnum(alias))
            }
            for (value in -1..350) for (choice in 0..1) {
                compareResults({ j.getPropertyValueName(property, value, choice) }, { k.getPropertyValueName(property, value, choice) })
                val name = runCatching { j.getPropertyValueName(property, value, choice) }.getOrNull() ?: continue
                val alias = name.uppercase().replace("_", "- ")
                assertEquals(j.getPropertyValueEnum(property, alias), k.getPropertyValueEnum(property, alias))
                assertEquals(j.getPropertyValueEnumNoThrow(property, alias), k.getPropertyValueEnumNoThrow(property, alias))
            }
            assertEquals(j.getPropertyValueEnumNoThrow(property, "invalid"), k.getPropertyValueEnumNoThrow(property, "invalid"))
        }
        val random = Random(641)
        repeat(10000) {
            fun name() = CharArray(random.nextInt(20)) { "abCD-_ \t\n".random(random) }.concatToString()
            val a = name(); val b = name()
            val expected = runCatching { JavaAliases.compare(a, b) }
            val actual = runCatching { UPropertyAliases.compare(a, b) }
            assertEquals(expected.getOrNull(), actual.getOrNull())
            assertEquals(expected.isSuccess, actual.isSuccess)
            assertEquals(j.getPropertyEnum(a), k.getPropertyEnum(a))
        }
    }
}
