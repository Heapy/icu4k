package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UCharacterEnums as JavaEnums
import com.ibm.icu.lang.UCharacterCategory as JavaCategory
import com.ibm.icu.lang.UCharacterDirection as JavaDirection
import kotlin.test.*

class UCharacterEnumsOracleTest {
    @Test fun preservesConstantTypesValuesAndNames() {
        fun fields(type: Class<*>): Map<String, Any> = type.fields.filter { it.type == Int::class.javaPrimitiveType || it.type == Byte::class.javaPrimitiveType }.associate { it.name to it.get(null) }
        assertEquals(fields(JavaEnums.ECharacterCategory::class.java), fields(UCharacterEnums.ECharacterCategory::class.java))
        assertEquals(fields(JavaEnums.ECharacterDirection::class.java), fields(UCharacterEnums.ECharacterDirection::class.java))
        assertEquals(fields(JavaCategory::class.java), fields(UCharacterCategory::class.java))
        assertEquals(fields(JavaDirection::class.java), fields(UCharacterDirection::class.java))
        for (value in -2..40) {
            assertEquals(JavaCategory.toString(value), UCharacterCategory.toString(value))
            assertEquals(JavaDirection.toString(value), UCharacterDirection.toString(value))
        }
    }
}
