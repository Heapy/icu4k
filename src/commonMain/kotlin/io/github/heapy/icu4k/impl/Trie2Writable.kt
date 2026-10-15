// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2009, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Mutable legacy trie, including ICU's in-place compaction and alternate lead-unit values. */
open class Trie2Writable : Trie2 {
    private var index1=IntArray(UNEWTRIE2_INDEX_1_LENGTH)
    private var index2=IntArray(UNEWTRIE2_MAX_INDEX_2_LENGTH)
    private lateinit var data: IntArray
    private var index2Length=0
    private var dataCapacity=0
    private var firstFreeBlock=0
    // ICU4J shadows Trie2.index2NullOffset with a separate builder field.
    private var writableIndex2NullOffset=0
    private var isCompacted=false
    private val map=IntArray(UNEWTRIE2_MAX_DATA_LENGTH shr UTRIE2_SHIFT_2)
    constructor(initialValueP: Int,errorValueP: Int) : super() { init(initialValueP,errorValueP) }
    constructor(source: Trie2?) : super() { init(source!!.initialValue,source.errorValue);for(r in source) setRange(r,true) }
    private fun init(initial: Int,error: Int) {
        initialValue=initial;errorValue=error;highStart=0x110000
        data=IntArray(INITIAL_DATA_LENGTH);dataCapacity=INITIAL_DATA_LENGTH;firstFreeBlock=0;isCompacted=false
        for(i in 0 until 0x80) data[i]=initialValue
        for(i in 0x80 until 0xc0) data[i]=errorValue
        for(i in DATA_NULL_OFFSET until DATA_START_OFFSET) data[i]=initialValue
        dataNullOffset=DATA_NULL_OFFSET;dataLength=DATA_START_OFFSET
        var i=0;var j=0
        while(j<0x80) { index2[i]=j;map[i]=1;++i;j+=UTRIE2_DATA_BLOCK_LENGTH }
        while(j<0xc0) { map[i++]=0;j+=UTRIE2_DATA_BLOCK_LENGTH }
        map[i++]=(0x110000 shr UTRIE2_SHIFT_2)-(0x80 shr UTRIE2_SHIFT_2)+1+UTRIE2_LSCP_INDEX_2_LENGTH
        j+=UTRIE2_DATA_BLOCK_LENGTH
        while(j<DATA_START_OFFSET) { map[i++]=0;j+=UTRIE2_DATA_BLOCK_LENGTH }
        for(k in (0x80 shr UTRIE2_SHIFT_2) until UTRIE2_INDEX_2_BMP_LENGTH) index2[k]=DATA_NULL_OFFSET
        for(k in 0 until UNEWTRIE2_INDEX_GAP_LENGTH) index2[UNEWTRIE2_INDEX_GAP_OFFSET+k]=-1
        for(k in 0 until UTRIE2_INDEX_2_BLOCK_LENGTH) index2[INDEX_2_NULL_OFFSET+k]=DATA_NULL_OFFSET
        writableIndex2NullOffset=INDEX_2_NULL_OFFSET;index2Length=INDEX_2_START_OFFSET
        i=0;j=0
        while(i<UTRIE2_OMITTED_BMP_INDEX_1_LENGTH) { index1[i++]=j;j+=UTRIE2_INDEX_2_BLOCK_LENGTH }
        while(i<UNEWTRIE2_INDEX_1_LENGTH) index1[i++]=INDEX_2_NULL_OFFSET
        i=0x80
        while(i<0x800) { set(i,initialValue);i+=UTRIE2_DATA_BLOCK_LENGTH }
    }
    private fun isInNullBlock(c: Int,forLSCP: Boolean): Boolean {
        val i2=if(c.toChar().code in 0xd800..0xdbff && forLSCP) (UTRIE2_LSCP_INDEX_2_OFFSET-(0xd800 shr UTRIE2_SHIFT_2))+(c shr UTRIE2_SHIFT_2) else index1[c shr UTRIE2_SHIFT_1]+((c shr UTRIE2_SHIFT_2) and UTRIE2_INDEX_2_MASK)
        return index2[i2]==dataNullOffset
    }
    private fun allocIndex2Block(): Int {
        val block=index2Length;val top=block+UTRIE2_INDEX_2_BLOCK_LENGTH
        if(top>index2.size) throw IllegalStateException("Internal error in Trie2 creation.")
        index2Length=top;index2.copyInto(index2,block,writableIndex2NullOffset,writableIndex2NullOffset+UTRIE2_INDEX_2_BLOCK_LENGTH);return block
    }
    private fun getIndex2Block(c: Int,forLSCP: Boolean): Int {
        if(c in 0xd800..0xdbff && forLSCP) return UTRIE2_LSCP_INDEX_2_OFFSET
        val i1=c shr UTRIE2_SHIFT_1;var i2=index1[i1]
        if(i2==writableIndex2NullOffset) { i2=allocIndex2Block();index1[i1]=i2 }
        return i2
    }
    private fun allocDataBlock(copyBlock: Int): Int {
        val block: Int
        if(firstFreeBlock!=0) { block=firstFreeBlock;firstFreeBlock=-map[block shr UTRIE2_SHIFT_2] }
        else {
            block=dataLength;val top=block+UTRIE2_DATA_BLOCK_LENGTH
            if(top>dataCapacity) {
                val capacity=if(dataCapacity<MEDIUM_DATA_LENGTH) MEDIUM_DATA_LENGTH else if(dataCapacity<UNEWTRIE2_MAX_DATA_LENGTH) UNEWTRIE2_MAX_DATA_LENGTH else throw IllegalStateException("Internal error in Trie2 creation.")
                val next=IntArray(capacity);data.copyInto(next,0,0,dataLength);data=next;dataCapacity=capacity
            }
            dataLength=top
        }
        data.copyInto(data,block,copyBlock,copyBlock+UTRIE2_DATA_BLOCK_LENGTH);map[block shr UTRIE2_SHIFT_2]=0;return block
    }
    private fun releaseDataBlock(block: Int) { map[block shr UTRIE2_SHIFT_2]=-firstFreeBlock;firstFreeBlock=block }
    private fun isWritableBlock(block: Int): Boolean = block!=dataNullOffset && map[block shr UTRIE2_SHIFT_2]==1
    private fun setIndex2Entry(i2: Int,block: Int) {
        ++map[block shr UTRIE2_SHIFT_2];val old=index2[i2]
        if(--map[old shr UTRIE2_SHIFT_2]==0) releaseDataBlock(old)
        index2[i2]=block
    }
    private fun getDataBlock(c: Int,forLSCP: Boolean): Int {
        val i2=getIndex2Block(c,forLSCP)+((c shr UTRIE2_SHIFT_2) and UTRIE2_INDEX_2_MASK);val old=index2[i2]
        if(isWritableBlock(old)) return old
        val block=allocDataBlock(old);setIndex2Entry(i2,block);return block
    }
    open fun set(c: Int,value: Int): Trie2Writable {
        if(c !in 0..0x10ffff) throw IllegalArgumentException("Invalid code point.")
        set(c,true,value);fHash=0;return this
    }
    private fun set(c: Int,forLSCP: Boolean,value: Int): Trie2Writable {
        if(isCompacted) uncompact()
        val block=getDataBlock(c,forLSCP);data[block+(c and UTRIE2_DATA_MASK)]=value;return this
    }
    private fun uncompact() {
        val t=Trie2Writable(this)
        index1=t.index1;index2=t.index2;data=t.data;index2Length=t.index2Length;dataCapacity=t.dataCapacity;isCompacted=t.isCompacted
        header=t.header
        // The immutable index is unset in both writable tries; do not read lateinit storage.
        data16=t.data16;data32=t.data32;indexLength=t.indexLength;dataLength=t.dataLength;writableIndex2NullOffset=t.writableIndex2NullOffset
        initialValue=t.initialValue;errorValue=t.errorValue;highStart=t.highStart;highValueIndex=t.highValueIndex;dataNullOffset=t.dataNullOffset
        // Source does not copy map or firstFreeBlock here.
    }
    private fun writeBlock(initialBlock: Int,value: Int) { var block=initialBlock;val limit=block+UTRIE2_DATA_BLOCK_LENGTH;while(block<limit) data[block++]=value }
    private fun fillBlock(block: Int,start: Int,limit: Int,value: Int,initial: Int,overwrite: Boolean) {
        for(i in block+start until block+limit) if(overwrite || data[i]==initial) data[i]=value
    }
    open fun setRange(initialStart: Int,end: Int,value: Int,overwrite: Boolean): Trie2Writable {
        var start=initialStart
        if(start !in 0..0x10ffff || end !in 0..0x10ffff || start>end) throw IllegalArgumentException("Invalid code point range.")
        if(!overwrite && value==initialValue) return this
        fHash=0;if(isCompacted) uncompact()
        var limit=end+1
        if(start and UTRIE2_DATA_MASK!=0) {
            val block=getDataBlock(start,true);val next=(start+UTRIE2_DATA_BLOCK_LENGTH) and UTRIE2_DATA_MASK.inv()
            if(next<=limit) { fillBlock(block,start and UTRIE2_DATA_MASK,UTRIE2_DATA_BLOCK_LENGTH,value,initialValue,overwrite);start=next }
            else { fillBlock(block,start and UTRIE2_DATA_MASK,limit and UTRIE2_DATA_MASK,value,initialValue,overwrite);return this }
        }
        val rest=limit and UTRIE2_DATA_MASK;limit=limit and UTRIE2_DATA_MASK.inv()
        var repeatBlock=if(value==initialValue) dataNullOffset else -1
        while(start<limit) {
            var setRepeat=false
            if(value==initialValue && isInNullBlock(start,true)) { start+=UTRIE2_DATA_BLOCK_LENGTH;continue }
            val i2=getIndex2Block(start,true)+((start shr UTRIE2_SHIFT_2) and UTRIE2_INDEX_2_MASK);val block=index2[i2]
            if(isWritableBlock(block)) {
                if(overwrite && block>=DATA_0800_OFFSET) setRepeat=true else fillBlock(block,0,UTRIE2_DATA_BLOCK_LENGTH,value,initialValue,overwrite)
            } else if(data[block]!=value && (overwrite || block==dataNullOffset)) setRepeat=true
            if(setRepeat) { if(repeatBlock>=0) setIndex2Entry(i2,repeatBlock) else { repeatBlock=getDataBlock(start,true);writeBlock(repeatBlock,value) } }
            start+=UTRIE2_DATA_BLOCK_LENGTH
        }
        if(rest>0) fillBlock(getDataBlock(start,true),0,rest,value,initialValue,overwrite)
        return this
    }
    open fun setRange(range: Range?,overwrite: Boolean): Trie2Writable {
        fHash=0
        if(range!!.leadSurrogate) {
            var c=range.startCodePoint
            while(c<=range.endCodePoint) { if(overwrite || getFromU16SingleLead(c.toChar())==initialValue) setForLeadSurrogateCodeUnit(c.toChar(),range.value);++c }
        } else setRange(range.startCodePoint,range.endCodePoint,range.value,overwrite)
        return this
    }
    open fun setForLeadSurrogateCodeUnit(codeUnit: Char,value: Int): Trie2Writable { fHash=0;set(codeUnit.code,false,value);return this }
    override fun get(codePoint: Int): Int = if(codePoint !in 0..0x10ffff) errorValue else get(codePoint,true)
    private fun get(c: Int,fromLSCP: Boolean): Int {
        if(c>=highStart && (c !in 0xd800..0xdbff || fromLSCP)) return data[dataLength-UTRIE2_DATA_GRANULARITY]
        val i2=if(c in 0xd800..0xdbff && fromLSCP) (UTRIE2_LSCP_INDEX_2_OFFSET-(0xd800 shr UTRIE2_SHIFT_2))+(c shr UTRIE2_SHIFT_2) else index1[c shr UTRIE2_SHIFT_1]+((c shr UTRIE2_SHIFT_2) and UTRIE2_INDEX_2_MASK)
        return data[index2[i2]+(c and UTRIE2_DATA_MASK)]
    }
    override fun getFromU16SingleLead(c: Char): Int = get(c.code,false)
    internal override fun dataValue(index: Int): Int = data[index]
    override fun getSerializedLength(): Int = throw UnsupportedOperationException("Freeze a writable trie before serialization.")
    private fun equalInt(a: IntArray,s: Int,t: Int,length: Int): Boolean { for(i in 0 until length) if(a[s+i]!=a[t+i]) return false;return true }
    private fun findSameIndex2Block(length: Int,other: Int): Int { for(block in 0..length-UTRIE2_INDEX_2_BLOCK_LENGTH) if(equalInt(index2,block,other,UTRIE2_INDEX_2_BLOCK_LENGTH)) return block;return -1 }
    private fun findSameDataBlock(length: Int,other: Int,blockLength: Int): Int { var block=0;while(block<=length-blockLength) { if(equalInt(data,block,other,blockLength)) return block;block+=UTRIE2_DATA_GRANULARITY };return -1 }
    private fun findHighStart(highValue: Int): Int {
        var prevI2=if(highValue==initialValue) writableIndex2NullOffset else -1
        var prevBlock=if(highValue==initialValue) dataNullOffset else -1
        var i1=UNEWTRIE2_INDEX_1_LENGTH;var c=0x110000
        while(c>0) {
            val i2Block=index1[--i1]
            if(i2Block==prevI2) { c-=UTRIE2_CP_PER_INDEX_1_ENTRY;continue }
            prevI2=i2Block
            if(i2Block==writableIndex2NullOffset) { if(highValue!=initialValue) return c;c-=UTRIE2_CP_PER_INDEX_1_ENTRY }
            else {
                var i2=UTRIE2_INDEX_2_BLOCK_LENGTH
                while(i2>0) {
                    val block=index2[i2Block+--i2]
                    if(block==prevBlock) { c-=UTRIE2_DATA_BLOCK_LENGTH;continue }
                    prevBlock=block
                    if(block==dataNullOffset) { if(highValue!=initialValue) return c;c-=UTRIE2_DATA_BLOCK_LENGTH }
                    else { var j=UTRIE2_DATA_BLOCK_LENGTH;while(j>0) { if(data[block+--j]!=highValue) return c;--c } }
                }
            }
        }
        return 0
    }
    private fun compactData() {
        var newStart=UTRIE2_DATA_START_OFFSET;var start=0;var i=0
        while(start<newStart) { map[i++]=start;start+=UTRIE2_DATA_BLOCK_LENGTH }
        var blockLength=64;var blockCount=blockLength shr UTRIE2_SHIFT_2;start=newStart
        while(start<dataLength) {
            if(start==DATA_0800_OFFSET) { blockLength=UTRIE2_DATA_BLOCK_LENGTH;blockCount=1 }
            if(map[start shr UTRIE2_SHIFT_2]<=0) { start+=blockLength;continue }
            var moved=findSameDataBlock(newStart,start,blockLength)
            if(moved>=0) {
                var count=blockCount;var mapIndex=start shr UTRIE2_SHIFT_2
                while(count-->0) { map[mapIndex++]=moved;moved+=UTRIE2_DATA_BLOCK_LENGTH }
                start+=blockLength;continue
            }
            var overlap=blockLength-UTRIE2_DATA_GRANULARITY
            while(overlap>0 && !equalInt(data,newStart-overlap,start,overlap)) overlap-=UTRIE2_DATA_GRANULARITY
            if(overlap>0 || newStart<start) {
                moved=newStart-overlap;var count=blockCount;var mapIndex=start shr UTRIE2_SHIFT_2
                while(count-->0) { map[mapIndex++]=moved;moved+=UTRIE2_DATA_BLOCK_LENGTH }
                start+=overlap;count=blockLength-overlap
                while(count-->0) data[newStart++]=data[start++]
            } else {
                var count=blockCount;var mapIndex=start shr UTRIE2_SHIFT_2
                while(count-->0) { map[mapIndex++]=start;start+=UTRIE2_DATA_BLOCK_LENGTH }
                newStart=start
            }
        }
        i=0
        while(i<index2Length) { if(i==UNEWTRIE2_INDEX_GAP_OFFSET) i+=UNEWTRIE2_INDEX_GAP_LENGTH;index2[i]=map[index2[i] shr UTRIE2_SHIFT_2];++i }
        dataNullOffset=map[dataNullOffset shr UTRIE2_SHIFT_2]
        while(newStart and (UTRIE2_DATA_GRANULARITY-1)!=0) data[newStart++]=initialValue
        dataLength=newStart
    }
    private fun compactIndex2() {
        var newStart=UTRIE2_INDEX_2_BMP_LENGTH;var start=0;var i=0
        while(start<newStart) { map[i++]=start;start+=UTRIE2_INDEX_2_BLOCK_LENGTH }
        newStart+=UTRIE2_UTF8_2B_INDEX_2_LENGTH+((highStart-0x10000) shr UTRIE2_SHIFT_1)
        start=INDEX_2_NULL_OFFSET
        while(start<index2Length) {
            val moved=findSameIndex2Block(newStart,start)
            if(moved>=0) { map[start shr UTRIE2_SHIFT_1_2]=moved;start+=UTRIE2_INDEX_2_BLOCK_LENGTH;continue }
            var overlap=UTRIE2_INDEX_2_BLOCK_LENGTH-1
            while(overlap>0 && !equalInt(index2,newStart-overlap,start,overlap)) --overlap
            if(overlap>0 || newStart<start) {
                map[start shr UTRIE2_SHIFT_1_2]=newStart-overlap;start+=overlap;var count=UTRIE2_INDEX_2_BLOCK_LENGTH-overlap
                while(count-->0) index2[newStart++]=index2[start++]
            } else { map[start shr UTRIE2_SHIFT_1_2]=start;start+=UTRIE2_INDEX_2_BLOCK_LENGTH;newStart=start }
        }
        for(k in 0 until UNEWTRIE2_INDEX_1_LENGTH) index1[k]=map[index1[k] shr UTRIE2_SHIFT_1_2]
        writableIndex2NullOffset=map[writableIndex2NullOffset shr UTRIE2_SHIFT_1_2]
        while(newStart and ((UTRIE2_DATA_GRANULARITY-1) or 1)!=0) index2[newStart++]=0xffff shl UTRIE2_INDEX_SHIFT
        index2Length=newStart
    }
    private fun compactTrie() {
        var highValue=get(0x10ffff);var local=findHighStart(highValue)
        local=(local+(UTRIE2_CP_PER_INDEX_1_ENTRY-1)) and (UTRIE2_CP_PER_INDEX_1_ENTRY-1).inv()
        if(local==0x110000) highValue=errorValue
        highStart=local
        if(highStart<0x110000) setRange(if(highStart<=0x10000) 0x10000 else highStart,0x10ffff,initialValue,true)
        compactData();if(highStart>0x10000) compactIndex2()
        data[dataLength++]=highValue
        while(dataLength and (UTRIE2_DATA_GRANULARITY-1)!=0) data[dataLength++]=initialValue
        isCompacted=true
    }
    open fun toTrie2_16(): Trie2_16 = Trie2_16().also { freeze(it,true) }
    open fun toTrie2_32(): Trie2_32 = Trie2_32().also { freeze(it,false) }
    private fun freeze(dest: Trie2,bits16: Boolean) {
        if(!isCompacted) compactTrie()
        val allIndexes=if(highStart<=0x10000) UTRIE2_INDEX_1_OFFSET else index2Length
        val dataMove=if(bits16) allIndexes else 0
        if(allIndexes>0xffff || dataMove+dataNullOffset>0xffff || dataMove+DATA_0800_OFFSET>0xffff || dataMove+dataLength>(0xffff shl UTRIE2_INDEX_SHIFT)) throw UnsupportedOperationException("Trie2 data is too large.")
        var destIndexLength=allIndexes
        if(bits16) destIndexLength+=dataLength else dest.data32=IntArray(dataLength)
        dest.index=CharArray(destIndexLength);dest.indexLength=allIndexes;dest.dataLength=dataLength
        dest.index2NullOffset=if(highStart<=0x10000) 0xffff else UTRIE2_INDEX_2_OFFSET+writableIndex2NullOffset
        dest.initialValue=initialValue;dest.errorValue=errorValue;dest.highStart=highStart;dest.highValueIndex=dataMove+dataLength-UTRIE2_DATA_GRANULARITY;dest.dataNullOffset=dataMove+dataNullOffset
        dest.header=UTrie2Header().apply { signature=0x54726932;options=if(bits16) 0 else 1;indexLength=dest.indexLength;shiftedDataLength=dest.dataLength shr UTRIE2_INDEX_SHIFT;index2NullOffset=dest.index2NullOffset;dataNullOffset=dest.dataNullOffset;shiftedHighStart=dest.highStart shr UTRIE2_SHIFT_1 }
        var destIdx=0
        for(i in 0 until UTRIE2_INDEX_2_BMP_LENGTH) dest.index[destIdx++]=((index2[i]+dataMove) shr UTRIE2_INDEX_SHIFT).toChar()
        for(i in 0 until 2) dest.index[destIdx++]=(dataMove+UTRIE2_BAD_UTF8_DATA_OFFSET).toChar()
        for(i in 2 until 32) dest.index[destIdx++]=(dataMove+index2[i shl (6-UTRIE2_SHIFT_2)]).toChar()
        if(highStart>0x10000) {
            val index1Length=(highStart-0x10000) shr UTRIE2_SHIFT_1
            val offset=UTRIE2_INDEX_2_BMP_LENGTH+UTRIE2_UTF8_2B_INDEX_2_LENGTH+index1Length
            for(i in 0 until index1Length) dest.index[destIdx++]=(UTRIE2_INDEX_2_OFFSET+index1[i+UTRIE2_OMITTED_BMP_INDEX_1_LENGTH]).toChar()
            for(i in 0 until index2Length-offset) dest.index[destIdx++]=((dataMove+index2[offset+i]) shr UTRIE2_INDEX_SHIFT).toChar()
        }
        if(bits16) { checkAssertion { destIdx==dataMove };dest.data16=destIdx;for(i in 0 until dataLength) dest.index[destIdx++]=data[i].toChar() }
        else for(i in 0 until dataLength) dest.data32!![i]=data[i]
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
    private companion object {
        const val INITIAL_DATA_LENGTH=1 shl 14
        const val MEDIUM_DATA_LENGTH=1 shl 17
        const val INDEX_2_NULL_OFFSET=UNEWTRIE2_INDEX_GAP_OFFSET+UNEWTRIE2_INDEX_GAP_LENGTH
        const val INDEX_2_START_OFFSET=INDEX_2_NULL_OFFSET+UTRIE2_INDEX_2_BLOCK_LENGTH
        const val DATA_NULL_OFFSET=UTRIE2_DATA_START_OFFSET
        const val DATA_START_OFFSET=DATA_NULL_OFFSET+0x40
        const val DATA_0800_OFFSET=DATA_START_OFFSET+0x780
        val assertionsEnabled=trie2WritableAssertionsEnabled()
    }
}
internal expect fun trie2WritableAssertionsEnabled(): Boolean
