package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCharacterName as JavaNames
import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.text.UnicodeSet
import kotlin.test.*

class NameMetadataOracleTest {
    @Test fun lengthsAndCharacterSetsMatchOriginal() {
        val j = JavaNames.INSTANCE; val k = UCharacterName.INSTANCE
        // Exercise character sets before maximum-length access as well as repeated reads.
        repeat(2) {
            val js = JavaSet().add("old"); val ks = UnicodeSet().add("old")
            j.getCharNameCharacters(js); k.getCharNameCharacters(ks)
            assertEquals(js.toPattern(true), ks.toPattern(true))
            j.getISOCommentCharacters(js); k.getISOCommentCharacters(ks)
            assertEquals(js.toPattern(true), ks.toPattern(true))
            assertEquals(j.getMaxCharNameLength(), k.getMaxCharNameLength())
            assertEquals(j.getMaxISOCommentLength(), k.getMaxISOCommentLength())
        }
    }
}
