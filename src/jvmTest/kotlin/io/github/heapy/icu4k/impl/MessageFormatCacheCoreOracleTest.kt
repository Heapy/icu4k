@file:Suppress("UNCHECKED_CAST")
package io.github.heapy.icu4k.impl

import com.ibm.icu.text.MessageFormat as Source
import com.ibm.icu.util.ULocale as SourceLocale
import io.github.heapy.icu4k.text.MessagePattern
import kotlin.test.*

class MessageFormatCacheCoreOracleTest {
    private fun outcome(block:()->Any?):Any?=try { block() } catch(e:Throwable) {
        val t=if(e is java.lang.reflect.InvocationTargetException)e.targetException else e
        if(t is VirtualMachineError)throw t
        when(t) { is NullPointerException->"null"; is IndexOutOfBoundsException->"bounds"; else->t.javaClass.simpleName+":"+t.message }
    }
    private val create=Source::class.java.getDeclaredMethod("createAppropriateFormat",String::class.java,String::class.java).also { it.isAccessible=true }
    private fun sourceField(a:Source,name:String):Any?=Source::class.java.getDeclaredField(name).also { it.isAccessible=true }.get(a)
    private fun snapshot(value:Any?):Any?=when(value) {
        is java.text.Format->listOf(value.javaClass.simpleName,outcome { value.format(1234) })
        else->value
    }
    private fun compare(a:Source,b:MessageFormatCacheCore<java.text.Format>) {
        assertEquals(a.formats.map(::snapshot),b.formats().map(::snapshot))
        assertEquals(outcome { a.formatsByArgumentIndex.map(::snapshot) },outcome { b.formatsByArgumentIndex().map(::snapshot) })
        assertEquals(a.argumentNames,b.argumentNames())
        val cache=sourceField(a,"cachedFormatters") as Map<Int,java.text.Format?>?
        assertEquals(cache?.keys,b.cachedFormatters?.keys)
        assertEquals(cache?.mapValues { snapshot(it.value) },b.cachedFormatters?.mapValues { snapshot(it.value) })
        assertEquals(sourceField(a,"customFormatArgStarts"),b.customFormatArgStarts)
        for(name in listOf(null,"0","1","2","n","name","01","-1","bad name","")) assertEquals(outcome { snapshot(a.getFormatByArgumentName(name)) },outcome { snapshot(b.formatByArgumentName(name)) },name)
    }
    @Test fun allSettersGettersNestedStockFormatsAndCloneMarkers() {
        for(text in listOf("","{2,number} {0} {2,number,integer}","{n,plural,other{{n,number,0.0}}} {n,number} {0}","{name} {1,select,other{{0,date,short}}} {name,number}")) {
            val a=Source(text,SourceLocale.US)
            val pattern=MessagePattern(text)
            val factory=Source("",SourceLocale.US)
            val b=MessageFormatCacheCore<java.text.Format>({pattern}) { type,style->create.invoke(factory,type,style) as java.text.Format? }
            b.cacheExplicitFormats(); compare(a,b)
            val format=java.text.DecimalFormat("00000")
            for(name in listOf("2","n","name","01","bad name","")) {
                assertEquals(outcome { a.setFormatByArgumentName(name,format) },outcome { b.setFormatByArgumentName(name,format) })
                compare(a,b)
            }
            for(index in -1..4) {
                assertEquals(outcome { a.setFormatByArgumentIndex(index,null) },outcome { b.setFormatByArgumentIndex(index,null) })
                compare(a,b)
                assertEquals(outcome { a.setFormat(index,format) },outcome { b.setFormat(index,format) })
                compare(a,b)
            }
            assertEquals(outcome { a.setFormatsByArgumentIndex(arrayOf(format,null)) },outcome { b.setFormatsByArgumentIndex(listOf(format,null)) }); compare(a,b)
            a.setFormatsByArgumentName(mapOf("name" to null,"0" to format)); b.setFormatsByArgumentName(mapOf("name" to null,"0" to format)); compare(a,b)
            a.formats=arrayOf(null,format); b.setFormats(listOf(null,format)); compare(a,b)
            val ac=a.clone() as Source
            val bc=MessageFormatCacheCore<java.text.Format>({pattern}) { _,_->null }; bc.copyStateFrom(b)
            compare(ac,bc)
        }
    }
    @Test fun reentrantMapSetterUsesTheCurrentMessagePattern() {
        val a=Source("{old} {1,number}",SourceLocale.US)
        var pattern=MessagePattern("{old} {1,number}")
        val factory=Source("",SourceLocale.US)
        val b=MessageFormatCacheCore<java.text.Format>({pattern}) { type,style->create.invoke(factory,type,style) as java.text.Format? }
        b.cacheExplicitFormats()
        var firstA=true; var firstB=true
        val am=object:HashMap<String,java.text.Format?>() {
            override fun containsKey(key:String):Boolean {
                if(firstA) { firstA=false; a.applyPattern("{next}",com.ibm.icu.text.MessagePattern.ApostropheMode.DOUBLE_REQUIRED) }
                return false
            }
        }
        val bm=object:HashMap<String,java.text.Format?>() {
            override fun containsKey(key:String):Boolean {
                if(firstB) { firstB=false; pattern=MessagePattern(MessagePattern.ApostropheMode.DOUBLE_REQUIRED).parse("{next}"); b.cacheExplicitFormats() }
                return false
            }
        }
        assertEquals(outcome { a.setFormatsByArgumentName(am) },outcome { b.setFormatsByArgumentName(bm) })
        compare(a,b)
    }
    @Test fun nullCollectionsEmptyPatternsAndAllocatedEmptyCache() {
        for(text in listOf("","{0}","{name}")) {
            val a=Source(text,SourceLocale.US); val pattern=MessagePattern(text)
            val b=MessageFormatCacheCore<java.text.Format>({pattern}) { _,_->null }; b.cacheExplicitFormats()
            assertEquals(outcome { a.setFormatsByArgumentIndex(null) },outcome { b.setFormatsByArgumentIndex(null) })
            assertEquals(outcome { a.setFormatsByArgumentName(null) },outcome { b.setFormatsByArgumentName(null) })
            assertEquals(outcome { a.setFormats(null) },outcome { b.setFormats(null) })
            compare(a,b)
            a.applyPattern("{0}"); pattern.parse("{0}"); b.cacheExplicitFormats()
            a.setFormat(0,null); b.setFormat(0,null)
            a.applyPattern(""); pattern.parse(""); b.cacheExplicitFormats(); compare(a,b)
        }
    }
}
