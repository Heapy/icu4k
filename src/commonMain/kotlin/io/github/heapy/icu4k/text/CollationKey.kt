// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.coll.Collation
import io.github.heapy.icu4k.impl.coll.collationIteratorClassAssertionsEnabled
import io.github.heapy.icu4k.util.byteArrayWrapperNegativeSizeException

/** Bytewise collation order. Only keys produced by the same collation rules are comparable. */
class CollationKey : Comparable<CollationKey?> {
    private val source: String?
    private val key: ByteArray?
    private var length: Int
    private constructor(source: String?,key: ByteArray?,length: Int) { this.source=source;this.key=key;this.length=length }
    private var cachedHash=0
    constructor(source: String?,key: ByteArray?) : this(source,key,-1)
    constructor(source: String?,key: RawCollationKey?) {
        this.source=source;length=key!!.size-1;this.key=key.releaseBytes()
        checkAssertion { unit(this.key,length).toInt()==0 }
    }
    fun getSourceString(): String? = source
    fun toByteArray(): ByteArray {
        val size=getLength()+1;val result=allocate(size)
        copy(key,0,result,0,size);return result
    }
    override fun compareTo(other: CollationKey?): Int {
        var i=0
        while(true) {
            val left=unit(key,i).toInt() and 255;val right=unit(other!!.key,i).toInt() and 255
            if(left<right) return -1
            if(left>right) return 1
            if(left==0) return 0
            ++i
        }
    }
    override fun equals(other: Any?): Boolean = other is CollationKey && equals(other)
    fun equals(target: CollationKey?): Boolean {
        if(this === target) return true
        if(target==null) return false
        var i=0
        while(true) {
            if(unit(key,i)!=unit(target.key,i)) return false
            if(unit(key,i).toInt()==0) break
            ++i
        }
        return true
    }
    override fun hashCode(): Int {
        if(cachedHash==0) {
            if(key==null) cachedHash=1
            else {
                var i=0;var hash=0
                while(unit(key,i).toInt()!=0 && unit(key,i+1).toInt()!=0) {
                    val c=((unit(key,i).toInt() shl 8) or (unit(key,i+1).toInt() and 255)).toChar()
                    hash=31*hash+c.code;i+=2
                }
                if(unit(key,i).toInt()!=0) hash=31*hash+(unit(key,i).toInt() shl 8).toChar().code
                cachedHash=hash
            }
        }
        return cachedHash
    }
    fun getBound(boundType: Int,noOfLevels: Int): CollationKey {
        var levels=noOfLevels;var offset=0;var strength=0
        if(levels>0) {
            while(offset<key!!.size && unit(key,offset).toInt()!=0) {
                if(unit(key,offset++).toInt()==Collation.LEVEL_SEPARATOR_BYTE) {
                    ++strength;--levels
                    if(levels==0 || offset==key.size || unit(key,offset).toInt()==0) { --offset;break }
                }
            }
        }
        if(levels>0) throw IllegalArgumentException("Source collation key has only $strength strength level. Call getBound() again  with noOfLevels < $strength")
        val result=allocate(offset+boundType+1);copy(key,0,result,0,offset)
        when(boundType) {
            BoundMode.LOWER -> Unit
            BoundMode.UPPER -> put(result,offset++,2)
            BoundMode.UPPER_LONG -> { put(result,offset++,255);put(result,offset++,255) }
            else -> throw IllegalArgumentException("Illegal boundType argument")
        }
        put(result,offset,0);return CollationKey(null,result,offset)
    }
    fun merge(source: CollationKey?): CollationKey {
        if(source==null || source.getLength()==0) throw IllegalArgumentException("CollationKey argument can not be null or of 0 length")
        val result=allocate(getLength()+source.getLength()+2)
        var rindex=0;var index=0;var sourceIndex=0
        while(true) {
            while(unit(key,index)<0 || unit(key,index)>=2) put(result,rindex++,unit(key,index++).toInt())
            put(result,rindex++,2)
            while(unit(source.key,sourceIndex)<0 || unit(source.key,sourceIndex)>=2) put(result,rindex++,unit(source.key,sourceIndex++).toInt())
            if(unit(key,index).toInt()==Collation.LEVEL_SEPARATOR_BYTE && unit(source.key,sourceIndex).toInt()==Collation.LEVEL_SEPARATOR_BYTE) {
                ++index;++sourceIndex;put(result,rindex++,Collation.LEVEL_SEPARATOR_BYTE)
            } else break
        }
        var remaining=length-index
        if(remaining>0) { copy(key,index,result,rindex,remaining);rindex+=remaining }
        else { remaining=source.length-sourceIndex;if(remaining>0) { copy(source.key,sourceIndex,result,rindex,remaining);rindex+=remaining } }
        put(result,rindex,0);checkAssertion { rindex==result.size-1 }
        return CollationKey(null,result,rindex)
    }
    private fun getLength(): Int {
        if(length>=0) return length
        var n=key!!.size
        for(index in 0 until n) if(unit(key,index).toInt()==0) { n=index;break }
        length=n;return length
    }
    class BoundMode private constructor() {
        companion object {
            const val LOWER=0
            const val UPPER=1
            const val UPPER_LONG=2
            @Deprecated("The numeric value may change over time") const val COUNT=3
        }
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
    private companion object {
        private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationKey::class)
        fun unit(a: ByteArray?,i: Int): Byte { val bytes=a!!;if(i<0 || i>=bytes.size) throw IndexOutOfBoundsException();return bytes[i] }
        fun put(a: ByteArray,i: Int,b: Int) { if(i<0 || i>=a.size) throw IndexOutOfBoundsException();a[i]=b.toByte() }
        fun allocate(size: Int): ByteArray { if(size<0) throw byteArrayWrapperNegativeSizeException();return ByteArray(size) }
        fun copy(source: ByteArray?,start: Int,dest: ByteArray,offset: Int,count: Int) {
            val a=source!!
            if(start<0 || offset<0 || count<0 || count>a.size || count>dest.size || start>a.size-count || offset>dest.size-count) throw IndexOutOfBoundsException()
            a.copyInto(dest,offset,start,start+count)
        }
    }
}
