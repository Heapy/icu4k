package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.random.Random
import kotlin.test.*

class VectorIntPropertiesOracleTest {
    @Test fun directVectorFieldsMatchOriginalDispatcher() {
        val properties = listOf(UProperty.DECOMPOSITION_TYPE, UProperty.EAST_ASIAN_WIDTH, UProperty.LINE_BREAK, UProperty.GRAPHEME_CLUSTER_BREAK, UProperty.SENTENCE_BREAK, UProperty.WORD_BREAK, UProperty.INDIC_CONJUNCT_BREAK)
        val random = Random(971)
        for (which in properties) {
            assertEquals(JavaCharacter.getIntPropertyMaxValue(which), UCharacterProperty.INSTANCE.getVectorIntPropertyMaxValue(which))
            for (cp in listOf(-1, 0, 0xd800, 0xffff, 0x10000, 0x10ffff, 0x110000, Int.MAX_VALUE) + List(20000) { random.nextInt(0x110000) }) assertEquals(JavaCharacter.getIntPropertyValue(cp, which), UCharacterProperty.INSTANCE.getVectorIntPropertyValue(cp, which), "$which $cp")
        }
    }
}
