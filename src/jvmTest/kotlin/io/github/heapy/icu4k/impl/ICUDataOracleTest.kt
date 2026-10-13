package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.ICUBinary as JavaBinary
import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UProperty
import io.github.heapy.icu4k.util.CodePointTrie
import kotlin.test.*

class ICUDataOracleTest {
    @Test fun embeddedResourcesMatchIcuReleaseBytes() {
        for (name in listOf("uprops.icu", "ulayout.icu")) {
            val expected = JavaBinary.getRequiredData(name)
            val data = ByteArray(expected.remaining())
            expected.get(data)
            assertContentEquals(data, ICUData.getBytes(name), name)
        }
    }

    @Test fun actualLayoutTriesMatchAllIcuCodePointProperties() {
        val props = LayoutProps.INSTANCE
        val tries = listOf(props.inpcTrie!! to UProperty.INDIC_POSITIONAL_CATEGORY,
            props.inscTrie!! to UProperty.INDIC_SYLLABIC_CATEGORY,
            props.voTrie!! to UProperty.VERTICAL_ORIENTATION)
        for ((trie, property) in tries) {
            for (cp in 0..0x10ffff) {
                val expected = UCharacter.getIntPropertyValue(cp, property)
                val actual = trie[cp]
                if (expected != actual) fail("Layout property $property U+${cp.toString(16)}: expected $expected, actual $actual")
            }
        }
        assertEquals(UCharacter.getIntPropertyMaxValue(UProperty.INDIC_POSITIONAL_CATEGORY), props.maxInpcValue)
        assertEquals(UCharacter.getIntPropertyMaxValue(UProperty.INDIC_SYLLABIC_CATEGORY), props.maxInscValue)
        assertEquals(UCharacter.getIntPropertyMaxValue(UProperty.VERTICAL_ORIENTATION), props.maxVoValue)
    }

    @Test fun modernBlockTrieInUpropsMatchesIcu() {
        val bytes = ICUBinary.getRequiredData("uprops.icu")
        assertEquals(UCharacter.getUnicodeVersion().toString(), ICUBinary.readHeaderAndDataVersion(bytes, 0x5550726f, ICUBinary.Authenticate { it[0] == 9.toByte() }).toString())
        val start = bytes.position
        val indexes = bytes.readInts(16)
        bytes.position = start + indexes[7] * 4
        val trie = CodePointTrie.fromBinary(null, CodePointTrie.ValueWidth.BITS_16, bytes)
        assertTrue(bytes.position <= start + indexes[8] * 4)
        for (cp in 0..0x10ffff) {
            val expected = UCharacter.getIntPropertyValue(cp, UProperty.BLOCK)
            val actual = trie[cp shr 4]
            if (expected != actual) fail("Block U+${cp.toString(16)}: expected $expected, actual $actual")
        }
    }
}
