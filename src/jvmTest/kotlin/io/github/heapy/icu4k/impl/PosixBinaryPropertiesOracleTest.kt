package io.github.heapy.icu4k.impl

import com.ibm.icu.lang.UCharacter as JavaCharacter
import io.github.heapy.icu4k.lang.UProperty
import kotlin.test.*

class PosixBinaryPropertiesOracleTest {
    @Test fun posixClassesMatchEveryUnicodeCodePoint() {
        val properties = listOf(UProperty.POSIX_ALNUM, UProperty.POSIX_BLANK, UProperty.POSIX_GRAPH, UProperty.POSIX_PRINT, UProperty.POSIX_XDIGIT)
        for (which in properties) for (cp in -1..0x110000) assertEquals(JavaCharacter.hasBinaryProperty(cp, which), UCharacterProperty.INSTANCE.hasPosixBinaryProperty(cp, which), "$which $cp")
    }
}
