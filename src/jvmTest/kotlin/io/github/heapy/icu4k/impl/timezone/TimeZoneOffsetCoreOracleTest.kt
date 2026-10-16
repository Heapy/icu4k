@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.text.TimeZoneFormat as Source
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.Output
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class TimeZoneOffsetCoreOracleTest {
    private val source = Source.getInstance(com.ibm.icu.util.ULocale.US)
    private val fieldsClass = Class.forName("com.ibm.icu.text.TimeZoneFormat\$OffsetFields")
    private val iso = Source::class.java.getDeclaredMethod("parseOffsetISO8601",String::class.java,java.text.ParsePosition::class.java,Boolean::class.javaPrimitiveType,com.ibm.icu.util.Output::class.java).also { it.isAccessible = true }
    private val abutting = Source::class.java.getDeclaredMethod("parseAbuttingAsciiOffsetFields",String::class.java,java.text.ParsePosition::class.java,fieldsClass,fieldsClass,Boolean::class.javaPrimitiveType).also { it.isAccessible = true }
    private val separated = Source::class.java.getDeclaredMethod("parseAsciiOffsetFields",String::class.java,java.text.ParsePosition::class.java,Char::class.javaPrimitiveType,fieldsClass,fieldsClass).also { it.isAccessible = true }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when(t) {
            is NullPointerException -> "null"
            is IndexOutOfBoundsException -> "bounds"
            is AssertionError -> "assertion"
            else -> t.javaClass.simpleName + ":" + t.message
        }
    }
    @Test fun formattingFlagsAndOverflow() {
        val values = mutableSetOf(Int.MIN_VALUE,Int.MIN_VALUE+1,Int.MAX_VALUE,-86_400_001,-86_400_000,86_400_000,86_400_001,0)
        for (unit in listOf(1000,60_000,3_600_000,86_400_000)) for (sign in listOf(-1,1)) for (delta in -2..2) values.add(sign*unit+delta)
        val random = Random(0x1407)
        repeat(4000) { values.add(random.nextInt(-90_000_000,90_000_000)) }
        for (offset in values) for (flags in 0..15) {
            val basic = flags and 1 != 0
            val utc = flags and 2 != 0
            val short = flags and 4 != 0
            val ignore = flags and 8 != 0
            val expected = outcome { if (basic) source.formatOffsetISO8601Basic(offset,utc,short,ignore) else source.formatOffsetISO8601Extended(offset,utc,short,ignore) }
            assertEquals(expected,outcome { TimeZoneOffsetCore.formatOffsetISO8601(offset,basic,utc,short,ignore) },"$offset/$flags")
        }
    }
    private fun compareISO(text: String?, start: Int, extendedOnly: Boolean, error: Int) {
        val a = java.text.ParsePosition(start).also { it.errorIndex = error }
        val b = ParsePosition(start).also { it.errorIndex = error }
        val ad = com.ibm.icu.util.Output<Boolean>(true)
        val bd = Output(true)
        assertEquals(outcome { iso.invoke(null,text,a,extendedOnly,ad) },outcome { TimeZoneOffsetCore.parseOffsetISO8601(text,b,extendedOnly,bd) },"$text/$start/$extendedOnly")
        assertEquals(listOf(a.index,a.errorIndex,ad.value),listOf(b.index,b.errorIndex,bd.value),"$text/$start/$extendedOnly")
    }
    @Test fun allShortDigitPrefixesAndLongerFuzz() {
        for (number in 0..99_999) {
            val digits = number.toString().padStart(5,'0')
            compareISO("+$digits!",0,false,-1)
            compareISO("-$digits!",0,true,57)
        }
        val random = Random(0x4841)
        val chars = "0123456789:+-Zzabc ١２\uD800\uDC00"
        repeat(12000) {
            val text = buildString { repeat(random.nextInt(16)) { append(chars[random.nextInt(chars.length)]) } }
            compareISO(text,random.nextInt(-2,text.length+3),random.nextBoolean(),random.nextInt(-2,10))
        }
        for (text in listOf(null,"","Z","z","+","-","+1:23","+23:59:59","+236099","+24:00","+99:59","+12::34","+12:34:56:78","+1234567","+١٢","\uD835\uDFD8")) {
            for (start in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) for (extended in listOf(false,true)) compareISO(text,start,extended,31)
        }
        val ad = com.ibm.icu.util.Output<Boolean>(true)
        val bd = Output(true)
        assertEquals(outcome { iso.invoke(null,null,null,false,ad) },outcome { TimeZoneOffsetCore.parseOffsetISO8601(null,null,false,bd) })
        assertEquals(ad.value,bd.value)
    }
    @Test fun numericHelpersAllFieldBoundsAndSeparators() {
        val random = Random(0x1903)
        val texts = mutableListOf("","0","1:23","12:34:56","235959","246099","999999","01.23.45","12::34","12:3","12:34:5","+123456","00","1234567")
        repeat(1800) { texts.add(buildString { repeat(random.nextInt(10)) { append("0123456789:. x"[random.nextInt(14)]) } }) }
        for (text in texts) for (min in TimeZoneOffsetCore.OffsetFields.entries) for (max in TimeZoneOffsetCore.OffsetFields.entries) {
            for (fixed in listOf(false,true)) {
                val a = java.text.ParsePosition(0).also { it.errorIndex = 19 }
                val b = ParsePosition(0).also { it.errorIndex = 19 }
                assertEquals(outcome { abutting.invoke(null,text,a,fieldsClass.enumConstants[min.ordinal],fieldsClass.enumConstants[max.ordinal],fixed) },outcome { TimeZoneOffsetCore.parseAbuttingAsciiOffsetFields(text,b,min,max,fixed) },"$text/$min/$max/$fixed")
                assertEquals(listOf(a.index,a.errorIndex),listOf(b.index,b.errorIndex))
            }
            for (sep in listOf(':','.','0')) {
                val a = java.text.ParsePosition(0).also { it.errorIndex = 19 }
                val b = ParsePosition(0).also { it.errorIndex = 19 }
                assertEquals(outcome { separated.invoke(null,text,a,sep,fieldsClass.enumConstants[min.ordinal],fieldsClass.enumConstants[max.ordinal]) },outcome { TimeZoneOffsetCore.parseAsciiOffsetFields(text,b,sep,min,max) },"$text/$min/$max/$sep")
                assertEquals(listOf(a.index,a.errorIndex),listOf(b.index,b.errorIndex))
            }
        }
    }
}
