// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

/** Mutable byte-array owner with a separate logical size, as used by ICU sort keys. */
open class ByteArrayWrapper : Comparable<ByteArrayWrapper> {
    var bytes: ByteArray? = null
    var size: Int = 0
    constructor()
    constructor(bytesToAdopt: ByteArray?,size: Int) {
        if((bytesToAdopt == null && size != 0) || size<0 || (bytesToAdopt != null && size>bytesToAdopt.size)) throw IndexOutOfBoundsException("illegal size: $size")
        bytes=bytesToAdopt;this.size=size
    }
    constructor(source: BinaryBuffer) {
        size=source.limit;bytes=ByteArray(size)
        val copy=source.readBytes(size);copy.copyInto(bytes!!)
    }
    open fun ensureCapacity(capacity: Int): ByteArrayWrapper {
        if(bytes == null || bytes!!.size<capacity) {
            if(capacity<0) throw byteArrayWrapperNegativeSizeException()
            val next=ByteArray(capacity)
            if(bytes != null) copyBytes(bytes,0,next,0,size)
            bytes=next
        }
        return this
    }
    fun set(src: ByteArray?,start: Int,limit: Int): ByteArrayWrapper { size=0;append(src,start,limit);return this }
    fun append(src: ByteArray?,start: Int,limit: Int): ByteArrayWrapper {
        val length=limit-start;ensureCapacity(size+length)
        copyBytes(src,start,bytes,size,length);size+=length;return this
    }
    fun releaseBytes(): ByteArray? { val result=bytes;bytes=null;size=0;return result }
    override fun toString(): String = buildString {
        for(i in 0 until size) { if(i != 0) append(' ');val value=unit(bytes,i).toInt() and 255;append(HEX[value ushr 4]);append(HEX[value and 15]) }
    }
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other !is ByteArrayWrapper || size != other.size) return false
        for(i in 0 until size) if(unit(bytes,i) != unit(other.bytes,i)) return false
        return true
    }
    override fun hashCode(): Int { var result=size;for(i in 0 until size) result=37*result+unit(bytes,i);return result }
    override fun compareTo(other: ByteArrayWrapper): Int {
        if(this === other) return 0
        val minSize=minOf(size,other.size)
        for(i in 0 until minSize) if(unit(bytes,i) != unit(other.bytes,i)) return (unit(bytes,i).toInt() and 255)-(unit(other.bytes,i).toInt() and 255)
        return size-other.size
    }
    private companion object {
        const val HEX="0123456789ABCDEF"
        fun unit(bytes: ByteArray?,index: Int): Byte { val a=bytes!!;if(index<0 || index>=a.size) throw IndexOutOfBoundsException();return a[index] }
        fun copyBytes(src: ByteArray?,srcOffset: Int,target: ByteArray?,targetOffset: Int,length: Int) {
            if(length<64) {
                var remaining=length;var i=srcOffset;var n=targetOffset
                while(--remaining>=0) {
                    // Java evaluates the destination reference/index before reading the source,
                    // but checks destination null/bounds only when storing the computed byte.
                    val value=unit(src,i)
                    val a=target!!;if(n<0 || n>=a.size) throw IndexOutOfBoundsException();a[n]=value
                    ++i;++n
                }
            } else {
                val a=src!!;val b=target!!
                if(srcOffset<0 || targetOffset<0 || length>a.size || length>b.size || srcOffset>a.size-length || targetOffset>b.size-length) throw IndexOutOfBoundsException()
                a.copyInto(b,targetOffset,srcOffset,srcOffset+length)
            }
        }
    }
}
internal expect fun byteArrayWrapperNegativeSizeException(): RuntimeException
