package io.github.heapy.icu4k.text

import com.ibm.icu.text.UnicodeSet as JavaSet
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.lang.UScript
import kotlin.test.*

class UnicodeSetIntPropertyOracleTest {
    private fun compare(prop: Int, value: Int) {
        val j = JavaSet().add("old").applyIntPropertyValue(prop, value)
        val k = UnicodeSet().add("old").applyIntPropertyValue(prop, value)
        assertEquals(j.getRangeCount(), k.getRangeCount(), "$prop $value count")
        for (i in 0 until j.getRangeCount()) {
            assertEquals(j.getRangeStart(i), k.getRangeStart(i), "$prop $value $i start")
            assertEquals(j.getRangeEnd(i), k.getRangeEnd(i), "$prop $value $i end")
        }
        assertEquals(j.strings().toSet(), k.strings().toSet(), "$prop $value strings")
    }
    @Test fun allSupportedPropertyValuesMatchOriginal() {
        for (prop in 0 until UProperty.BINARY_LIMIT) for (value in -1..2) compare(prop, value)
        for (prop in UProperty.INT_START until UProperty.INT_LIMIT) {
            for (value in -1..UCharacter.getIntPropertyMaxValue(prop) + 1) compare(prop, value)
        }
        for (script in -1..UScript.CODE_LIMIT) compare(UProperty.SCRIPT_EXTENSIONS, script)
        for (idType in -1..UCharacter.IdentifierType.entries.size) compare(UProperty.IDENTIFIER_TYPE, idType)
        for (mask in listOf(0, -1, Int.MIN_VALUE, 0x3e, 0x1c0, 0x700)) compare(UProperty.GENERAL_CATEGORY_MASK, mask)
        for (i in 0..29) compare(UProperty.GENERAL_CATEGORY_MASK, 1 shl i)
    }
    @Test fun frozenAndUnsupportedOutcomesMatchOriginal() {
        for (prop in listOf(UProperty.ALPHABETIC, UProperty.SCRIPT, UProperty.SCRIPT_EXTENSIONS, UProperty.IDENTIFIER_TYPE, UProperty.GENERAL_CATEGORY_MASK, -1, UProperty.NUMERIC_VALUE)) {
            val j = runCatching { JavaSet().freeze().applyIntPropertyValue(prop, 1) }
            val k = runCatching { UnicodeSet().freeze().applyIntPropertyValue(prop, 1) }
            assertEquals(j.exceptionOrNull()!!::class, k.exceptionOrNull()!!::class, "$prop")
        }
    }
}
