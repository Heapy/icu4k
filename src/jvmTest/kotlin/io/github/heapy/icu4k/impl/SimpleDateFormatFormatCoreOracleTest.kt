@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl

import com.ibm.icu.text.SimpleDateFormat as Source
import com.ibm.icu.text.NumberFormat as SourceNumber
import com.ibm.icu.text.DisplayContext as SourceContext
import com.ibm.icu.util.Calendar as SourceCalendar
import com.ibm.icu.util.ULocale as SourceLocale
import com.ibm.icu.util.TimeZone as SourceZone
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.*
import kotlin.test.*

class SimpleDateFormatFormatCoreOracleTest {
    private class Probe(pattern: String, locale: SourceLocale) : Source(pattern, locale) {
        val trace = ArrayList<String>()
        override fun subFormat(buf: StringBuffer?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: SourceContext?, pos: java.text.FieldPosition?, outputChar: Char, cal: SourceCalendar?) {
            trace.add("field:$ch:$outputChar:$fieldNum")
            super.subFormat(buf, ch, count, begin, fieldNum, context, pos, outputChar, cal)
        }
        override fun getNumberFormat(ch: Char): SourceNumber? { trace.add("number:$ch"); return super.getNumberFormat(ch) }
        override fun zeroPaddingNumber(nf: SourceNumber?, buf: StringBuffer?, value: Int, min: Int, max: Int) {
            trace.add("pad:$value:$min:$max"); super.zeroPaddingNumber(nf, buf, value, min, max)
        }
        fun field(buf: StringBuffer?, ch: Char, count: Int, begin: Int, fieldNum: Int, context: SourceContext?, pos: java.text.FieldPosition?, outputChar: Char, cal: SourceCalendar?) = subFormat(buf,ch,count,begin,fieldNum,context,pos,outputChar,cal)
        fun padding(nf: SourceNumber?, buf: StringBuffer?, value: Int, min: Int, max: Int) = zeroPaddingNumber(nf,buf,value,min,max)
    }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        val t = if (e is java.lang.reflect.InvocationTargetException) e.targetException else e
        if (t is VirtualMachineError) throw t
        when (t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName + ":" + t.message }
    }
    private fun pair(id: String, pattern: String): Pair<Probe, SimpleDateFormatFormatFixture> {
        val a = Probe(pattern, SourceLocale(id)); a.numberFormat = SourceNumber.getInstance(SourceLocale(id))
        return a to SimpleDateFormatFormatFixture(id, pattern)
    }
    private fun compare(a: Probe, b: SimpleDateFormatFormatFixture, millis: Long, zone: String = "Etc/UTC", field: Int = -1) {
        a.calendar.timeZone = SourceZone.getTimeZone(zone); a.calendar.timeInMillis = millis
        b.calendar!!.setTimeZone(TimeZone.getTimeZone(zone)); b.calendar!!.setTimeInMillis(millis)
        val ap = java.text.FieldPosition(field).also { it.beginIndex = 7; it.endIndex = 9 }
        val bp = FormatFieldPosition(field).also { it.setBeginIndex(7); it.setEndIndex(9) }
        val ab = StringBuffer("prefix:"); val bb = StringBuilder("prefix:")
        assertEquals(outcome { a.format(a.calendar, ab, ap).toString() }, outcome { b.core.format(b.calendar, bb, bp).toString() }, "${b.locale}/${b.pattern}/$millis/$zone/$field")
        assertEquals(ab.toString(), bb.toString()); assertEquals(listOf(ap.beginIndex, ap.endIndex), listOf(bp.getBeginIndex(), bp.getEndIndex()))
        val an = a.numberFormat; val bn = b.numberFormat!!
        assertEquals(an.minimumIntegerDigits, bn.getMinimumIntegerDigits()); assertEquals(an.maximumIntegerDigits, bn.getMaximumIntegerDigits())
    }
    @Test fun everyPatternLetterAndWidthAcrossCalendarsLocalesAndDates() {
        val ids = listOf("en_US", "en@numbers=mathbold", "en@numbers=hanidec", "en@numbers=roman", "ru", "fr", "ar_EG", "fa", "zh_CN", "en_US@calendar=hebrew", "zh@calendar=chinese", "ko@calendar=dangi", "ja@calendar=japanese", "th@calendar=buddhist", "fa@calendar=persian", "en@calendar=indian", "ar@calendar=islamic", "en@calendar=coptic", "en@calendar=ethiopic")
        val dates = listOf(-62167219200000L, -2208988800001L, 0L, 1710054000123L, 1730615400999L)
        for (id in ids) {
            val (a,b) = pair(id, "")
            for (ch in ('A'..'Z') + ('a'..'z')) for (width in 1..7) {
                val pattern = ch.toString().repeat(width)
                a.applyPattern(pattern); b.pattern = pattern
                for (date in dates) compare(a,b,date)
            }
        }
    }
    @Test fun zonesDayPeriodsCapitalizationAndFieldPositions() {
        for (id in listOf("en_US", "ru", "fr", "tr", "zh", "ar", "zz")) {
            val (a,b) = pair(id, "")
            for (pattern in listOf("G yyyy MMMM EEEE QQQQ", "b B", "h b", "h:mm:ss BBBB", "'literal 'MMMM", "MMMM MMMM", "z zzzz v vvvv V VV VVV VVVV", "O OOOO Z ZZZZZ X XX XXX XXXX XXXXX x xx xxx xxxx xxxxx")) {
                a.applyPattern(pattern); b.pattern = pattern
                for (context in listOf("CAPITALIZATION_NONE", "CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE", "CAPITALIZATION_FOR_UI_LIST_OR_MENU", "CAPITALIZATION_FOR_STANDALONE")) {
                    a.setContext(SourceContext.valueOf(context)); b.context = DisplayContext.valueOf(context)
                    for (zone in listOf("America/New_York", "Europe/Minsk", "GMT+05:45", "Etc/Unknown")) for (hour in listOf(0, 6, 12, 18, 23)) compare(a,b,hour*3600000L,zone,DateFormat.MONTH_FIELD)
                }
            }
        }
    }
    @Test fun virtualFallbackCallbacksAndFractionUsesPrimaryNumberFormat() {
        val (a,b) = pair("en_US", "b B SSSS")
        a.calendar.timeZone = SourceZone.getTimeZone("Etc/UTC"); a.calendar.timeInMillis = 123
        b.calendar!!.setTimeZone(TimeZone.getTimeZone("Etc/UTC")); b.calendar!!.setTimeInMillis(123)
        a.trace.clear(); b.trace.clear()
        a.format(a.calendar, StringBuffer(), java.text.FieldPosition(-1))
        b.core.format(b.calendar, StringBuilder(), FormatFieldPosition(-1))
        assertEquals(a.trace, b.trace.filter { !it.startsWith("slow:") })
        val sourceOverride = SourceNumber.getInstance(SourceLocale("ar")); a.setNumberFormat("S", sourceOverride)
        b.setOverride("S", NumberFormat.getInstance(ULocale("ar")))
        compare(a,b,123)
    }
    @Test fun fastAndSlowDispatchAttributesAndCalendarMismatch() {
        val format = Source::class.java.getDeclaredMethod("format", SourceCalendar::class.java, StringBuffer::class.java, java.text.FieldPosition::class.java, List::class.java).apply { isAccessible = true }
        val fast = Source::class.java.getDeclaredField("useFastFormat").apply { isAccessible = true }
        for (useFast in listOf(true,false)) {
            val (a,b) = pair("en_US", "yyyy-MM-dd G 'x' EEEE SSS XXX")
            fast.setBoolean(a,useFast); b.useFastFormat = useFast
            val ac = SourceCalendar.getInstance(SourceZone.getTimeZone("America/New_York"),SourceLocale("ja@calendar=japanese")); ac.timeInMillis = 1710054000123
            val bc = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"),ULocale("ja@calendar=japanese")); bc.setTimeInMillis(1710054000123)
            a.calendar.timeZone = SourceZone.getTimeZone("Europe/Minsk"); b.calendar!!.setTimeZone(TimeZone.getTimeZone("Europe/Minsk"))
            val attrsA=ArrayList<java.text.FieldPosition>(); val attrsB=ArrayList<FormatFieldPosition>()
            val ab=StringBuffer("p:"); val bb=StringBuilder("p:")
            assertEquals(format.invoke(a,ac,ab,java.text.FieldPosition(0),attrsA).toString(), b.core.format(bc,bb,FormatFieldPosition(0),attrsB).toString())
            assertEquals(attrsA.map { listOf(it.fieldAttribute?.toString()?.substringAfter('('),it.beginIndex,it.endIndex) },attrsB.map { listOf(it.getFieldAttribute()?.toString()?.substringAfter('('),it.getBeginIndex(),it.getEndIndex()) })
            assertEquals(a.calendar.timeZone.id,b.calendar!!.getTimeZone()!!.getID())
            a.applyPattern("y j"); b.pattern="y j"
            assertEquals(outcome { format.invoke(a,ac,StringBuffer(),java.text.FieldPosition(-1),null) }, outcome { b.core.format(bc,StringBuilder(),FormatFieldPosition(-1)) })
            assertEquals(a.calendar.timeZone.id,b.calendar!!.getTimeZone()!!.getID())
        }
    }
    @Test fun zeroPaddingBoundariesDigitChangesAndCloneBuffers() {
        val (a,b) = pair("ar_EG", "yyyy")
        for (min in listOf(-1,0,1,2,10,12)) for (max in listOf(-1,0,1,2,10,Int.MAX_VALUE)) for (value in listOf(Int.MIN_VALUE,-1,0,1,12345,Int.MAX_VALUE)) {
            val ab=StringBuffer("p:");val bb=StringBuilder("p:")
            assertEquals(outcome { a.padding(a.numberFormat,ab,value,min,max); ab.toString() },outcome { b.numbers.format(b.numberFormat,bb,value,min,max);bb.toString() },"$min/$max/$value")
            assertEquals(ab.toString(),bb.toString())
        }
        val copy=SimpleDateFormatNumberCore().also { it.copyStateFrom(b.numbers) }
        val field=SimpleDateFormatNumberCore::class.java.getDeclaredField("decimalBuf").apply { isAccessible=true }
        assertNotSame(field.get(b.numbers),field.get(copy))
        val digits=SimpleDateFormatNumberCore::class.java.getDeclaredField("decDigits").apply { isAccessible=true }
        assertSame(digits.get(b.numbers),digits.get(copy))
    }

    @Test fun defaultDateNumberFormatAndPerFieldOverrides() {
        for (id in listOf("en_US", "ar_EG", "fa", "zh@calendar=chinese")) {
            val a = Probe("yyyy MMMM dd EEEE HH:mm:ss.SSSS XXX B",SourceLocale(id))
            val b = SimpleDateFormatFormatFixture(id,a.toPattern())
            val ns = NumberingSystem.getInstance(ULocale(id))!!
            b.numberFormat = DateNumberFormat(ULocale(id),ns.getDescription(),ns.getName())
            b.numbers.initialize(b.numberFormat)
            for (date in listOf(0L,1679443200123L,1710054000999L)) compare(a,b,date)
            a.setNumberFormat("yMd",SourceNumber.getInstance(SourceLocale("ar")))
            b.setOverride("yMd",NumberFormat.getInstance(ULocale("ar")))
            for (date in listOf(0L,1679443200123L)) compare(a,b,date)
        }
    }

    @Test fun malformedAndMissingSymbolArraysAndDirectFieldFailures() {
        val dataField=Source::class.java.getDeclaredField("formatData").apply { isAccessible=true }
        for (name in listOf("months","shortMonths","shorterWeekdays","ampms","ampmsWide","wideDayPeriods","abbreviatedDayPeriods","leapMonthPatterns","shortYearNames")) {
            for (array in listOf<Array<String?>?>(null,emptyArray(),arrayOf(null),arrayOf("custom"))) {
                val (a,b)=pair("en_US","MMMM MMM EEEEEE aaaa bbbb BBBB UUUU")
                val data=dataField.get(a)
                data.javaClass.getDeclaredField(name).apply { isAccessible=true }.set(data,array?.copyOf())
                b.symbols!!.state.data.fields[name]=array?.copyOf()
                compare(a,b,43200000L)
            }
        }
        val (a,b)=pair("en_US","yyyy")
        a.calendar.timeInMillis=0;b.calendar!!.setTimeInMillis(0)
        for (ch in listOf('y','S','l','j','b','B')) for (width in listOf(-1,0,1,4,6)) for (output in listOf(ch,'?')) {
            val ab=StringBuffer("p:");val bb=StringBuilder("p:")
            val ap=java.text.FieldPosition(-1);val bp=FormatFieldPosition(-1)
            assertEquals(outcome { a.field(ab,ch,width,11,0,null,ap,output,a.calendar);ab.toString() },outcome { b.core.formatField(bb,ch,width,11,0,null,bp,output,b.calendar);bb.toString() },"$ch/$width/$output")
            assertEquals(ab.toString(),bb.toString());assertEquals(listOf(ap.beginIndex,ap.endIndex),listOf(bp.getBeginIndex(),bp.getEndIndex()))
        }
    }

    @Test fun capitalizationUsesForwardCodePointsAndValidatesTransformArrays() {
        val dataField=Source::class.java.getDeclaredField("formatData").apply { isAccessible=true }
        for (symbol in listOf("lower", "\ud801\udc28lower", "\udc28lower")) for (prefix in listOf("p:", "\ud801")) {
            val (a,b)=pair("en_US","MMMM")
            val data=dataField.get(a)
            val values=Array<String?>(12) { symbol }
            data.javaClass.getDeclaredField("months").apply { isAccessible=true }.set(data,values)
            b.symbols!!.state.data.fields["months"]=values.copyOf()
            a.calendar.timeZone=SourceZone.getTimeZone("Etc/UTC");a.calendar.timeInMillis=0
            b.calendar!!.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));b.calendar!!.setTimeInMillis(0)
            val ab=StringBuffer(prefix);val bb=StringBuilder(prefix)
            a.field(ab,'M',4,prefix.length,0,SourceContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,java.text.FieldPosition(-1),'M',a.calendar)
            b.core.formatField(bb,'M',4,prefix.length,0,DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,FormatFieldPosition(-1),'M',b.calendar)
            assertEquals(ab.toString(),bb.toString())
        }
        for (flags in listOf<BooleanArray?>(null,booleanArrayOf(),booleanArrayOf(true),booleanArrayOf(false,true))) {
            val (a,b)=pair("ru","MMMM")
            val data=dataField.get(a)
            val type=Class.forName("com.ibm.icu.text.DateFormatSymbols\$CapitalizationContextUsage")
            val usage=type.enumConstants.first { (it as Enum<*>).name=="MONTH_FORMAT" }
            val map=HashMap<Any,BooleanArray>()
            if(flags!=null) map[usage]=flags
            data.javaClass.getDeclaredField("capitalization").apply { isAccessible=true }.set(data,map)
            b.symbols!!.state.data.capitalization=HashMap()
            if(flags!=null) b.symbols!!.state.data.capitalization!![DateFormatSymbols.CapitalizationContextUsage.MONTH_FORMAT]=flags.copyOf()
            a.setContext(SourceContext.CAPITALIZATION_FOR_STANDALONE);b.context=DisplayContext.CAPITALIZATION_FOR_STANDALONE
            compare(a,b,0)
        }
    }
}
