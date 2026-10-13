package io.github.heapy.icu4k.lang

import com.ibm.icu.lang.UProperty as JavaProperty
import kotlin.test.*

class UPropertyOracleTest {
    @Test fun preservesAllPropertyIdsIncludingDeprecatedBoundaries() {
        fun fields(type: Class<*>): Map<String, Int> = type.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.name to it.getInt(null) }
        assertEquals(fields(JavaProperty::class.java), fields(UProperty::class.java))
        assertEquals(fields(JavaProperty.NameChoice::class.java), fields(UProperty.NameChoice::class.java))
    }
}
