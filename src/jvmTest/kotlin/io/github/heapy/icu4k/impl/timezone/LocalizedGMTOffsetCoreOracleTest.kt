@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.timezone

import com.ibm.icu.text.TimeZoneFormat as Source
import com.ibm.icu.text.TimeZoneFormat.GMTOffsetPatternType as SType
import io.github.heapy.icu4k.impl.ICUData
import io.github.heapy.icu4k.text.ParsePosition
import io.github.heapy.icu4k.util.*
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random
import kotlin.test.*

class LocalizedGMTOffsetCoreOracleTest {
    private fun source(locale: String): Source = object : Source(com.ibm.icu.util.ULocale(locale)) { }
    private fun target(locale: String) = LocalizedGMTOffsetCore(ULocale(locale))
    private fun type(type: SType) = LocalizedGMTOffsetCore.GMTOffsetPatternType.valueOf(type.name)
    private fun outcome(block: () -> Any?): Any? = try { block() } catch (e: Throwable) {
        val error = if (e is InvocationTargetException) e.targetException else e
        when(error) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; is AssertionError -> "assertion"; else -> error.javaClass.simpleName+":"+error.message }
    }
    private fun state(a: Source,b: LocalizedGMTOffsetCore) {
        assertEquals(a.gmtPattern,b.getGMTPattern())
        assertEquals(a.gmtOffsetDigits,b.getGMTOffsetDigits())
        assertEquals(a.gmtZeroFormat,b.getGMTZeroFormat())
        assertEquals(a.isFrozen,b.isFrozen())
        for (t in SType.entries) assertEquals(a.getGMTOffsetPattern(t),b.getGMTOffsetPattern(type(t)))
    }
    private fun format(a: Source,b: LocalizedGMTOffsetCore,offset: Int) {
        assertEquals(outcome { a.formatOffsetLocalizedGMT(offset) },outcome { b.formatOffsetLocalizedGMT(offset) },"long/$offset")
        assertEquals(outcome { a.formatOffsetShortLocalizedGMT(offset) },outcome { b.formatOffsetShortLocalizedGMT(offset) },"short/$offset")
    }
    private val parse = Source::class.java.getDeclaredMethod("parseOffsetLocalizedGMT",String::class.java,java.text.ParsePosition::class.java,Boolean::class.javaPrimitiveType,com.ibm.icu.util.Output::class.java).also { it.isAccessible = true }
    private fun parse(a: Source,b: LocalizedGMTOffsetCore,text: String?,start: Int = 0,short: Boolean = false,error: Int = -1) {
        val ap = java.text.ParsePosition(start).also { it.errorIndex = error }
        val bp = ParsePosition(start).also { it.errorIndex = error }
        val ad = com.ibm.icu.util.Output<Boolean>(true)
        val bd = Output(true)
        assertEquals(outcome { parse.invoke(a,text,ap,short,ad) },outcome { b.parseOffsetLocalizedGMT(text,bp,short,bd) },"$text/$start/$short")
        assertEquals(listOf(ap.index,ap.errorIndex,ad.value),listOf(bp.index,bp.errorIndex,bd.value),"positions/$text/$start/$short")
    }
    @Test fun allLocalePatternsDigitsFormattingAndParsing() {
        val files = mutableListOf<String>()
        ICUData.visitResources(ICUData.ICU_ZONE_BASE_NAME+"/") { files.add(it) }
        val locales = files.filter { it != "pool.res" && it != "res_index.res" }.map { it.removeSuffix(".res") } + listOf("en@numbers=mathbold","ar@numbers=latn","en@numbers=roman","en@numbers=hanidec")
        val offsets = listOf(Int.MIN_VALUE,Int.MAX_VALUE,-86_400_001,-86_400_000,-86_399_999,-19_845_987,-1001,-999,0,999,1001,60_000,3_600_000,19_845_987,86_399_999,86_400_000)
        for (locale in locales) {
            val a = source(locale)
            val b = target(locale)
            try { state(a,b) } catch (e: AssertionError) { throw AssertionError("locale=$locale",e) }
            for (offset in offsets) {
                format(a,b,offset)
                if (offset in -86_399_999..86_399_999) {
                    for (text in listOf(a.formatOffsetLocalizedGMT(offset),a.formatOffsetShortLocalizedGMT(offset))) {
                        parse(a,b,text)
                        parse(a,b,"!$text suffix",1,true,31)
                    }
                }
            }
            for (text in listOf("GMT","UTC","ut","GMT+bad","UTC+١٢:٣٤","UT-1:23:45","GMT+12:60","GMT+12:34:6","GMT+2460","GMT-999999")) parse(a,b,text)
        }
    }
    @Test fun customPatternsQuotesAndFailedMutation() {
        val random = Random(0x4271)
        val patterns = mutableListOf<String?>(null,"","H","HH","HHH","+H:mm","+HH:mm:ss","+Hmmss","+Hms","+HHmmss","mH","H'H'm","'+'HH':'mm","''HH:mm","'H'H'mm'","H'","HH''mm","\u200e+HH:mm","+mm:HH","sHm","HHmmHH")
        repeat(450) { patterns.add(buildString { repeat(random.nextInt(18)) { append("Hms:+-'x "[random.nextInt(9)]) } }) }
        val a = source("en")
        val b = target("en")
        for (t in SType.entries) for (pattern in patterns) {
            assertEquals(outcome { a.setGMTOffsetPattern(t,pattern); Unit },outcome { b.setGMTOffsetPattern(type(t),pattern); Unit },"$t/$pattern")
            state(a,b)
            for (offset in listOf(0,19_845_000,-19_845_000,3_600_000)) {
                format(a,b,offset)
                parse(a,b,a.formatOffsetLocalizedGMT(offset))
            }
        }
        for (pattern in listOf(null,"","GMT","{0}","'{0}'","a''{0}''b","x{0}{0}y","𐐀{0}𐐀")) {
            assertEquals(outcome { a.setGMTPattern(pattern); Unit },outcome { b.setGMTPattern(pattern); Unit })
            state(a,b)
            format(a,b,19_845_000)
            parse(a,b,a.formatOffsetLocalizedGMT(19_845_000).lowercase())
        }
        assertEquals(outcome { a.setGMTOffsetPattern(null,null) },outcome { b.setGMTOffsetPattern(null,null) })
        assertEquals(outcome { a.getGMTOffsetPattern(null) },outcome { b.getGMTOffsetPattern(null) })
    }
    @Test fun customDigitsUnicodeFallbackAndMalformedInputs() {
        val a = source("en")
        val b = target("en")
        val random = Random(0x1987)
        for (digits in listOf(null,"","123","0123456789","٩٨٧٦٥٤٣٢١٠","abcdefghij","aaaaaaaaaa","𝟎𝟏𝟐𝟑𝟒𝟓𝟔𝟕𝟖𝟗",String(CharArray(10) { (0xd800+it).toChar() }))) {
            assertEquals(outcome { a.setGMTOffsetDigits(digits); Unit },outcome { b.setGMTOffsetDigits(digits); Unit })
            state(a,b)
            for (offset in listOf(0,19_845_000,-19_845_000,86_399_000)) { format(a,b,offset); parse(a,b,a.formatOffsetLocalizedGMT(offset)) }
            for (text in listOf(null,"","GMT","GMT+bad","UTC+1:23","GMT+12:60","GMT+12:34:6","UT+٢٣:٥٩:٥٩","GMT+１２３４５６","GMT+𝟏𝟐:𝟑𝟒","GMT+1234567","GMT+aaaaaaaa","GMT+01:23","GMT+24:00")) {
                for (start in listOf(Int.MIN_VALUE,-1,0,1,Int.MAX_VALUE)) parse(a,b,text,start,true,73)
            }
            val chars = "GMTUTCgmtutc0123456789:+- abc١٢３４'\u200e\uD800\uDC00"
            repeat(800) {
                val text = buildString { repeat(random.nextInt(20)) { append(chars[random.nextInt(chars.length)]) } }
                parse(a,b,text,random.nextInt(-1,text.length+2),random.nextBoolean(),19)
                parse(a,b,"GMT+$text")
            }
        }
        for (zero in listOf(null,"","ZERO","GMT+","'x'","𝟎")) {
            assertEquals(outcome { a.setGMTZeroFormat(zero); Unit },outcome { b.setGMTZeroFormat(zero); Unit })
            state(a,b); parse(a,b,a.gmtZeroFormat.lowercase()); format(a,b,0)
        }
        val ad = com.ibm.icu.util.Output<Boolean>(true)
        val bd = Output(true)
        assertEquals(outcome { parse.invoke(a,null,null,false,ad) },outcome { b.parseOffsetLocalizedGMT(null,null,false,bd) })
        assertEquals(ad.value,bd.value)
    }
    @Test fun frozenAndShallowClonePatternSharing() {
        val a = source("en").freeze()
        val b = target("en").freeze()
        val ac = a.cloneAsThawed()
        val bc = b.cloneAsThawed()
        ac.setGMTOffsetPattern(SType.POSITIVE_HMS,"+Hmmss")
        bc.setGMTOffsetPattern(type(SType.POSITIVE_HMS),"+Hmmss")
        state(a,b); state(ac,bc)
        for (text in listOf("GMT+01020","GMT+123456","GMT+12345")) { parse(a,b,text); parse(ac,bc,text) }
        format(a,b,19_845_000); format(ac,bc,19_845_000)
        ac.setGMTOffsetDigits("abcdefghij"); bc.setGMTOffsetDigits("abcdefghij")
        ac.setGMTPattern("X{0}Y"); bc.setGMTPattern("X{0}Y")
        ac.setGMTZeroFormat("zero"); bc.setGMTZeroFormat("zero")
        state(a,b); state(ac,bc)
        val aclone = a.clone()
        val bclone = b.clone()
        state(aclone,bclone)
        assertEquals(outcome { a.setGMTPattern(null) },outcome { b.setGMTPattern(null) })
        assertEquals(outcome { a.setGMTOffsetPattern(null,null) },outcome { b.setGMTOffsetPattern(null,null) })
        assertEquals(outcome { a.setGMTOffsetDigits(null) },outcome { b.setGMTOffsetDigits(null) })
        assertEquals(outcome { a.setGMTZeroFormat(null) },outcome { b.setGMTZeroFormat(null) })
    }
    @Test fun portableRegionComparisonAgainstJava() {
        val values = listOf("","ABC","abc","Iİıi","Σσς","ßẞ","𐐀𐐨","X𐐀Y","X𐐨Y","\uD800","\uDC00")
        for (a in values) for (b in values) for (start in -1..a.length+1) for (offset in -1..b.length+1) for (len in -1..5) {
            assertEquals(a.regionMatches(start,b,offset,len,true),LocalizedGMTOffsetCore.portableRegionMatches(a,start,b,offset,len),"$a/$b/$start/$offset/$len")
        }
    }
}
