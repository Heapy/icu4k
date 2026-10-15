@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.PatternStringParser as JParser
import java.lang.reflect.Modifier
import kotlin.test.*

class PatternStringParserOracleTest {
    private fun result(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        when (e) {
            is IndexOutOfBoundsException -> "bounds"
            is NullPointerException -> "null"
            else -> "${e.javaClass.simpleName}:${e.message}"
        }
    }
    @Test fun publicMutableFieldContract() {
        val j = JParser.ParsedSubpatternInfo::class.java.fields.associateBy { it.name }
        val k = PatternStringParser.ParsedSubpatternInfo::class.java.fields.associateBy { it.name }
        assertEquals(j.keys, k.keys)
        for (name in j.keys) {
            assertEquals(Modifier.isPublic(j.getValue(name).modifiers), Modifier.isPublic(k.getValue(name).modifiers))
            assertFalse(Modifier.isFinal(k.getValue(name).modifiers))
        }
        val actual = PatternStringParser.ParsedPatternInfo::class.java
        for (name in listOf("pattern", "positive", "negative")) assertTrue(Modifier.isPublic(actual.getField(name).modifiers))
        assertTrue(actual.declaredConstructors.none { Modifier.isPublic(it.modifiers) && !it.isSynthetic })
        assertEquals(DecimalQuantity_DualStorageBCD::class.java, k.getValue("rounding").type)
    }
    @Test fun replacedSubpatternsAndMalformedEndpoints() {
        for (endpoints in listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 1L shl 32, (5L shl 32) or 1)) {
            for (text in listOf(null, "", "abcdef", "😀x")) {
                val j = JParser.parseToPatternInfo("+0.05x;-0y")
                val k = PatternStringParser.parseToPatternInfo("+0.05x;-0y")
                j.negative = j.positive; k.negative = k.positive
                j.positive.suffixEndpoints = endpoints; k.positive!!.suffixEndpoints = endpoints
                j.pattern = text; k.pattern = text
                for (flags in listOf(0, 0x100, 0x200, 0x300, 0x400)) {
                    assertEquals(result { j.getString(flags) }, result { k.getString(flags) })
                    assertEquals(result { j.length(flags) }, result { k.length(flags) })
                    for (index in -1..2) assertEquals(result { j.charAt(flags, index) }, result { k.charAt(flags, index) })
                }
            }
        }
    }
    @Test fun publicProjectionAllFieldsAndFailureOrder() {
        val getters = com.ibm.icu.impl.number.DecimalFormatProperties::class.java.methods.filter {
            it.name.startsWith("get") && it.name != "getClass" && it.parameterCount == 0
        }
        for (pattern in listOf(null, "", "#,##0.050", "¤0.00;(¤0.00)", "@@##", "*x0E+00")) for (ignore in 0..2) {
            val j = JParser.parseToProperties(pattern, ignore)
            val k = PatternStringParser.parseToProperties(pattern, ignore)
            for (getter in getters) {
                val a = getter.invoke(j)
                val b = k.javaClass.getMethod(getter.name).invoke(k)
                assertEquals(a?.toString(), b?.toString(), "$pattern/$ignore/${getter.name}")
            }
        }
        for (pattern in listOf(null, "", "0", "0#", "'")) {
            assertEquals(result { JParser.parseToExistingProperties(pattern, null) }, result { PatternStringParser.parseToExistingProperties(pattern, null) })
        }
    }
}
