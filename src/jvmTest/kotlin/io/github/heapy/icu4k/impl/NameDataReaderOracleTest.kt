package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCharacterName as JavaNames
import kotlin.test.*

class NameDataReaderOracleTest {
    private fun field(name: String): Any = JavaNames::class.java.getDeclaredField(name).apply { isAccessible = true }.get(JavaNames.INSTANCE)
    @Test fun fullNameTablesAndAlgorithmRangesMatchOriginal() {
        val j = JavaNames.INSTANCE; val k = UCharacterName.INSTANCE
        assertContentEquals(field("m_tokentable_") as CharArray, k.tokenTable)
        assertContentEquals(field("m_tokenstring_") as ByteArray, k.tokenString)
        assertContentEquals(field("m_groupinfo_") as CharArray, k.groupInfo)
        assertContentEquals(field("m_groupstring_") as ByteArray, k.groupString)
        assertEquals(j.m_groupcount_, k.m_groupcount_)
        assertEquals(j.getAlgorithmLength(), k.getAlgorithmLength())
        for (i in 0 until j.getAlgorithmLength()) {
            assertEquals(j.getAlgorithmStart(i), k.getAlgorithmStart(i))
            assertEquals(j.getAlgorithmEnd(i), k.getAlgorithmEnd(i))
            for (cp in j.getAlgorithmStart(i)..j.getAlgorithmEnd(i)) {
                assertEquals(j.getAlgorithmName(i, cp), k.getAlgorithmName(i, cp), "$i $cp")
            }
        }
    }
}
