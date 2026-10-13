package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.CharacterProperties as JavaProperties
import com.ibm.icu.impl.CharacterPropertiesImpl as JavaImpl
import io.github.heapy.icu4k.impl.CharacterPropertiesImpl
import io.github.heapy.icu4k.util.CodePointTrie
import kotlin.test.*

class CharacterPropertiesOracleTest {
    @Test fun allSetsMapsAndInclusionsMatchOriginal() {
        for (prop in 0 until UProperty.BINARY_LIMIT) {
            val j = JavaProperties.getBinaryPropertySet(prop)
            val k = CharacterProperties.getBinaryPropertySet(prop)
            assertEquals(j.getRangeCount(), k.getRangeCount(), "ranges $prop")
            for (i in 0 until j.getRangeCount()) {
                assertEquals(j.getRangeStart(i), k.getRangeStart(i), "$prop $i start")
                assertEquals(j.getRangeEnd(i), k.getRangeEnd(i), "$prop $i end")
            }
            assertEquals(j.strings().toSet(), k.strings().toSet(), "strings $prop")
            assertEquals(JavaImpl.getInclusionsForProperty(prop).toPattern(true), CharacterPropertiesImpl.getInclusionsForProperty(prop).toPattern(true), "incl $prop")
        }
        for (prop in UProperty.INT_START until UProperty.INT_LIMIT) {
            val j = JavaProperties.getIntPropertyMap(prop)
            val k = CharacterProperties.getIntPropertyMap(prop)
            for (c in 0..0x10ffff) if (j[c] != k[c]) fail("map $prop c=$c")
            assertEquals(j[-1], k[-1])
            assertEquals(JavaImpl.getInclusionsForProperty(prop).toPattern(true), CharacterPropertiesImpl.getInclusionsForProperty(prop).toPattern(true), "incl $prop")
            val out = java.io.ByteArrayOutputStream()
            (j as com.ibm.icu.util.CodePointTrie).toBinary(out)
            assertContentEquals(out.toByteArray(), (k as CodePointTrie).toBinary(), "binary $prop")
        }
        for (prop in listOf(-1, UProperty.NAME, Int.MAX_VALUE)) {
            assertFailsWith<IllegalStateException> { JavaImpl.getInclusionsForProperty(prop) }
            assertFailsWith<IllegalStateException> { CharacterPropertiesImpl.getInclusionsForProperty(prop) }
        }
    }
}
