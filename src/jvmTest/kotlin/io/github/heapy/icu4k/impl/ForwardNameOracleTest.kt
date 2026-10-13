package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import com.ibm.icu.impl.UCharacterName as JavaNames
import io.github.heapy.icu4k.lang.UCharacter
import kotlin.test.*

class ForwardNameOracleTest {
    @Test fun allUnicodeForwardNamesMatchOriginal() {
        for (cp in 0..0x10ffff) {
            if (JavaCharacter.getName(cp) != UCharacter.getName(cp)) fail("name $cp")
            if (JavaCharacter.getExtendedName(cp) != UCharacter.getExtendedName(cp)) fail("extended $cp")
            if (JavaCharacter.getNameAlias(cp) != UCharacter.getNameAlias(cp)) fail("alias $cp")
        }
        for (cp in listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 0x110000, 0, 65, 0xd800, 0x10ffff)) {
            for (choice in -1..6) assertEquals(JavaNames.INSTANCE.getName(cp, choice), UCharacterName.INSTANCE.getName(cp, choice), "$cp $choice")
            assertEquals(JavaNames.INSTANCE.getExtendedOr10Name(cp), UCharacterName.INSTANCE.getExtendedOr10Name(cp))
            assertEquals(JavaCharacter.getName1_0(cp), UCharacter.getName1_0(cp))
            assertEquals(JavaCharacter.getISOComment(cp), UCharacter.getISOComment(cp))
        }
        for (text in listOf("", "A", "\u0000", "A😀", "\u0000A", "각Å")) for (separator in listOf(null, "", ",")) {
            assertEquals(JavaCharacter.getName(text, separator), UCharacter.getName(text, separator))
        }
    }
}
