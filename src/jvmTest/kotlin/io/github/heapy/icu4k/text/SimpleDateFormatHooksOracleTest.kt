@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.text.DisplayContext as SourceContext
import com.ibm.icu.util.Calendar as SourceCalendar
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.io.*
import kotlin.test.*

class SimpleDateFormatHooksOracleTest {
    private class A : Source {
        var slow = 0; var fast = 0; var numbers = 0
        constructor() : super("yyyy-MM-dd", SourceLocale.US)
        constructor(override: String?) : super("yyyy-MM-dd", override, SourceLocale.US)
        override fun subFormat(ch: Char, count: Int, begin: Int, fieldNum: Int, context: SourceContext?, pos: java.text.FieldPosition?, output: Char, cal: SourceCalendar?): String? {
            slow++; return super.subFormat(ch, count, begin, fieldNum, context, pos, output, cal)
        }
        override fun subFormat(buf: StringBuffer?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: SourceContext?, pos: java.text.FieldPosition?, output: Char, cal: SourceCalendar?) {
            fast++; super.subFormat(buf, ch, count, begin, fieldNum, context, pos, output, cal)
        }
        override fun zeroPaddingNumber(nf: com.ibm.icu.text.NumberFormat?, buf: StringBuffer?, value: Int, min: Int, max: Int) { numbers++; super.zeroPaddingNumber(nf, buf, value, min, max) }
    }
    private class B : SimpleDateFormat {
        var slow = 0; var fast = 0; var numbers = 0
        constructor() : super("yyyy-MM-dd", ULocale.US)
        constructor(override: String?) : super("yyyy-MM-dd", override, ULocale.US)
        override fun subFormat(ch: Char, count: Int, beginOffset: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?): String? {
            slow++; return super.subFormat(ch, count, beginOffset, fieldNum, context, pos, outputChar, cal)
        }
        override fun subFormat(buf: StringBuilder?, ch: Char, count: Int, beginOffset: Int, fieldNum: Int, context: DisplayContext?, pos: FormatFieldPosition?, outputChar: Char, cal: Calendar?) {
            fast++; super.subFormat(buf, ch, count, beginOffset, fieldNum, context, pos, outputChar, cal)
        }
        override fun zeroPaddingNumber(nf: NumberFormat?, buf: StringBuilder?, value: Int, minDigits: Int, maxDigits: Int) { numbers++; super.zeroPaddingNumber(nf, buf, value, minDigits, maxDigits) }
    }
    private fun roundTrip(value: Any): Any {
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(value) } }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }
    private fun compare(a: A, b: B) {
        assertEquals(a.format(java.util.Date(0)), b.format(Date(0)))
        assertEquals(listOf(a.slow, a.fast, a.numbers), listOf(b.slow, b.fast, b.numbers))
    }
    @Test fun fastSlowAndZeroPaddingHooksRebindAfterCloneAndRead() {
        for (fast in listOf(false, true)) {
            val a = if (fast) A() else A(null); val b = if (fast) B() else B(null)
            a.timeZone = com.ibm.icu.util.TimeZone.getTimeZone("Etc/UTC"); b.setTimeZone(TimeZone.getTimeZone("Etc/UTC"))
            a.set2DigitYearStart(java.util.Date(0)); b.set2DigitYearStart(Date(0))
            compare(a, b)
            val ac = a.clone() as A; val bc = b.clone() as B
            compare(ac, bc)
            compare(a, b)
            compare(roundTrip(a) as A, roundTrip(b) as B)
            assertEquals(listOf(a.slow, a.fast, a.numbers), listOf(b.slow, b.fast, b.numbers))
        }
    }
}
