package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.UCharacterName as JavaNames
import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UCharacter
import kotlin.random.Random
import kotlin.test.*

class InverseNameOracleTest {
    @Test fun compressedGroupComparisonsMatchOriginal() {
        val j = JavaNames.INSTANCE; val k = UCharacterName.INSTANCE
        val jm = JavaNames::class.java.getDeclaredMethod("getGroupChar", Int::class.javaPrimitiveType, CharArray::class.java, String::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val km = UCharacterName::class.java.getDeclaredMethod("getGroupChar", Int::class.javaPrimitiveType, CharArray::class.java, String::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        for (group in 0 until j.m_groupcount_) {
            val offsets = CharArray(33); val lengths = CharArray(33)
            val index = j.getGroupLengths(group, offsets, lengths)
            for (line in 0 until 32) for (choice in 0..3) {
                val name = j.getGroupName(index + offsets[line].code, lengths[line].code, choice) ?: continue
                assertEquals(jm.invoke(j, index, lengths, name, choice), km.invoke(k, index, lengths, name, choice), "$group $line $choice")
            }
        }
    }
    @Test fun publicNameLookupAndExtendedValidationMatchOriginal() {
        val random = Random(391)
        val inputs = mutableListOf<String?>(null, "", "not a name", "LATIN CAPITAL LETTER GHA", "<control-0000>", "<control-+0>", "<control-٠>", "<lead surrogate-D800>", "<noncharacter-10ffff>", "<control-0041>", "<unassigned-110000>", "<control-000000000>", "<unknown-0000>", "LATIN CAPITAL LETTER A!", "CJK UNIFIED IDEOGRAPH-FFFFFFFF")
        repeat(100) {
            val cp = random.nextInt(0x110000)
            inputs.add(JavaCharacter.getExtendedName(cp))
        }
        for (cp in listOf(65, 0xe9, 0x1f600, 0x1a2, 0x4e00, 0xac00, 0xac01, 0xd7a3)) inputs.add(JavaCharacter.getName(cp)?.lowercase())
        for (name in inputs) {
            assertEquals(JavaCharacter.getCharFromName(name), UCharacter.getCharFromName(name), "modern $name")
            assertEquals(JavaCharacter.getCharFromExtendedName(name), UCharacter.getCharFromExtendedName(name), "extended $name")
            assertEquals(JavaCharacter.getCharFromNameAlias(name), UCharacter.getCharFromNameAlias(name), "alias $name")
            assertEquals(JavaCharacter.getCharFromName1_0(name), UCharacter.getCharFromName1_0(name))
        }
    }
}
