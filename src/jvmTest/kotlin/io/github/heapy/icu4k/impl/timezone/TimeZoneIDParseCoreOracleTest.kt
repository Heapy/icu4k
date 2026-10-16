@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.text.TimeZoneFormat
import io.github.heapy.icu4k.text.ParsePosition
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class TimeZoneIDParseCoreOracleTest {
    private val full = TimeZoneFormat::class.java.getDeclaredMethod("parseZoneID",String::class.java,java.text.ParsePosition::class.java).also { it.isAccessible = true }
    private val short = TimeZoneFormat::class.java.getDeclaredMethod("parseShortZoneID",String::class.java,java.text.ParsePosition::class.java).also { it.isAccessible = true }
    private fun outcome(block: () -> Any?): Any? = try { block() } catch(e: Throwable) {
        val t = if (e is InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName+":"+t.message }
    }
    private fun compare(text: String?,start: Int,shortID: Boolean,error: Int = 37) {
        val a = java.text.ParsePosition(start).also { it.errorIndex = error }
        val b = ParsePosition(start).also { it.errorIndex = error }
        assertEquals(outcome { (if (shortID) short else full).invoke(null,text,a) },outcome { if (shortID) TimeZoneIDParseCore.parseShortZoneID(text,b) else TimeZoneIDParseCore.parseZoneID(text,b) },"$text/$start/$shortID")
        assertEquals(listOf(a.index,a.errorIndex),listOf(b.index,b.errorIndex))
    }
    @Test fun everyIDAliasShortIDAndPrefix() {
        val ids = com.ibm.icu.util.TimeZone.getAvailableIDs().toList()
        val shortIDs = com.ibm.icu.util.TimeZone.getAvailableIDs(com.ibm.icu.util.TimeZone.SystemTimeZoneType.CANONICAL,null,null).mapNotNull { com.ibm.icu.impl.ZoneMeta.getShortID(it) } + "unk"
        for ((shortID,values) in listOf(false to ids,true to shortIDs)) for (id in values) {
            compare(id,0,shortID)
            compare("!"+id.lowercase()+" suffix",1,shortID)
            compare(id.uppercase()+"_more",0,shortID)
            for (length in 0 until id.length) compare(id.substring(0,length),0,shortID)
        }
        for (text in listOf(null,"","Etc/Unknown","unk","GMT+05:30","US/Pacific","America/Indiana/Indianapolis","america/new_york","USLAX","UTC","GMT0","Zulu","Asia/Kolkata")) {
            for (start in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) for (shortID in listOf(false,true)) compare(text,start,shortID)
        }
        val random = Random(0x7821)
        repeat(3000) {
            val text = ids[random.nextInt(ids.size)].take(random.nextInt(25))+"𐐀"
            compare(text,random.nextInt(-1,text.length+2),random.nextBoolean())
        }
        for (method in listOf(full,short)) assertEquals(outcome { method.invoke(null,null,null) },outcome { if (method == full) TimeZoneIDParseCore.parseZoneID(null,null) else TimeZoneIDParseCore.parseShortZoneID(null,null) })
    }
}
