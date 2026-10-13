package io.github.heapy.icu4k.impl

import kotlin.test.*

class AlgorithmNameOracleTest {
    private val type = Class.forName("com.ibm.icu.impl.UCharacterName\$AlgorithmName")
    private fun newOriginal(): Any = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    private fun call(obj: Any, name: String, vararg args: Any): Any? {
        val types = args.map { when (it) { is Int -> Int::class.javaPrimitiveType!!; is Byte -> Byte::class.javaPrimitiveType!!; else -> it.javaClass } }.toTypedArray()
        return type.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(obj, *args)
    }
    @Test fun bothAlgorithmTypesMatchOriginalIncludingNameMetadata() {
        for (algorithm in 0..1) {
            val j = newOriginal(); val k = UCharacterName.AlgorithmName()
            val start = if (algorithm == 0) 0x3400 else 100
            val end = if (algorithm == 0) 0x4dbf else 111
            val variant: Byte = if (algorithm == 0) 4 else 3
            assertEquals(call(j, "setInfo", start, end, algorithm.toByte(), variant), k.setInfo(start, end, algorithm.toByte(), variant))
            call(j, "setPrefix", "PREFIX-"); k.setPrefix("PREFIX-")
            if (algorithm == 1) {
                val factors = charArrayOf(2.toChar(), 3.toChar(), 2.toChar())
                val strings = "A\u0000B\u0000X\u0000Y\u0000Z\u0000\u0000!\u0000".encodeToByteArray()
                call(j, "setFactor", factors); k.setFactor(factors)
                call(j, "setFactorString", strings); k.setFactorString(strings)
            }
            for (c in start..end) {
                val jo = StringBuffer("!"); val ko = StringBuilder("!")
                call(j, "appendName", c, jo); k.appendName(c, ko)
                assertEquals(jo.toString(), ko.toString())
                val name = jo.substring(1)
                assertEquals(call(j, "getChar", name), k.getChar(name))
                assertEquals(call(j, "contains", c), k.contains(c))
            }
            for (name in listOf("", "PREFIX-+3400", "PREFIX-３４００", "PREFIX-٣٤٠٠", "PREFIX--3400", "PREFIX-FFFFFFFF", "PREFIX-3400!", "prefix-3400")) {
                assertEquals(call(j, "getChar", name), k.getChar(name), name)
            }
            val js = IntArray(2048); val ks = IntArray(2048)
            assertEquals(call(j, "add", js, 0), k.add(ks, 0))
            assertContentEquals(js, ks)
        }
    }
    @Test fun hexFormattingMatchesOriginal() {
        for (value in listOf(0L, 65L, -10L, Long.MIN_VALUE, Long.MAX_VALUE)) for (places in -1..16) {
            assertEquals(com.ibm.icu.impl.Utility.hex(value, places), Utility.hex(value, places))
        }
    }
}
