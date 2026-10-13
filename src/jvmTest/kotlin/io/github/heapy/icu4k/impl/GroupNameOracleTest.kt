package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCharacterName as JavaNames
import kotlin.test.*

class GroupNameOracleTest {
    @Test fun allGroupLengthsAndNameChoicesMatchOriginal() {
        val j = JavaNames.INSTANCE; val k = UCharacterName.INSTANCE
        for (group in 0 until j.m_groupcount_) {
            assertEquals(j.getGroupMSB(group), k.getGroupMSB(group))
            val jo = CharArray(33); val jl = CharArray(33)
            val ko = CharArray(33); val kl = CharArray(33)
            val ji = j.getGroupLengths(group, jo, jl)
            val ki = k.getGroupLengths(group, ko, kl)
            assertEquals(ji, ki)
            assertContentEquals(jo, ko)
            assertContentEquals(jl, kl)
            for (offset in 0 until 32) for (choice in 0..4) {
                val cp = (j.getGroupMSB(group) shl 5) + offset
                assertEquals(j.getGroupName(ji + jo[offset].code, jl[offset].code, choice), k.getGroupName(ki + ko[offset].code, kl[offset].code, choice), "$group $offset $choice raw")
                assertEquals(j.getGroupName(cp, choice), k.getGroupName(cp, choice), "$cp $choice")
            }
        }
        for (cp in 0..0x10ffff) if (j.getGroup(cp) != k.getGroup(cp)) fail("group $cp")
        for (cp in listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 0x110000)) {
            assertEquals(j.getGroup(cp), k.getGroup(cp))
            assertEquals(JavaNames.getCodepointMSB(cp), UCharacterName.getCodepointMSB(cp))
            assertEquals(JavaNames.getGroupMinFromCodepoint(cp), UCharacterName.getGroupMinFromCodepoint(cp))
            assertEquals(JavaNames.getGroupOffset(cp), UCharacterName.getGroupOffset(cp))
        }
    }
}
