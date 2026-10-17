@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.impl

import com.ibm.icu.impl.DateNumberFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.text.DisplayContext
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.ULocale
import java.io.*
import kotlin.test.*

class DateNumberFormatSerializationOracleTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { b -> ObjectOutputStream(b).use { it.writeObject(value) } }.toByteArray()
    private fun read(data: ByteArray, from: String? = null, to: String? = null): Any = object : ObjectInputStream(ByteArrayInputStream(data)) {
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            if (from != null && desc.name.contains(from)) {
                try { return Class.forName(desc.name.replace(from, to!!)) } catch (_: ClassNotFoundException) { }
            }
            return super.resolveClass(desc)
        }
    }.use { it.readObject() }
    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).also { it.isAccessible = true }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        when (e) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> e.javaClass.simpleName + ":" + e.message }
    }
    private fun compare(a: Source, b: DateNumberFormat) {
        assertContentEquals(a.digits, b.getDigits()); assertEquals(a.zeroDigit, b.getZeroDigit())
        assertEquals(a.maximumIntegerDigits, b.getMaximumIntegerDigits()); assertEquals(a.minimumIntegerDigits, b.getMinimumIntegerDigits())
        assertEquals(a.maximumFractionDigits, b.getMaximumFractionDigits()); assertEquals(a.minimumFractionDigits, b.getMinimumFractionDigits())
        assertEquals(a.currency?.currencyCode, b.getCurrency()?.getCurrencyCode()); assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a.isGroupingUsed, b.isGroupingUsed()); assertEquals(a.isParseStrict, b.isParseStrict())
        assertEquals(a.getContext(com.ibm.icu.text.DisplayContext.Type.CAPITALIZATION).name, b.getContext(DisplayContext.Type.CAPITALIZATION).name)
        for (name in listOf("zeroDigit", "minusSign", "positiveOnly", "maxIntDigits", "minIntDigits")) assertEquals(field(Source::class.java, name).get(a), field(DateNumberFormat::class.java, name).get(b))
        for (number in listOf(-123L, 0L, 123L)) assertEquals(outcome { a.format(number) }, outcome { b.format(number) })
        for (text in listOf("123", "-123", "abc", "١٢", "")) {
            val ap = java.text.ParsePosition(0).also { it.errorIndex = 7 }; val bp = ParsePosition(0).also { it.errorIndex = 7 }
            assertEquals(outcome { a.parse(text, ap) }, outcome { b.parse(text, bp) })
            assertEquals(listOf(ap.index, ap.errorIndex), listOf(bp.index, bp.errorIndex))
        }
    }
    @Test fun exactDescriptorsRoundTripsAndCrossReading() {
        val ad = ObjectStreamClass.lookup(Source::class.java); val bd = ObjectStreamClass.lookup(DateNumberFormat::class.java)
        assertEquals(ad.serialVersionUID, bd.serialVersionUID)
        assertEquals(ad.fields.map { it.name to it.typeCode }, bd.fields.map { it.name to it.typeCode })
        for (id in listOf("fr_FR_DNFSERIAL", "ar_EG_DNFSERIAL")) {
            val a = Source(SourceLocale(id), '0', "latn"); val b = DateNumberFormat(ULocale(id), '0', "latn")
            a.maximumIntegerDigits = 8; b.setMaximumIntegerDigits(8); a.minimumIntegerDigits = 3; b.setMinimumIntegerDigits(3)
            a.maximumFractionDigits = 7; b.setMaximumFractionDigits(7); a.minimumFractionDigits = 2; b.setMinimumFractionDigits(2)
            a.isGroupingUsed = false; b.setGroupingUsed(false); a.isParseStrict = true; b.setParseStrict(true)
            a.setParsePositiveOnly(true); b.setParsePositiveOnly(true)
            a.currency = com.ibm.icu.util.Currency.getInstance("EUR"); b.setCurrency(io.github.heapy.icu4k.util.Currency.getInstance("EUR"))
            a.setContext(com.ibm.icu.text.DisplayContext.CAPITALIZATION_FOR_STANDALONE); b.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
            val ab = bytes(a); val bb = bytes(b)
            val ar = read(ab) as Source; val br = read(bb) as DateNumberFormat
            compare(ar, br); compare(ar, read(ab, "com.ibm.icu.", "io.github.heapy.icu4k.") as DateNumberFormat)
            compare(read(bb, "io.github.heapy.icu4k.", "com.ibm.icu.") as Source, br)
            assertEquals(20, (field(DateNumberFormat::class.java, "decimalBuf").get(br) as CharArray).size)
        }
    }
    @Test fun legacyNullDigitsAndMalformedNonNullArrays() {
        val original = Source(SourceLocale("en_US_DNFLEGACY"), '0', "latn").also { it.maximumIntegerDigits = 20 }
        for (digits in listOf<CharArray?>(null, charArrayOf(), charArrayOf('x'), "abcdefghij".toCharArray())) for (zero in listOf('٠', '\ufffc')) {
            val a = original.clone()
            field(Source::class.java, "digits").set(a, digits); field(Source::class.java, "zeroDigit").setChar(a, zero)
            val data = bytes(a)
            val ar = read(data) as Source; val br = read(data, "com.ibm.icu.", "io.github.heapy.icu4k.") as DateNumberFormat
            compare(ar, br)
            if (digits == null) assertContentEquals(CharArray(10) { (zero.code + it).toChar() }, br.getDigits())
        }
        val a = original.clone(); a.maximumIntegerDigits = -5; a.minimumIntegerDigits = 100
        val data = bytes(a)
        compare(read(data) as Source, read(data, "com.ibm.icu.", "io.github.heapy.icu4k.") as DateNumberFormat)
    }
    @Test fun cloneGraphsKeepIndependentDigitsAndTransientBuffers() {
        val a = Source(SourceLocale("en_US_DNFGRAPH"), '0', "latn"); val b = DateNumberFormat(ULocale("en_US_DNFGRAPH"), '0', "latn")
        a.maximumIntegerDigits = 20; b.setMaximumIntegerDigits(20)
        a.format(12345L); b.format(12345L)
        val ar = read(bytes(arrayOf(a, a.clone()))) as Array<Source>
        val br = read(bytes(arrayOf(b, b.clone()))) as Array<DateNumberFormat>
        for (name in listOf("digits", "decimalBuf")) {
            assertFalse(field(Source::class.java, name).get(ar[0]) === field(Source::class.java, name).get(ar[1]))
            assertFalse(field(DateNumberFormat::class.java, name).get(br[0]) === field(DateNumberFormat::class.java, name).get(br[1]))
        }
        val buffer = field(DateNumberFormat::class.java, "decimalBuf").get(br[0]) as CharArray
        assertContentEquals(CharArray(20), buffer)
        compare(ar[0], br[0]); compare(ar[1], br[1])
        br[1].setZeroDigit('٠'); assertEquals('0', br[0].getZeroDigit())
    }
}
