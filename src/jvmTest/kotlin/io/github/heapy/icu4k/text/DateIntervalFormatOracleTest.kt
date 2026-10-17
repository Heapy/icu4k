@file:Suppress("DEPRECATION", "UNCHECKED_CAST")
package io.github.heapy.icu4k.text

import com.ibm.icu.text.DateIntervalFormat as Source
import com.ibm.icu.text.DateIntervalInfo as SourceInfo
import com.ibm.icu.util.DateInterval as SourceInterval
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.util.*
import java.text.FieldPosition
import kotlin.test.*

class DateIntervalFormatOracleTest {
    private val start = 1705323723456L
    private fun fieldName(field: Any?): String = field.toString().substringAfterLast('(')
    private fun rows(value: com.ibm.icu.text.FormattedValue): List<List<Any?>> = buildList {
        val p = com.ibm.icu.text.ConstrainedFieldPosition()
        while (value.nextPosition(p)) add(listOf(fieldName(p.field),p.fieldValue,p.start,p.limit))
    }
    private fun rows(value: FormattedValue): List<List<Any?>> = buildList {
        val p = ConstrainedFieldPosition()
        while (value.nextPosition(p)) add(listOf(fieldName(p.getField()),p.getFieldValue(),p.getStart(),p.getLimit()))
    }
    private fun compare(a: Source, b: DateIntervalFormat, from: Long, to: Long, label: String) {
        val intervalA = SourceInterval(from,to); val intervalB = DateInterval(from,to)
        assertEquals(a.format(intervalA), b.format(intervalB), label)
        for (field in listOf(0,1,2,3,5,6,8,9,11,12,17,-1)) {
            val ap = FieldPosition(field); val bp = FormatFieldPosition(field)
            val ao = StringBuffer("prefix:"); val bo = StringBuilder("prefix:")
            assertSame(bo,b.format(intervalB,bo,bp))
            a.format(intervalA,ao,ap)
            assertEquals(ao.toString(),bo.toString(),"$label/$field")
            assertEquals(ap.beginIndex,bp.getBeginIndex(),"$label/$field/start")
            assertEquals(ap.endIndex,bp.getEndIndex(),"$label/$field/limit")
        }
        val av = a.formatToValue(intervalA); val bv = b.formatToValue(intervalB)
        assertEquals(av.toString(),bv.toString(),label); assertEquals(rows(av),rows(bv),label)
        assertEquals(av.length,bv.length); assertEquals(av.toString().toList(),bv.toList())
        assertEquals(av.subSequence(0,av.length).toString(),bv.subSequence(0,bv.length).toString())
        val ai = av.toCharacterIterator(); val bi = bv.toCharacterIterator()
        assertEquals(ai.allAttributeKeys.map(::fieldName).toSet(),bi.getAllAttributeKeys().map(::fieldName).toSet())
        for (i in 0 until av.length) {
            ai.setIndex(i); bi.setIndex(i)
            assertEquals(ai.attributes.entries.associate { fieldName(it.key) to (if(it.value is Int) it.value else fieldName(it.value)) },bi.getAttributes().entries.associate { fieldName(it.key) to (if(it.value is Int) it.value else fieldName(it.value)) },"$label/$i")
            assertEquals(ai.runStart,bi.getRunStart()); assertEquals(ai.runLimit,bi.getRunLimit())
        }
    }
    @Test fun cldrFormattingPositionsAndEndpointSpansAcrossCalendarsAndDifferenceFields() {
        for (id in listOf("en_US","en_GB","fr","ru","ja","ar","zh@calendar=chinese","he@calendar=hebrew","th@calendar=buddhist","ja@calendar=japanese")) {
            for (skeleton in listOf("y","yMMMd","yyyyMMMMdd","GyMMMEd","Hm","hm","HmsSSS","Jm","hmz","yMdHm","yMMMdjm")) {
                val a = Source.getInstance(skeleton,SourceLocale(id),SourceInfo(SourceLocale(id)))
                val b = DateIntervalFormat.getInstance(skeleton,ULocale(id),DateIntervalInfo(ULocale(id)))
                a.timeZone = com.ibm.icu.util.TimeZone.getTimeZone("America/New_York"); b.setTimeZone(TimeZone.getTimeZone("America/New_York"))
                for (delta in listOf(0L,1L,1000L,60000L,3600000L,43200000L,86400000L,2678400000L,34560000000L,-86400000L)) compare(a,b,start,start+delta,"$id/$skeleton/$delta")
            }
        }
    }
    @Test fun defaultPatternCacheHitRetainsSourceFallbackState() {
        for (round in 0..2) for (skeleton in listOf("yMdHms","yMMMdHm","Hm","Jm")) {
            val id = "en_US@icu4kcase=intervalcache"
            val a = Source.getInstance(skeleton,SourceLocale(id)); val b = DateIntervalFormat.getInstance(skeleton,ULocale(id))
            a.timeZone = com.ibm.icu.util.TimeZone.GMT_ZONE; b.setTimeZone(TimeZone.GMT_ZONE)
            compare(a,b,start,start+1000,"$round/$skeleton")
            for (field in listOf("fDatePattern","fTimePattern","fDateTimeFormat")) assertEquals(privateField(a,field),privateField(b,field),"$round/$skeleton/$field")
        }
    }
    private fun privateField(value: Any,name: String): Any? = value.javaClass.getDeclaredField(name).also { it.isAccessible=true }.get(value)
    @Test fun customPatternsFallbackOrderClonesContextsAndDetachedGetters() {
        val ai = SourceInfo(); val bi = DateIntervalInfo()
        for ((skeleton, field, pattern) in listOf(Triple("yMd",Calendar.DATE,"latestFirst:y/M/d <> d"),Triple("ahm",Calendar.HOUR,"earliestFirst:h a <> h a"))) {
            ai.setIntervalPattern(skeleton,field,pattern); bi.setIntervalPattern(skeleton,field,pattern)
        }
        ai.setFallbackIntervalPattern("'{quoted}' {1} / {0}"); bi.setFallbackIntervalPattern("'{quoted}' {1} / {0}")
        val a = Source.getInstance("yMd",SourceLocale.US,ai); val b = DateIntervalFormat.getInstance("yMd",ULocale.US,bi)
        assertFalse(ai.isFrozen); assertFalse(bi.isFrozen())
        for (i in 0..6) {
            val ac = com.ibm.icu.text.DisplayContext.values()[i]; val bc = DisplayContext.entries[i]
            a.setContext(ac); b.setContext(bc)
            for (type in DisplayContext.Type.entries) assertEquals(a.getContext(com.ibm.icu.text.DisplayContext.Type.valueOf(type.name)).name,b.getContext(type).name)
            compare(a,b,start,start+86400000,"custom/$i")
        }
        val ac = a.clone(); val bc = b.clone()
        assertSame(privateField(b,"fInfo"),privateField(bc,"fInfo"))
        assertSame(privateField(b,"fIntervalPatterns"),privateField(bc,"fIntervalPatterns"))
        assertNotSame(privateField(b,"fDateFormat"),privateField(bc,"fDateFormat"))
        for (field in listOf("fFromCalendar","fToCalendar")) assertNotSame(privateField(b,field),privateField(bc,field))
        val zone = b.getTimeZone(); zone.setRawOffset(12345); assertEquals(a.timeZone.rawOffset,b.getTimeZone().getRawOffset())
        val format = b.getDateFormat() as SimpleDateFormat; format.applyPattern("'changed'"); assertEquals((a.dateFormat as com.ibm.icu.text.SimpleDateFormat).toPattern(),(b.getDateFormat() as SimpleDateFormat).toPattern())
        ac.timeZone = com.ibm.icu.util.TimeZone.getTimeZone("Asia/Tokyo"); bc.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"))
        compare(ac,bc,start,start+34560000000L,"clone")
        ac.setDateIntervalInfo(SourceInfo(SourceLocale.FRANCE)); bc.setDateIntervalInfo(DateIntervalInfo(ULocale.FRANCE))
        compare(ac,bc,start,start+86400000,"replace-info")
        assertTrue(b.getDateIntervalInfo().isFrozen())
        assertFailsWith<UnsupportedOperationException> { (b.getRawPatterns() as MutableMap).clear() }
    }
    @Test fun calendarAndTemporalOverloadsPatternsAndUnsupportedParsing() {
        val a = Source.getInstance("yMdHm",java.util.Locale.US); val b = DateIntervalFormat.getInstance("yMdHm",java.util.Locale.US)
        val ac = com.ibm.icu.util.Calendar.getInstance(com.ibm.icu.util.TimeZone.GMT_ZONE,SourceLocale.US)
        val bc = Calendar.getInstance(TimeZone.GMT_ZONE,ULocale.US)
        ac.timeInMillis=start; bc.setTimeInMillis(start)
        val ad=ac.clone();val bd=bc.clone();ad.add(Calendar.DATE,2);bd.add(Calendar.DATE,2)
        assertEquals(a.format(ac,ad,StringBuffer(),FieldPosition(0)).toString(),b.format(bc,bd,StringBuilder(),FormatFieldPosition(0)).toString())
        assertEquals(a.formatToValue(ac,ad).toString(),b.formatToValue(bc,bd).toString())
        val ap=com.ibm.icu.util.Output<String>("old");val bp=Output<String>("old")
        fun outcome(action:()->Any?):String=try { "ok:${action()}" }catch(e:Exception){ when(e){is NullPointerException->"null";else->e.javaClass.simpleName+":"+e.message} }
        assertEquals(outcome { a.getPatterns(ac,ad,ap) },outcome { b.getPatterns(bc,bd,bp) });assertEquals(ap.value,bp.value)
        assertNull(b.getPatterns(bc,bc,bp));assertEquals(ap.value,bp.value)
        val from=java.time.LocalDateTime.of(2024,1,15,10,0);val to=from.plusHours(3)
        assertEquals(a.format(from,to,StringBuffer(),FieldPosition(0)).toString(),b.format(from,to,StringBuilder(),FormatFieldPosition(0)).toString())
        assertEquals(a.formatToValue(from,to).toString(),b.formatToValue(from,to).toString())
        assertEquals(outcome { a.parseObject("date",java.text.ParsePosition(0)) },outcome { b.parseObject("date",ParsePosition(0)) })
        for (value in listOf(null,123,"text",Date())) assertEquals(outcome { a.format(value) },outcome { b.format(value) })
        val af=a.formatToValue(SourceInterval(start,start));val bf=b.formatToValue(DateInterval(start,start))
        assertEquals(outcome { af[-1] },outcome { bf[-1] });assertEquals(outcome { af[af.length] },outcome { bf[bf.length] })
        for((start,end) in listOf(-1 to 0,0 to af.length+1,2 to 1,0 to 0)) assertEquals(outcome { af.subSequence(start,end) },outcome { bf.subSequence(start,end) })
    }
    @Test fun repeatedFallbackArgumentsAndInvalidFactoryInputsMatch() {
        for(fallback in listOf("{0} / {1}","{1} / {0}","{0} / {0} / {1}","{1} + {0} + {1}","'{0}' {1} '{x}' {0}")) {
            val ai=SourceInfo().also { it.setFallbackIntervalPattern(fallback) };val bi=DateIntervalInfo().also { it.setFallbackIntervalPattern(fallback) }
            val a=Source.getInstance("yMdHms",SourceLocale.US,ai);val b=DateIntervalFormat.getInstance("yMdHms",ULocale.US,bi)
            a.timeZone=com.ibm.icu.util.TimeZone.GMT_ZONE;b.setTimeZone(TimeZone.GMT_ZONE)
            for(delta in listOf(1000L,86400000L)) compare(a,b,start,start+delta,fallback)
        }
        fun outcome(action:()->Any?):String=try { action();"ok" }catch(e:Exception){when(e){is NullPointerException->"null";is IndexOutOfBoundsException->"bounds";else->e.javaClass.simpleName+":"+e.message}}
        for(skeleton in listOf(null,"", "yMd", "#", "yyyyQQQQ")) for(id in listOf(null,"en_US")) {
            val al=id?.let(::SourceLocale);val bl=id?.let(::ULocale)
            assertEquals(outcome { Source.getInstance(skeleton,al) },outcome { DateIntervalFormat.getInstance(skeleton,bl) },"$skeleton/$id")
            assertEquals(outcome { Source.getInstance(skeleton,al,null) },outcome { DateIntervalFormat.getInstance(skeleton,bl,null) },"null-info/$skeleton/$id")
        }
    }
    @Test fun eraExtremesDstTransitionsAndCapitalizationMatch() {
        for(id in listOf("en_US","fr","ru")) for(skeleton in listOf("GyMMMd","yMdHm","Hmsz")) {
            val a=Source.getInstance(skeleton,SourceLocale(id),SourceInfo(SourceLocale(id)))
            val b=DateIntervalFormat.getInstance(skeleton,ULocale(id),DateIntervalInfo(ULocale(id)))
            a.timeZone=com.ibm.icu.util.TimeZone.getTimeZone("America/New_York");b.setTimeZone(TimeZone.getTimeZone("America/New_York"))
            for((from,to) in listOf(-62167219200000L to -62135596800000L,Long.MIN_VALUE to Long.MAX_VALUE,1710052200000L to 1710055800000L,1730611800000L to 1730615400000L)) compare(a,b,from,to,"$id/$skeleton/era-dst")
        }
        for(id in listOf("fr","ru","fi")) for(skeleton in listOf("MMMM","MMMMd","yMMMMdHm")) {
            val a=Source.getInstance(skeleton,SourceLocale(id),SourceInfo(SourceLocale(id)))
            val b=DateIntervalFormat.getInstance(skeleton,ULocale(id),DateIntervalInfo(ULocale(id)))
            a.timeZone=com.ibm.icu.util.TimeZone.GMT_ZONE;b.setTimeZone(TimeZone.GMT_ZONE)
            for(context in DisplayContext.entries.filter { it.type()==DisplayContext.Type.CAPITALIZATION }) {
                a.setContext(com.ibm.icu.text.DisplayContext.valueOf(context.name));b.setContext(context)
                compare(a,b,start,start+2678400000L,"$id/$skeleton/$context")
            }
        }
    }
    @Test fun adoptedFormatVirtualCallsAndFailureRetainSourcePatternMutation() {
        for(skeleton in listOf(null,"yMd","hm")) {
            val ae=mutableListOf<String>();val be=mutableListOf<String>()
            var failA=false;var failB=false
            val ad=object:com.ibm.icu.text.SimpleDateFormat("y/M/d",SourceLocale.US) {
                override fun toPattern():String { ae.add("pattern");return super.toPattern() }
                override fun getCalendar():com.ibm.icu.util.Calendar { ae.add("calendar");return super.getCalendar() }
                override fun setContext(context:com.ibm.icu.text.DisplayContext?) { ae.add("context:$context");super.setContext(context) }
                override fun applyPattern(pattern:String?) { ae.add("apply:$pattern");super.applyPattern(pattern);if(failA && pattern!!.contains("boom")) throw IllegalStateException("boom") }
                override fun format(cal:com.ibm.icu.util.Calendar?,out:StringBuffer?,p:FieldPosition?):StringBuffer=error("public format must be bypassed")
            }
            val bd=object:SimpleDateFormat("y/M/d",ULocale.US) {
                override fun toPattern():String? { be.add("pattern");return super.toPattern() }
                override fun getCalendar():Calendar? { be.add("calendar");return super.getCalendar() }
                override fun setContext(context:DisplayContext?) { be.add("context:$context");super.setContext(context) }
                override fun applyPattern(pattern:String?) { be.add("apply:$pattern");super.applyPattern(pattern);if(failB && pattern!!.contains("boom")) throw IllegalStateException("boom") }
                override fun format(calendar:Calendar?,output:StringBuilder?,position:FormatFieldPosition?):StringBuilder=error("public format must be bypassed")
            }
            val ai=object:SourceInfo() { override fun freeze():SourceInfo { ae.add("freeze");return super.freeze() } }
            val bi=object:DateIntervalInfo() { override fun freeze():DateIntervalInfo { be.add("freeze");return super.freeze() } }
            ai.setIntervalPattern("yMd",Calendar.DATE,"d 'boom' d");bi.setIntervalPattern("yMd",Calendar.DATE,"d 'boom' d")
            ae.clear();be.clear()
            val a=Source(skeleton,ai,ad);val b=DateIntervalFormat(skeleton,bi,bd)
            assertEquals(ae,be,"constructor/$skeleton")
            failA=true;failB=true;ae.clear();be.clear()
            val ao=StringBuffer("prefix");val bo=StringBuilder("prefix")
            fun outcome(action:()->Unit):String=try { action();"ok" }catch(e:Exception){e.javaClass.simpleName+":"+e.message}
            assertEquals(outcome { a.format(SourceInterval(start,start+86400000),ao,FieldPosition(0)) },outcome { b.format(DateInterval(start,start+86400000),bo,FormatFieldPosition(0)) })
            assertEquals(ae,be,"format/$skeleton");assertEquals(ao.toString(),bo.toString())
            assertEquals(ad.toPattern(),bd.toPattern());assertEquals(ae,be)
            assertEquals(a.javaClass,a.clone().javaClass);assertEquals(b.javaClass,b.clone().javaClass)
        }
    }
}
