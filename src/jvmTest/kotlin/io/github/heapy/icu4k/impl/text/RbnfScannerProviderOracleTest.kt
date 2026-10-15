// SPDX-License-Identifier: Unicode-3.0
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.impl.text

import com.ibm.icu.impl.text.RbnfScannerProviderImpl as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.text.*
import io.github.heapy.icu4k.util.ULocale
import kotlin.test.*

class RbnfScannerProviderOracleTest {
    private fun outcome(block: () -> Any?): Any? = try { block().let { if(it is IntArray) it.toList() else it } } catch(e: Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException) e.targetException else e
        when(t) { is NullPointerException -> "null"; is IndexOutOfBoundsException -> "bounds"; else -> t.javaClass.simpleName+":"+t.message }
    }
    @Test fun scannerMethodsOffsetsAndLegacyFindText2() {
        val texts=listOf<String?>(null,"","a","A","é","e\u0301"," -", "É-x!", "fifty-7", "zzÆ ae","😀a",charArrayOf(0xd800.toChar(),'a').concatToString())
        val keys=listOf<String?>(null,"","a","e","-","ex","ae","😀","fifty-")
        for(locale in listOf("en","de@collation=phonebook","fr","es@collation=traditional")) for(extras in listOf(null,"","&[last primary ignorable] = ' ' = '-'","&a=b","&")) {
            val a=Source().get(SourceLocale(locale),extras)
            val b=RbnfScannerProviderImpl().get(ULocale(locale),extras)!!
            val am=a.javaClass.getDeclaredMethod("findText2",String::class.java,String::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
            val bm=b.javaClass.getDeclaredMethod("findText2",String::class.java,String::class.java,Int::class.javaPrimitiveType).apply { isAccessible=true }
            for(text in texts) {
                assertEquals(outcome { a.allIgnorable(text) },outcome { b.allIgnorable(text) },"ignorable/$locale/$extras/$text")
                for(key in keys) {
                    assertEquals(outcome { a.prefixLength(text,key) },outcome { b.prefixLength(text,key) },"prefix/$locale/$extras/$text/$key")
                    for(start in listOf(-1,0,1,text?.length?:0,(text?.length?:0)+1)) {
                        assertEquals(outcome { a.findText(text,key,start) },outcome { b.findText(text,key,start) },"find/$locale/$extras/$text/$key/$start")
                        assertEquals(outcome { am.invoke(a,text,key,start) },outcome { bm.invoke(b,text,key,start) },"find2/$locale/$extras/$text/$key/$start")
                    }
                }
            }
        }
    }
    @Test fun cacheKeysNullableOverridesAndPortableLocaleFiltering() {
        val a=Source(); val b=RbnfScannerProviderImpl()
        assertEquals(outcome { a.get(null,null) },outcome { b.get(null,null) })
        assertSame(a.get(SourceLocale.ENGLISH,null),a.get(SourceLocale.ENGLISH,"null"))
        assertSame(b.get(ULocale.ENGLISH,null),b.get(ULocale.ENGLISH,"null"))
        var sourceCalls=0; var targetCalls=0
        val x=object : Source() { override fun createScanner(locale: SourceLocale?, extras: String?): com.ibm.icu.text.RbnfLenientScanner? { sourceCalls++; return null } }
        val y=object : RbnfScannerProviderImpl() { override fun createScanner(locale: ULocale?, extras: String?): RbnfLenientScanner? { targetCalls++; return null } }
        repeat(3) { assertNull(x.get(SourceLocale.ENGLISH,null)); assertNull(y.get(ULocale.ENGLISH,null)) }
        assertEquals(3,sourceCalls); assertEquals(sourceCalls,targetCalls)
        for(name in listOf("root","en","en_US_POSIX","ja_JP_JP","th_TH_TH","no_NO_NY","iw_IL","de@collation=phonebook","en@colNumeric=yes;colCaseFirst=upper","en@colStrength=invalid","de@unknown=value","en_US_ABC","en_Latn_US_ABC")) {
            assertEquals(outcome { val c=com.ibm.icu.text.Collator.getInstance(SourceLocale(name).toLocale()); c.getCollationKey("aAä 123😀").toByteArray().toList() },
                outcome { val c=Collator.getInstance(portableRbnfScannerLocale(ULocale(name))); c.getCollationKey("aAä 123😀")!!.toByteArray().toList() },name)
        }
    }
    @Test fun numberFormatDefaultProviderEndToEnd() {
        for(name in listOf("en","fr","de","es")) {
            val a=com.ibm.icu.text.RuleBasedNumberFormat(SourceLocale(name),1)
            val b=RuleBasedNumberFormat(ULocale(name),1)
            a.setLenientParseMode(true); b.setLenientParseMode(true)
            assertNotNull(a.getLenientScannerProvider()); assertNotNull(b.getLenientScannerProvider())
            val samples=(0L..30L).map { a.format(it) }+listOf(a.format(255L),a.format(1234L),"TWO HUNDRED FIFTY-FIVE","2 hundred fifty-5")
            for(text in samples.flatMap { listOf(it,it.uppercase(),it.replace("-"," "),it.replace(" ","")) }) {
                val ap=java.text.ParsePosition(0); val bp=io.github.heapy.icu4k.text.ParsePosition(0)
                assertEquals(a.parse(text,ap).toString(),b.parse(text,bp).toString(),"$name/$text")
                assertEquals(ap.index,bp.index,"$name/$text"); assertEquals(ap.errorIndex,bp.errorIndex)
            }
        }
    }
}
