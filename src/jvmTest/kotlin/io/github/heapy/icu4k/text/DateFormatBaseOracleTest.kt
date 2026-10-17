@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text
import com.ibm.icu.text.DateFormat as Source
import com.ibm.icu.util.Calendar as SourceCalendar
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.io.*
import kotlin.test.*

class DateFormatBaseOracleTest {
    private class A:Source() {
        init { calendar=com.ibm.icu.util.GregorianCalendar(com.ibm.icu.util.SimpleTimeZone(0,"test"),SourceLocale.US);numberFormat=com.ibm.icu.text.NumberFormat.getInstance(SourceLocale.US) }
        override fun format(cal:SourceCalendar?,out:StringBuffer?,pos:java.text.FieldPosition?):StringBuffer=out!!.append("${cal!!.timeInMillis}:${cal.timeZone.id}")
        override fun parse(text:String?,cal:SourceCalendar?,pos:java.text.ParsePosition?) {
            if(text=="bad") { pos!!.errorIndex=pos.index;return }
            cal!!.timeZone=com.ibm.icu.util.SimpleTimeZone(19800000,"parsed")
            if(text=="throw") throw IllegalArgumentException("parser")
            cal.set(2024,1,if(text=="invalid") 31 else 29);pos!!.index+=text!!.length
        }
    }
    private class B:DateFormat() {
        init { calendar=GregorianCalendar(SimpleTimeZone(0,"test"),ULocale.US);numberFormat=NumberFormat.getInstance(ULocale.US) }
        override fun format(calendar:Calendar?,output:StringBuilder?,position:FormatFieldPosition?):StringBuilder=output!!.append("${calendar!!.getTimeInMillis()}:${calendar.getTimeZone()!!.getID()}")
        override fun parse(text:String?,calendar:Calendar?,position:ParsePosition?) {
            if(text=="bad") { position!!.errorIndex=position.index;return }
            calendar!!.setTimeZone(SimpleTimeZone(19800000,"parsed"))
            if(text=="throw") throw IllegalArgumentException("parser")
            calendar.set(2024,1,if(text=="invalid") 31 else 29);position!!.index+=text!!.length
        }
    }
    @Test fun parsePositionErrorsAndZoneRestoration() {
        for(text in listOf("ok","bad","invalid","throw")) for(start in listOf(0,2)) for(lenient in listOf(false,true)) {
            val a=A();val b=B();a.isLenient=lenient;b.setLenient(lenient)
            val ap=java.text.ParsePosition(start);val bp=ParsePosition(start)
            var ax:String?=null;var bx:String?=null
            val av=try { a.parse(text,ap)?.time } catch(e:RuntimeException){ax=e.javaClass.name+e.message;null}
            val bv=try { b.parse(text,bp)?.getTime() } catch(e:RuntimeException){bx=e.javaClass.name+e.message;null}
            assertEquals(av,bv);assertEquals(ax,bx);assertEquals(ap.index,bp.index);assertEquals(ap.errorIndex,bp.errorIndex)
            assertEquals(a.timeZone.id,b.getTimeZone()!!.getID())
        }
    }
    @Test fun ownershipLeniencyContextAndClone() {
        val a=A();val b=B();val ac=a.clone();val bc=b.clone()
        for(attr in DateFormat.BooleanAttribute.entries) {
            ac.setBooleanAttribute(Source.BooleanAttribute.valueOf(attr.name),false);bc.setBooleanAttribute(attr,false)
            assertEquals(a.getBooleanAttribute(Source.BooleanAttribute.valueOf(attr.name)),b.getBooleanAttribute(attr))
        }
        ac.isLenient=false;bc.setLenient(false)
        assertEquals(a.isLenient,b.isLenient());assertEquals(a.isCalendarLenient,b.isCalendarLenient())
        val an=com.ibm.icu.text.DecimalFormat("#,##0.00");val bn=DecimalFormat("#,##0.00")
        a.numberFormat=an;b.setNumberFormat(bn)
        assertEquals(a.numberFormat.isGroupingUsed,b.getNumberFormat()!!.isGroupingUsed())
        assertEquals(a.numberFormat.isParseIntegerOnly,b.getNumberFormat()!!.isParseIntegerOnly())
        assertEquals(a.numberFormat.minimumFractionDigits,b.getNumberFormat()!!.getMinimumFractionDigits())
        for(context in DisplayContext.entries) {
            a.setContext(com.ibm.icu.text.DisplayContext.valueOf(context.name));b.setContext(context)
            for(type in DisplayContext.Type.entries) assertEquals(a.getContext(com.ibm.icu.text.DisplayContext.Type.valueOf(type.name)).name,b.getContext(type).name)
        }
    }
    @Test fun objectDispatchMatchesJdkTemporalConversions() {
        val inputs=listOf<Any>(java.util.Date(123),456L,Double.NaN,
            java.time.LocalDate.of(1500,2,28),java.time.LocalDateTime.of(2024,3,31,2,30),java.time.LocalTime.of(1,2,3),
            java.time.ZonedDateTime.parse("2024-10-27T02:30:00+02:00[Europe/Paris]"),java.time.OffsetDateTime.parse("2024-01-01T12:00:00+05:30"),
            java.time.DayOfWeek.MONDAY,java.time.Month.DECEMBER,java.time.chrono.JapaneseDate.of(2024,1,1),java.util.GregorianCalendar())
        val a=A();val b=B()
        for(input in inputs) assertEquals(a.format(input),b.format(input),input.toString())
        assertEquals(assertFailsWith<IllegalArgumentException>{a.format(java.time.Instant.EPOCH)}.message,assertFailsWith<IllegalArgumentException>{b.format(java.time.Instant.EPOCH)}.message)
        assertEquals(assertFailsWith<IllegalArgumentException>{a.format("invalid")}.message,assertFailsWith<IllegalArgumentException>{b.format("invalid")}.message)
    }
    @Test fun fieldMappingsAndSerialCanonicalization() {
        for(f in -1..24) {
            if(f !in 0..23) { assertFailsWith<IllegalArgumentException>{Source.Field.ofCalendarField(f)};assertFailsWith<IllegalArgumentException>{DateFormat.Field.ofCalendarField(f)};continue }
            assertEquals(Source.Field.ofCalendarField(f)?.calendarField,DateFormat.Field.ofCalendarField(f)?.getCalendarField())
        }
        for(field in Source.Field::class.java.fields.filter { it.type==Source.Field::class.java }) {
            val a=field.get(null) as Source.Field;val b=DateFormat.Field::class.java.getField(field.name).get(null) as DateFormat.Field
            assertEquals(a.calendarField,b.getCalendarField())
            val bytes=ByteArrayOutputStream();ObjectOutputStream(bytes).use{it.writeObject(b)}
            val restored=ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use{it.readObject()}
            assertSame(b,restored)
        }
    }
    @Test fun scalarConstantsAndFixedSizeSkeletonLists() {
        for(field in Source::class.java.fields.filter { it.type==String::class.java || it.type==Int::class.javaPrimitiveType }) {
            if(field.declaringClass!=Source::class.java) continue
            assertEquals(field.get(null),DateFormat::class.java.getField(field.name).get(null),field.name)
        }
        for((a,b) in listOf(Source.DATE_SKELETONS to DateFormat.DATE_SKELETONS,Source.TIME_SKELETONS to DateFormat.TIME_SKELETONS,Source.ZONE_SKELETONS to DateFormat.ZONE_SKELETONS)) {
            assertEquals(a,b);assertFailsWith<UnsupportedOperationException>{a.add("x")};assertFailsWith<UnsupportedOperationException>{b.add("x")}
            val av=a[0];val bv=b[0]
            try { a[0]="changed";b[0]="changed";assertEquals(a,b) } finally { a[0]=av;b[0]=bv }
        }
    }
    private class ChildField:DateFormat.Field("year",1)
    private class SourceChildField:Source.Field("year",1)
    @Test fun fieldSubclassesMustProvideTheirOwnReadResolve() {
        fun restore(value:Any):Any {
            val bytes=ByteArrayOutputStream();ObjectOutputStream(bytes).use{it.writeObject(value)}
            return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use{it.readObject()}
        }
        assertEquals(assertFailsWith<InvalidObjectException>{restore(SourceChildField())}.message,assertFailsWith<InvalidObjectException>{restore(ChildField())}.message)
    }
    @Test fun sourceChronoDateTimeRecursionIsPreservedInIsolatedVms() {
        val classes=listOf(DateFormatRecursionProbe::class.java,DateFormat::class.java,Source::class.java,kotlin.Unit::class.java)
        val path=classes.map{java.io.File(it.protectionDomain.codeSource.location.toURI()).path}.distinct().joinToString(java.io.File.pathSeparator)
        for(mode in listOf("source","port")) {
            val process=ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Xss256k","-cp",path,DateFormatRecursionProbe::class.java.name,mode).redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS),mode)
                assertEquals(0,process.exitValue());assertEquals("StackOverflowError",process.inputStream.bufferedReader().readText().trim())
            } finally { if(process.isAlive) process.destroyForcibly() }
        }
    }

}
