package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UCharacter as JavaCharacter
import com.ibm.icu.impl.UCharacterProperty as JavaProperties
import io.github.heapy.icu4k.impl.UCharacterProperty
import java.util.EnumSet
import kotlin.random.Random
import kotlin.test.*

class IdentifierTypesOracleTest {
    @Test fun typePredicatesAndSetOutputMatchOriginal() {
        val random = Random(819)
        val j = EnumSet.allOf(JavaCharacter.IdentifierType::class.java)
        val k = UCharacter.IdentifierType.entries.toMutableSet()
        for (cp in listOf(-1, 0, 65, 0xd800, 0xffff, 0x10ffff, 0x110000, Int.MAX_VALUE) + List(20000) { random.nextInt(0x110000) }) {
            assertEquals(JavaCharacter.getIdentifierTypes(cp, j), UCharacter.getIdentifierTypes(cp, k), "$cp count")
            assertEquals(j.map { it.name }.toSet(), k.map { it.name }.toSet(), "$cp types")
            for (type in UCharacter.IdentifierType.entries) assertEquals(JavaCharacter.hasIdentifierType(cp, JavaCharacter.IdentifierType.valueOf(type.name)), UCharacter.hasIdentifierType(cp, type), "$cp $type")
            for (index in -1..12) assertEquals(JavaProperties.INSTANCE.hasIDType(cp, index), UCharacterProperty.INSTANCE.hasIDType(cp, index))
        }
    }
}
