package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility
import com.ibm.icu.impl.Utility as Source
import kotlin.test.*

class TransliteratorUtilityOracleTest {
    private fun error(block:()->Any?):String=try{"ok:"+block()}catch(e:Throwable){if(e is IndexOutOfBoundsException)"bounds" else e.javaClass.simpleName+":"+e.message}
    @Test fun numberAppendRadicesPaddingAndIoCallbacks() {
        for(number in listOf(Int.MIN_VALUE,-999,-1,0,1,0x10ffff,Int.MAX_VALUE))for(radix in listOf(1,2,8,10,16,36,37))for(minimum in listOf(-1,0,1,4,10)) {
            val source=StringBuilder();val port=StringBuilder()
            assertEquals(error{Source.appendNumber(source,number,radix,minimum);Unit},error{Utility.appendNumber(port,number,radix,minimum);Unit})
            assertEquals(source.toString(),port.toString())
        }
        for(failAt in 0..8) {
            class Sink:Appendable {
                val trace=mutableListOf<String>()
                private fun record(value:String){trace.add(value);if(trace.size==failAt)throw java.io.IOException("append")}
                override fun append(value:Char):Appendable {record("char:${value.code}");return this}
                override fun append(value:CharSequence?):Appendable {record("text:$value");return this}
                override fun append(value:CharSequence?,start:Int,end:Int):Appendable {record("range:$value/$start/$end");return this}
            }
            val source=Sink();val port=Sink()
            assertEquals(error{Source.appendNumber(source,-255,16,4);Unit},error{Utility.appendNumber(port,-255,16,4);Unit})
            assertEquals(source.trace,port.trace)
        }
    }
    @Test fun replaceablePatternParsing() {
        for(pattern in listOf("","~","a","a~b","\\N~{~","😀~Σ"))for(input in listOf("","a","a b","a  b!","\\N{A}","\\N { A}","😀 Σ","😀Σ"))for(start in 0..input.length)for(limit in start..input.length) {
            assertEquals(Source.parsePattern(pattern,com.ibm.icu.text.ReplaceableString(input),start,limit),Utility.parsePattern(pattern,ReplaceableString(input),start,limit),"$pattern/$input/$start/$limit")
        }
    }
}
