@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.number

import com.ibm.icu.impl.number.PatternStringParser as JParser
import com.ibm.icu.impl.number.DecimalQuantity_DualStorageBCD as JQuantity
import kotlin.random.Random
import kotlin.test.*

class PatternStringParserCoreOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) { if (e is IllegalArgumentException) "argument:${e.message}" else if (e is IndexOutOfBoundsException) "bounds" else e.javaClass.simpleName }
    private fun patterns(): List<String> {
        val fixed = listOf("", "0", "#", ".##", "#.##", "@@##", "#,##0.00", "#,##,##0.000", "0.050", "1234567890123456789012345.0000500", "0¤00", "¤0", "0¤@", "0E", "0E+", "0E+00", "##0.0E0", "@@E00", "¤0.00;(¤0.00)", "0%‰", "-0;+0", "*xP00S", "P*x00S", "P00*xS", "P00S*x", "*😀0", "*''0", "*'ab'0", "'a''b'0", "١.##", "😀0\ud800", "0;", ";", "0;;", "0#", "0@", "@0", "@#@", "0.#0", "0,", "#,", "#,,#", "#,0E0", "0*", "*x0*y", "'abc", "0.0.0", "0;0;0")
        val random = Random(3965)
        val pieces = listOf("0", "#", "@", ",", ".", "E", "+", "-", "¤", "%", "‰", "*x", "'a'", "'", ";", "5", "😀", "p")
        return fixed + List(1200) { buildString { repeat(random.nextInt(15)) { append(pieces[random.nextInt(pieces.size)]) } } } +
            listOf("#," + "#".repeat(32768), "#," + "#".repeat(65535), "#".repeat(65536), "1" + "0".repeat(50) + ".000001")
    }
    private fun compareSubpattern(j: JParser.ParsedSubpatternInfo?, k: PatternStringParserCore.ParsedSubpatternInfo?, label: String) {
        assertEquals(j == null, k == null, label)
        if (j == null || k == null) return
        for (field in j.javaClass.fields) {
            val port = k.javaClass.getDeclaredField(field.name).apply { isAccessible = true }.get(k)
            val original = field.get(j)
            val a = when (original) { is Enum<*> -> original.name; is JQuantity -> original.toString(); else -> original }
            val b = when (port) { is Enum<*> -> port.name; is DecimalQuantityCore -> port.toString(); else -> port }
            assertEquals(a, b, "$label/${field.name}")
        }
    }
    @Test fun recursiveParserAllFieldsAndAffixViewsMatchSource() {
        for (pattern in patterns() + listOf(null)) {
            assertEquals(outcome { JParser.parseToPatternInfo(pattern).pattern }, outcome { PatternStringParserCore.parseToPatternInfo(pattern).pattern }, pattern)
            val j = try { JParser.parseToPatternInfo(pattern) } catch (_: Exception) { continue }
            val k = PatternStringParserCore.parseToPatternInfo(pattern)
            compareSubpattern(j.positive, k.positive, "$pattern/positive")
            compareSubpattern(j.negative, k.negative, "$pattern/negative")
            assertEquals(j.hasNegativeSubpattern(), k.hasNegativeSubpattern())
            assertEquals(j.positiveHasPlusSign(), k.positiveHasPlusSign())
            assertEquals(outcome { j.negativeHasMinusSign() }, outcome { k.negativeHasMinusSign() })
            assertEquals(j.hasCurrencySign(), k.hasCurrencySign())
            assertEquals(j.hasBody(), k.hasBody()); assertEquals(j.currencyAsDecimal(), k.currencyAsDecimal())
            for (flags in listOf(0, 0x100, 0x200, 0x300, 0x400, 0x500, 0x600, 0x700, 0x7ff, Int.MIN_VALUE)) {
                assertEquals(outcome { j.getString(flags) }, outcome { k.getString(flags) }, "$pattern/$flags/string")
                assertEquals(outcome { j.length(flags) }, outcome { k.length(flags) }, "$pattern/$flags/length")
                for (index in listOf(-1, 0, 1, 3, 10)) assertEquals(outcome { j.charAt(flags, index) }, outcome { k.charAt(flags, index) }, "$pattern/$flags/$index")
            }
            for (type in listOf(-1, -2, -4, -6, -7)) assertEquals(outcome { j.containsSymbolType(type) }, outcome { k.containsSymbolType(type) }, "$pattern/$type")
        }
    }
    @Test fun projectedPatternOwnedPropertiesMatchSource() {
        for (pattern in patterns() + listOf(null)) for (ignore in listOf(0, 1, 2, -1, 3)) {
            assertEquals(outcome { JParser.parseToProperties(pattern, ignore).getPositivePrefixPattern() }, outcome { PatternStringParserCore.parseToProperties(pattern, ignore).positivePrefixPattern }, "$pattern/$ignore")
            val j = try { JParser.parseToProperties(pattern, ignore) } catch (_: Throwable) { continue }
            val k = PatternStringParserCore.parseToProperties(pattern, ignore)
            compareProperties(j, k, "$pattern/$ignore")
        }
    }
    private fun compareProperties(j: com.ibm.icu.impl.number.DecimalFormatProperties, k: PatternStringParserCore.PatternProperties, label: String) {
        for (field in k.javaClass.declaredFields) {
            field.isAccessible = true
            val getter = "get" + field.name.replaceFirstChar { it.uppercaseChar() }
            val a = j.javaClass.getMethod(getter).invoke(j)
            val b = field.get(k)
            val av = when (a) { is java.math.BigDecimal -> a.toString(); is Enum<*> -> a.name; else -> a }
            val bv = when (b) { is io.github.heapy.icu4k.impl.units.UnitDecimal -> b.toString(); is Enum<*> -> b.name; else -> b }
            assertEquals(av, bv, "$label/${field.name}")
        }
    }
    @Test fun localeNumberFormatPatternCorpusMatchesSource() {
        val seen = mutableSetOf<String>()
        for (locale in com.ibm.icu.text.NumberFormat.getAvailableULocales()) {
            val formats = listOf(com.ibm.icu.text.NumberFormat.getNumberInstance(locale), com.ibm.icu.text.NumberFormat.getCurrencyInstance(locale),
                com.ibm.icu.text.NumberFormat.getPercentInstance(locale), com.ibm.icu.text.NumberFormat.getScientificInstance(locale))
            for (format in formats) {
                if (format !is com.ibm.icu.text.DecimalFormat) continue
                val pattern = format.toPattern()
                if (!seen.add(pattern)) continue
                val j = JParser.parseToPatternInfo(pattern)
                val k = PatternStringParserCore.parseToPatternInfo(pattern)
                compareSubpattern(j.positive, k.positive, "$locale/$pattern/positive")
                compareSubpattern(j.negative, k.negative, "$locale/$pattern/negative")
                for (ignore in 0..2) compareProperties(JParser.parseToProperties(pattern, ignore), PatternStringParserCore.parseToProperties(pattern, ignore), "$locale/$pattern/$ignore")
            }
        }
        assertTrue(seen.size > 10)
    }
    @Test fun mutableAffixViewsAndPackedEndpointArithmeticMatchSource() {
        for (endpoints in listOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x80000000ffffffffUL.toLong())) {
            assertEquals(JParser.ParsedPatternInfo.getLengthFromEndpoints(endpoints), PatternStringParserCore.ParsedPatternInfo.getLengthFromEndpoints(endpoints))
        }
        for (replacement in listOf(null, "", "x", "abcdef")) {
            val j = JParser.parseToPatternInfo("ab0cd;-0x"); val k = PatternStringParserCore.parseToPatternInfo("ab0cd;-0x")
            j.pattern = replacement; k.pattern = replacement
            for (flags in listOf(0, 0x100, 0x200, 0x300, 0x400)) {
                assertEquals(outcome { j.getString(flags) }, outcome { k.getString(flags) })
                assertEquals(outcome { j.charAt(flags, 0) }, outcome { k.charAt(flags, 0) })
            }
        }
    }
}
