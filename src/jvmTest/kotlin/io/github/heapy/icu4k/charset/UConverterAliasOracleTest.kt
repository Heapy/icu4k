package io.github.heapy.icu4k.charset

import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class UConverterAliasOracleTest {
    private val source = Class.forName("com.ibm.icu.charset.UConverterAlias")
    private val normalize = source.getDeclaredMethod("stripForCompare", StringBuilder::class.java, String::class.java).apply { isAccessible = true }
    private val compare = source.getDeclaredMethod("compareNames", String::class.java, String::class.java).apply { isAccessible = true }
    private val count = source.getDeclaredMethod("countAliases", String::class.java).apply { isAccessible = true }
    private val alias = source.getDeclaredMethod("getAlias", String::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
    private val standard = source.getDeclaredMethod("getStandardName", String::class.java, String::class.java).apply { isAccessible = true }
    private val canonical = source.getDeclaredMethod("getCanonicalName", String::class.java, String::class.java).apply { isAccessible = true }
    private fun outcome(action: () -> Any?): Any? = try { action() } catch (error: Throwable) {
        val cause = if (error is InvocationTargetException) error.cause!! else error
        when (cause) {
            is IndexOutOfBoundsException -> "bounds"
            is IllegalArgumentException -> "argument:${cause.message}"
            is NullPointerException -> "null"
            else -> throw cause
        }
    }
    @Test fun normalizationAndComparatorMatchAcrossAllAsciiAndRandomUnicode() {
        val random = Random(310)
        for (c1 in 0..127) for (c2 in 0..127) {
            val first = "a${c1.toChar()}00b"
            val second = "A${c2.toChar()}0B"
            assertEquals(outcome { compare.invoke(null, first, second) }, outcome { UConverterAlias.compareNames(first, second) }, "$c1/$c2")
        }
        val names = mutableListOf("", "a", "b", "abc", "0", "00", "a0", "a1", "UTF-8", "UTF-7", "ibm-00037")
        repeat(3000) {
            names += buildString { repeat(random.nextInt(15)) { append(if (random.nextInt(4) == 0) random.nextInt(65536).toChar() else random.nextInt(128).toChar()) } }
        }
        for ((index, name) in names.withIndex()) {
            assertEquals(normalize.invoke(null, StringBuilder("prefix"), name).toString(), UConverterAlias.stripForCompare(StringBuilder("prefix"), name).toString())
            val other = names[(index * 73 + 1) % names.size]
            assertEquals(outcome { compare.invoke(null, name, other) }, outcome { UConverterAlias.compareNames(name, other) }, "$index")
            assertEquals(outcome { compare.invoke(null, other, name) }, outcome { UConverterAlias.compareNames(other, name) }, "$index reverse")
        }
    }
    @Test fun normalizedVariantsInvalidIndicesAndNullHandling() {
        val random = Random(1252)
        for (row in converterAliasGoldenRows) {
            val original = row.substringBefore('|')
            val variant = buildString {
                for (c in original) {
                    append(if (random.nextBoolean()) c.uppercaseChar() else c.lowercaseChar())
                    if (random.nextInt(6) == 0) append('_')
                }
            }
            assertEquals(count.invoke(null, variant), UConverterAlias.countAliases(variant), variant)
            assertEquals(outcome { alias.invoke(null, variant, 0) }, outcome { UConverterAlias.getAlias(variant, 0) }, variant)
            for (tag in converterAliasStandards) {
                assertEquals(outcome { standard.invoke(null, variant, tag) }, outcome { UConverterAlias.getStandardName(variant, tag) }, "$variant/$tag")
                assertEquals(outcome { canonical.invoke(null, variant, tag) }, outcome { UConverterAlias.getCanonicalName(variant, tag) }, "$variant/$tag")
            }
        }
        for (name in listOf(null, "", "unknown", "UTF-8", "ibm-37")) {
            assertEquals(outcome { count.invoke(null, name) }, outcome { UConverterAlias.countAliases(name) })
            for (index in listOf(Int.MIN_VALUE, -50, -2, -1, 0, 1, 20, 65535, Int.MAX_VALUE)) assertEquals(outcome { alias.invoke(null, name, index) }, outcome { UConverterAlias.getAlias(name, index) }, "$name/$index")
            assertEquals(outcome { standard.invoke(null, name, null) }, outcome { UConverterAlias.getStandardName(name, null) })
            assertEquals(outcome { canonical.invoke(null, name, null) }, outcome { UConverterAlias.getCanonicalName(name, null) })
        }
    }
}
