package io.github.heapy.icu4k.impl.calendar

import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.FormatParser
import io.github.heapy.icu4k.impl.calendar.DateTimePatternSyntaxCore.VariableField
import kotlin.test.*

class DateTimePatternSyntaxCoreTest {
    @Test fun fieldClassificationStrictLengthsAndParserStrictQuirk() {
        assertEquals(83,DateTimePatternSyntaxCore.types.size)
        assertEquals(3,VariableField("LLLL").getType()); assertFalse(VariableField("LLLL").isNumeric()); assertTrue(VariableField("MM").isNumeric())
        assertEquals(15,VariableField("VVVVV").getType()); assertFailsWith<IllegalArgumentException> { VariableField("VVVVV",true) }
        val parser=FormatParser(); assertSame(parser,parser.set("VVVVV",true)); assertEquals("VVVVV",parser.getItems().single().toString())
        assertFailsWith<IllegalArgumentException> { VariableField("yM") }; assertFailsWith<NullPointerException> { VariableField(null) }
        assertEquals("-1",VariableField.getCanonicalCode(-1)); assertEquals("v",VariableField.getCanonicalCode(15))
    }
    @Test fun parserMutableItemsQuotedScriptsAndPartialFailure() {
        val parser=FormatParser().set("yyyy-MM-dd 'at' HH:mm")
        assertEquals("yyyy-MM-dd 'at' HH:mm",parser.toString()); assertTrue(parser.hasDateAndTimeFields())
        assertEquals("'абв'",parser.quoteLiteral("абв")); assertEquals("日本語",parser.quoteLiteral("日本語"))
        val alias=parser.getItems(); parser.set("y M"); assertSame(alias,parser.getItems())
        alias.add(null); assertFailsWith<NullPointerException> { parser.toString() }; alias.removeAt(alias.lastIndex)
        alias.add(42); assertEquals("y M42",parser.toString())
        assertFailsWith<IndexOutOfBoundsException> { parser.toString(-1,2) }; assertEquals("",parser.toString(3,1))
        assertFailsWith<IllegalArgumentException> { parser.set("y j M") }; assertEquals(listOf("y"," "),parser.getItems().map { it.toString() })
        assertFailsWith<NullPointerException> { parser.set(null) }; assertTrue(alias.isEmpty())
    }
    @Test fun standaloneIcu4jSkeletonCanonicalAndBaseSnapshots() {
        val cases=listOf(
            listOf("hmm","hmm","hmm","hm"),listOf("mSSS","msSSS","msSSS","msS"),
            listOf("HB","H","H","H"),listOf("rUy","r","y","r"),
            listOf("yyyy-MM-dd 'at' HH:mm","yyyyMMddHHmm","yyyyMMddHHmm","yMdHm"),
            listOf("QQQ LLLL ccc Kmm","QQQLLLLcccKmm","QQQMMMMEEEhmm","QLLLLcccKm"),
        )
        val parser=FormatParser(); val matcher=DateTimePatternMatcherCore()
        for(row in cases) {
            matcher.set(row[0],parser,true)
            assertEquals(row[1],matcher.toString()); assertEquals(row[2],matcher.toCanonicalString()); assertEquals(row[3],matcher.getBasePattern())
        }
        matcher.set("hmm",parser,false)
        assertEquals('a',matcher.original.getFieldChar(10)); assertEquals(1,matcher.original.getFieldLength(10))
        assertTrue(matcher.getFieldMask() and (1 shl 10)!=0)
        matcher.set("HB",parser,false); assertTrue(matcher.original.isFieldEmpty(10)); assertFalse(matcher.getFieldMask() and (1 shl 10)!=0)
    }
    @Test fun duplicateConflictsRetainPartialStateAndCanBeReused() {
        val matcher=DateTimePatternMatcherCore(); val parser=FormatParser()
        assertFailsWith<IllegalArgumentException> { matcher.set("yYMd",parser,false) }
        assertEquals("y",matcher.toString()); assertEquals(2,matcher.getFieldMask()); assertTrue(matcher.original.isFieldEmpty(3))
        assertEquals("yMd",matcher.set("yYMd",parser,true).toString())
        assertFailsWith<IllegalArgumentException> { matcher.set("HBa",parser,false) }; assertEquals("BH",matcher.toString())
        assertEquals("H",matcher.set("HBa",parser,true).toString())
        assertFailsWith<NullPointerException> { matcher.set("y",null,false) }; assertEquals(0,matcher.getFieldMask()); assertEquals("",matcher.toString())
    }
    @Test fun masksDistancesReverseOrderingAndStaleBaseExtraction() {
        val parser=FormatParser(); val a=DateTimePatternMatcherCore().set("yMd",parser,false); val b=DateTimePatternMatcherCore().set("yM",parser,false)
        val info=DateTimePatternDistanceInfo()
        assertEquals(0x1000,a.getDistance(b,-1,info)); assertEquals(1 shl 7,info.missingFieldMask); assertEquals(0,info.extraFieldMask)
        assertEquals("missingFieldMask: Day , extraFieldMask: ",info.toString())
        assertEquals(0x10000,b.getDistance(a,-1,info)); assertEquals(1 shl 7,info.extraFieldMask)
        assertEquals(-1,a.compareTo(b)); assertEquals(1,b.compareTo(a))
        val extracted=DateTimePatternMatcherCore().set("hmm",parser,false)
        extracted.extractFrom(a,1 shl 1)
        assertEquals("y",extracted.toString()); assertEquals("hm",extracted.getBasePattern())
        assertEquals("y",extracted.toCanonicalString()); assertEquals(2,extracted.getFieldMask())
        val copy=DateTimePatternDistanceInfo(); copy.setTo(info); assertEquals(info.toString(),copy.toString()); copy.clear(); assertEquals(0,copy.extraFieldMask)
    }
    @Test fun signedByteStorageAndOptionalSourceAssertions() {
        val parser=FormatParser(); val matcher=DateTimePatternMatcherCore(false).set("y".repeat(128),parser,false)
        assertEquals(-128,matcher.original.getFieldLength(1)); assertEquals("",matcher.toString()); assertEquals(2,matcher.getFieldMask())
        matcher.set("y".repeat(256),parser,false); assertEquals(0,matcher.original.getFieldLength(1)); assertEquals("",matcher.toString()); assertEquals(2,matcher.getFieldMask())
        assertFailsWith<AssertionError> { DateTimePatternMatcherCore(true).set("y".repeat(128),parser,false) }
        val fields=DateTimeSkeletonFields(false); fields.populate(0,'\u00ff',257)
        assertEquals('\uffff',fields.getFieldChar(0)); assertEquals(1,fields.getFieldLength(0)); assertEquals("\uffff",fields.toString())
        val duplicate=DateTimeSkeletonFields(false); duplicate.copyFieldFrom(fields,0); assertEquals(fields,duplicate); assertEquals(fields.hashCode(),duplicate.hashCode())
        duplicate.clearField(0); assertTrue(duplicate.isFieldEmpty(0)); assertFailsWith<IndexOutOfBoundsException> { duplicate.getFieldLength(16) }
    }
}
