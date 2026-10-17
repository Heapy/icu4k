// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.util.CaseInsensitiveString
import kotlin.test.*
import java.lang.reflect.InvocationTargetException

class TransliterationParserDependenciesOracleTest {
    @Test fun fullFoldedStringKeys() {
        val values=listOf("","ABC","abc","ß","SS","İ","i\u0307","Σςσ","σσσ","ﬃ","ffi","𐐀","𐐨",charArrayOf('\ud800','A').concatToString())
        for(a in values)for(b in values) {
            val sa=com.ibm.icu.util.CaseInsensitiveString(a);val sb=com.ibm.icu.util.CaseInsensitiveString(b)
            val pa=CaseInsensitiveString(a);val pb=CaseInsensitiveString(b)
            assertEquals(sa==sb,pa==pb);assertEquals(sa.hashCode(),pa.hashCode());assertEquals(sa.string,pa.getString());assertEquals(sa.toString(),pa.toString())
            assertFalse(pa.equals(a));assertTrue(pa.equals(pa));assertFalse(pa.equals(null))
        }
    }
    private fun outcome(action:()->Any?):String=try{"OK:"+action()}catch(e:Throwable){"ERR:"+if(e is IndexOutOfBoundsException)"bounds"else e::class.java.simpleName}
    @Test fun parsingUtilities() {
        val texts=listOf("","0","00","01","09","0x","0xA","0Xff","2147483647","2147483648","9999999999999999","١٢٣","𐒠𐒡","Ab_9","a\u200Cb","𐐀abc","'a;b';c","a\\;b;c","use VARIABLE range 0xF000 0177777;","use  variable range 0 0;")
        for(text in texts)for(start in 0..text.length)for(limit in start..text.length) {
            val sp=intArrayOf(start);val pp=intArrayOf(start)
            assertEquals(com.ibm.icu.impl.Utility.parseInteger(text,sp,limit),Utility.parseInteger(text,pp,limit));assertContentEquals(sp,pp)
            assertEquals(com.ibm.icu.impl.Utility.quotedIndexOf(text,start,limit,";b"),Utility.quotedIndexOf(text,start,limit,";b"))
            for(pattern in listOf("use ","~variable range # #~;","#","~","a")) {
                val si=IntArray(2);val pi=IntArray(2)
                assertEquals(com.ibm.icu.impl.Utility.parsePattern(text,start,limit,pattern,si),Utility.parsePattern(text,start,limit,pattern,pi));assertContentEquals(si,pi)
            }
            sp[0]=start;pp[0]=start
            assertEquals(com.ibm.icu.impl.Utility.parseUnicodeIdentifier(text,sp),Utility.parseUnicodeIdentifier(text,pp));assertContentEquals(sp,pp)
            for(c in listOf(';','a','(')) {
                sp[0]=start;pp[0]=start
                assertEquals(com.ibm.icu.impl.Utility.parseChar(text,sp,c),Utility.parseChar(text,pp,c));assertContentEquals(sp,pp)
            }
        }
    }
    @Test fun quantifiersAndZeroWidthCallbackState() {
        val qc=Class.forName("com.ibm.icu.text.Quantifier")
        val ctor=qc.getDeclaredConstructor(com.ibm.icu.text.UnicodeMatcher::class.java,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply{isAccessible=true}
        for(min in listOf(-1,0,1,2,4))for(max in listOf(-1,0,1,3,Int.MAX_VALUE)) {
            for(pattern in listOf("[]","[a]","[a{ab}]")) {
                var source: Any?=null;var port: Quantifier?=null
                fun constructSource(): Any?=try{ctor.newInstance(com.ibm.icu.text.UnicodeSet(pattern),min,max)}catch(e:InvocationTargetException){throw e.cause!!}
                assertEquals(outcome{source=constructSource();null},outcome{port=Quantifier(UnicodeSet(pattern),min,max);null})
                if(source==null||port==null)continue
                val sm=source as com.ibm.icu.text.UnicodeMatcher
                for(escape in listOf(false,true))assertEquals(sm.toPattern(escape),port!!.toPattern(escape))
                for(value in listOf("","a","aaa","ab","abab","xaaa"))for(start in 0..value.length)for(limit in -1..value.length)for(inc in listOf(false,true)) {
                    val sp=intArrayOf(start);val pp=intArrayOf(start)
                    assertEquals(outcome{sm.matches(com.ibm.icu.text.ReplaceableString(value),sp,limit,inc)},outcome{port!!.matches(ReplaceableString(value),pp,limit,inc)})
                    assertContentEquals(sp,pp)
                }
                val ss=com.ibm.icu.text.UnicodeSet();val ps=UnicodeSet();sm.addMatchSetTo(ss);port!!.addMatchSetTo(ps)
                assertEquals(ss.toPattern(false),ps.toPattern(false))
                for(v in 0..255)assertEquals(sm.matchesIndexValue(v),port!!.matchesIndexValue(v))
            }
        }
        for(status in 0..2)for(min in 0..2)for(inc in listOf(false,true)) {
            val sourceMatcher=object:com.ibm.icu.text.UnicodeMatcher {
                override fun matches(t:com.ibm.icu.text.Replaceable,o:IntArray,l:Int,i:Boolean)=status
                override fun toPattern(e:Boolean)="x"
                override fun matchesIndexValue(v:Int)=true
                override fun addMatchSetTo(s:com.ibm.icu.text.UnicodeSet){}
            }
            val portMatcher=object:UnicodeMatcher {
                override fun matches(text:Replaceable,offset:IntArray,limit:Int,incremental:Boolean)=status
                override fun toPattern(escapeUnprintable:Boolean)="x"
                override fun matchesIndexValue(v:Int)=true
                override fun addMatchSetTo(toUnionTo:UnicodeSet){}
            }
            val sm=ctor.newInstance(sourceMatcher,min,Int.MAX_VALUE) as com.ibm.icu.text.UnicodeMatcher
            val pm=Quantifier(portMatcher,min,Int.MAX_VALUE)
            val sp=intArrayOf(0);val pp=intArrayOf(0)
            assertEquals(sm.matches(com.ibm.icu.text.ReplaceableString("a"),sp,1,inc),pm.matches(ReplaceableString("a"),pp,1,inc));assertContentEquals(sp,pp)
        }
    }
}
