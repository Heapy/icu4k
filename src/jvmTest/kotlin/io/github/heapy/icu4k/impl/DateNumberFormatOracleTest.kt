@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.DateNumberFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class DateNumberFormatOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        when (e) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            else -> e.javaClass.simpleName + ":" + e.message
        }
    }
    private fun source(id: String, digits: String = "0123456789", ns: String? = "latn") = Source(SourceLocale(id), digits, ns)
    private fun port(id: String, digits: String = "0123456789", ns: String? = "latn") = DateNumberFormat(ULocale(id), digits, ns)
    private fun compare(a: Source, b: DateNumberFormat) {
        assertContentEquals(a.digits, b.getDigits()); assertEquals(a.zeroDigit, b.getZeroDigit())
        assertEquals(a.maximumIntegerDigits, b.getMaximumIntegerDigits()); assertEquals(a.minimumIntegerDigits, b.getMinimumIntegerDigits())
        assertEquals(a.hashCode(), b.hashCode()); assertEquals(a.isGroupingUsed, b.isGroupingUsed())
        assertEquals(a.isParseIntegerOnly, b.isParseIntegerOnly()); assertEquals(a.isParseStrict, b.isParseStrict())
    }
    @Test fun localeMinusSignsAndNumberingFallback() {
        val files = mutableListOf<String>(); ICUData.visitResources(ICUData.ICU_BASE_NAME + "/") { files.add(it) }
        val ids = files.filter { '/' !in it && it.endsWith(".res") && it != "pool.res" && it != "res_index.res" }.map { it.removeSuffix(".res") }
        for ((index, locale) in ids.withIndex()) for (ns in listOf("latn", "arab", "missing")) {
            val id = locale + "_DNFORACLESIGN${index}_" + ns
            val a = source(id, ns = ns); val b = port(id, ns = ns)
            compare(a, b)
            a.maximumIntegerDigits = 20; b.setMaximumIntegerDigits(20)
            assertEquals(a.format(-123L), b.format(-123L), "$locale/$ns")
        }
        assertTrue(ids.size > 900)
    }
    @Test fun constructorsAndLocaleOnlyCacheIncludingInvalidArguments() {
        val id = "en_US_DNFORACLECACHE"
        val a = source(id, "abcdefghij"); val b = port(id, "abcdefghij")
        compare(a, b)
        for (digits in listOf("٠١٢٣٤٥٦٧٨٩", "", "short", "0123456789", "01234567890", null)) for (ns in listOf("latn", "arab", "missing", null)) {
            val left = outcome { Source(SourceLocale(id), digits, ns).digits.toList() }
            val right = outcome { DateNumberFormat(ULocale(id), digits, ns).getDigits().toList() }
            assertEquals(left, right, "cached/$digits/$ns")
        }
        var sequence = 0
        for (digits in listOf("", "short", "0123456789", "01234567890", "𝟎𝟏𝟐𝟑𝟒", null)) for (ns in listOf("latn", "missing", null)) {
            val fresh = "en_US_DNFORACLECOLD${sequence++}"
            assertEquals(outcome { Source(SourceLocale(fresh), digits, ns).digits.toList() }, outcome { DateNumberFormat(ULocale(fresh), digits, ns).getDigits().toList() }, "cold/$digits/$ns")
        }
        for (zero in listOf('0', '٠', '\ufffc', '\ud800')) {
            val fresh = "en_US_DNFORACLECHAR${zero.code}"
            assertContentEquals(Source(SourceLocale(fresh), zero, "latn").digits, DateNumberFormat(ULocale(fresh), zero, "latn").getDigits())
        }
        assertEquals(outcome { Source(null as SourceLocale?, '0', "latn").digits.toList() }, outcome { DateNumberFormat(null as ULocale?, '0', "latn").getDigits().toList() })
    }
    @Test fun longFormattingIndependentLimitsAndFailureSideEffects() {
        val a = source("en_US_DNFORACLELONG"); val b = port("en_US_DNFORACLELONG")
        for (max in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 5, 20, 21, Int.MAX_VALUE)) for (min in listOf(Int.MIN_VALUE, -1, 0, 1, 4, 20, 21, Int.MAX_VALUE)) {
            a.maximumIntegerDigits = max; b.setMaximumIntegerDigits(max)
            a.minimumIntegerDigits = min; b.setMinimumIntegerDigits(min)
            compare(a, b)
            for (number in listOf(Long.MIN_VALUE, Long.MIN_VALUE + 1, -4294967297L, -4294967296L, -2147483648L, -12345L, -1L, 0L, 1L, 12345L, 2147483647L, 2147483648L, 4294967296L, 4294967297L, Long.MAX_VALUE)) for (field in listOf(0, 1)) {
                val ap = java.text.FieldPosition(field).also { it.beginIndex = 7; it.endIndex = 9 }; val bp = FormatFieldPosition(field).also { it.setBeginIndex(7); it.setEndIndex(9) }
                val ab = StringBuffer("prefix"); val bb = StringBuilder("prefix")
                assertEquals(outcome { a.format(number, ab, ap).toString() }, outcome { b.format(number, bb, bp).toString() }, "$max/$min/$number/$field")
                assertEquals(ab.toString(), bb.toString()); assertEquals(listOf(ap.beginIndex, ap.endIndex), listOf(bp.getBeginIndex(), bp.getEndIndex()))
            }
        }
        for (max in listOf(0, 20)) for (number in listOf(-1L, 1L)) {
            a.maximumIntegerDigits = max; b.setMaximumIntegerDigits(max); a.minimumIntegerDigits = 0; b.setMinimumIntegerDigits(0)
            assertEquals(outcome { a.format(number, null, null) }, outcome { b.format(number, null as StringBuilder?, null) })
            val ab = StringBuffer(); val bb = StringBuilder()
            assertEquals(outcome { a.format(number, ab, null) }, outcome { b.format(number, bb, null) }); assertEquals(ab.toString(), bb.toString())
        }
    }
    @Test fun integerParsingThresholdDigitPriorityAndPositions() {
        for ((i, digits) in listOf("0123456789", "٠١٢٣٤٥٦٧٨٩", "abcdefghij", "9876543210", "0123406789").withIndex()) {
            val a = source("en_US_DNFORACLEPARSE$i", digits); val b = port("en_US_DNFORACLEPARSE$i", digits)
            for (positive in listOf(false, true)) {
                a.setParsePositiveOnly(positive); b.setParsePositiveOnly(positive)
                a.isParseIntegerOnly = true; b.setParseIntegerOnly(true); a.isParseStrict = true; b.setParseStrict(true)
                for (text in listOf("", "-", "--1", "-0", "+1", " 12", "12.3", "12,345", "١٢", "१२", "abc", "jig", "987", "000000000000000000000000012", "9223372036854775789", "9223372036854775790", "9223372036854775807", "-9223372036854775808", "xx123suffix", "𝟏", null)) for (start in listOf(-1, 0, 2, text?.length ?: 0, Int.MAX_VALUE)) {
                    val ap = java.text.ParsePosition(start).also { it.errorIndex = 7 }; val bp = ParsePosition(start).also { it.errorIndex = 7 }
                    assertEquals(outcome { a.parse(text, ap)?.let { listOf(it.javaClass.name, it.toString()) } }, outcome { b.parse(text, bp)?.let { listOf(it.javaClass.name, it.toString()) } }, "$i/$positive/$text/$start")
                    assertEquals(listOf(ap.index, ap.errorIndex), listOf(bp.index, bp.errorIndex))
                }
            }
        }
    }
    @Test fun inheritedDispatchAndUnsupportedNumberOverloads() {
        val a = source("en_US_DNFORACLEUNSUPPORTED"); val b = port("en_US_DNFORACLEUNSUPPORTED")
        a.maximumIntegerDigits = 20; b.setMaximumIntegerDigits(20)
        for (number in listOf(Double.NaN, Double.NEGATIVE_INFINITY, -1.0, 0.0, 1.0)) assertEquals(outcome { a.format(number, null, null) }, outcome { b.format(number, null as StringBuilder?, null) })
        assertEquals(outcome { a.format(null as java.math.BigInteger?, null, null) }, outcome { b.format(null as BigInteger?, null as StringBuilder?, null) })
        assertEquals(outcome { a.format(java.math.BigInteger("123"), null, null) }, outcome { b.format(BigInteger("123"), null as StringBuilder?, null) })
        assertEquals(outcome { a.format(java.math.BigDecimal("1.2"), null, null) }, outcome { b.format(BigDecimal("1.2"), null as StringBuilder?, null) })
        assertEquals(outcome { a.format(com.ibm.icu.math.BigDecimal("1.2"), null, null) }, outcome { b.format(io.github.heapy.icu4k.math.BigDecimal("1.2"), null as StringBuilder?, null) })
        for (input in listOf(1, 1L, 1.0, "bad", null)) assertEquals(outcome { a.format(input as Any?) }, outcome { b.format(input as Any?) })
        val ac = com.ibm.icu.util.Currency.getInstance("USD"); val bc = Currency.getInstance("USD")
        for (number in listOf<Number>(1L, 1.0)) {
            assertEquals(outcome { a.format(com.ibm.icu.util.CurrencyAmount(number, ac)) }, outcome { b.format(CurrencyAmount(number, bc)) })
            assertEquals(a.currency?.currencyCode, b.getCurrency()?.getCurrencyCode())
        }
    }
    @Test fun clonesEqualityAndDigitDefensiveCopies() {
        val a = source("en_US_DNFORACLECLONE"); val b = port("en_US_DNFORACLECLONE")
        a.maximumIntegerDigits = 5; b.setMaximumIntegerDigits(5); a.minimumIntegerDigits = 2; b.setMinimumIntegerDigits(2)
        a.isGroupingUsed = false; b.setGroupingUsed(false); a.maximumFractionDigits = 7; b.setMaximumFractionDigits(7)
        a.isParseStrict = true; b.setParseStrict(true); a.setParsePositiveOnly(true); b.setParsePositiveOnly(true)
        assertEquals(a.format(12345L), b.format(12345L))
        val ac = a.clone(); val bc = b.clone(); compare(ac, bc)
        assertEquals(a == ac, b == bc); assertNotSame(b, bc)
        ac.zeroDigit = '٠'; bc.setZeroDigit('٠'); assertEquals(a == ac, b == bc)
        ac.digits[0] = '!'; bc.getDigits()[0] = '!'; assertContentEquals(ac.digits, bc.getDigits())
        assertEquals(a.format(12L), b.format(12L)); assertEquals(ac.format(12L), bc.format(12L))
        assertEquals(a == null, b == null); assertEquals(a.equals("other"), b.equals("other"))
        val ab = Source::class.java.getDeclaredField("decimalBuf").also { it.isAccessible = true }; val bb = DateNumberFormat::class.java.getDeclaredField("decimalBuf").also { it.isAccessible = true }
        assertFalse(ab.get(a) === ab.get(ac)); assertFalse(bb.get(b) === bb.get(bc)); assertContentEquals(ab.get(ac) as CharArray, bb.get(bc) as CharArray)
    }
}
