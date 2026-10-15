// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1999-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

/** Allocates tailoring weights between two exclusive limits, retaining iteration state. */
internal class CollationWeights {
    private var middleLength=0
    private val minBytes=IntArray(5)
    private val maxBytes=IntArray(5)
    private val ranges=arrayOfNulls<WeightRange>(7)
    private var rangeIndex=0
    private var rangeCount=0
    private class WeightRange : Comparable<WeightRange> {
        var start=0L;var end=0L;var length=0;var count=0
        override fun compareTo(other: WeightRange): Int = start.compareTo(other.start)
    }
    fun initForPrimary(compressible: Boolean) {
        middleLength=1;minBytes[1]=Collation.MERGE_SEPARATOR_BYTE+1;maxBytes[1]=Collation.TRAIL_WEIGHT_BYTE
        minBytes[2]=if(compressible) Collation.PRIMARY_COMPRESSION_LOW_BYTE+1 else 2
        maxBytes[2]=if(compressible) Collation.PRIMARY_COMPRESSION_HIGH_BYTE-1 else 255
        minBytes[3]=2;maxBytes[3]=255;minBytes[4]=2;maxBytes[4]=255
    }
    fun initForSecondary() {
        middleLength=3;minBytes[1]=0;maxBytes[1]=0;minBytes[2]=0;maxBytes[2]=0
        minBytes[3]=Collation.LEVEL_SEPARATOR_BYTE+1;maxBytes[3]=255;minBytes[4]=2;maxBytes[4]=255
    }
    fun initForTertiary() {
        middleLength=3;minBytes[1]=0;maxBytes[1]=0;minBytes[2]=0;maxBytes[2]=0
        minBytes[3]=Collation.LEVEL_SEPARATOR_BYTE+1;maxBytes[3]=63;minBytes[4]=2;maxBytes[4]=63
    }
    fun allocWeights(lowerLimit: Long,upperLimit: Long,n: Int): Boolean {
        if(!getWeightRanges(lowerLimit,upperLimit)) return false
        while(true) {
            val minLength=ranges[0]!!.length
            if(allocWeightsInShortRanges(n,minLength)) break
            if(minLength == 4) return false
            if(allocWeightsInMinLengthRanges(n,minLength)) break
            var i=0
            while(i<rangeCount && ranges[i]!!.length == minLength) { lengthenRange(ranges[i]!!);++i }
        }
        rangeIndex=0
        if(rangeCount<ranges.size) ranges[rangeCount]=null
        return true
    }
    fun nextWeight(): Long {
        if(rangeIndex>=rangeCount) return 0xffffffffL
        val range=ranges[rangeIndex]!!;val weight=range.start
        --range.count
        if(range.count == 0) ++rangeIndex else {
            range.start=incWeight(weight,range.length)
            checkAssertion { range.start<=range.end }
        }
        return weight
    }
    private fun countBytes(idx: Int): Int = maxBytes[idx]-minBytes[idx]+1
    private fun incWeight(initial: Long,initialLength: Int): Long {
        var weight=initial;var length=initialLength
        while(true) {
            val b=getWeightByte(weight,length)
            if(b<maxBytes[length]) return setWeightByte(weight,length,b+1)
            weight=setWeightByte(weight,length,minBytes[length]);--length
            checkAssertion { length>0 }
        }
    }
    private fun incWeightByOffset(initial: Long,initialLength: Int,initialOffset: Int): Long {
        var weight=initial;var length=initialLength;var offset=initialOffset
        while(true) {
            offset+=getWeightByte(weight,length)
            if(offset<=maxBytes[length]) return setWeightByte(weight,length,offset)
            offset-=minBytes[length]
            weight=setWeightByte(weight,length,minBytes[length]+offset%countBytes(length))
            offset/=countBytes(length);--length
            checkAssertion { length>0 }
        }
    }
    private fun lengthenRange(range: WeightRange) {
        val length=range.length+1
        range.start=setWeightTrail(range.start,length,minBytes[length])
        range.end=setWeightTrail(range.end,length,maxBytes[length])
        range.count*=countBytes(length);range.length=length
    }
    private fun getWeightRanges(lowerLimit: Long,upperLimit: Long): Boolean {
        checkAssertion { lowerLimit != 0L };checkAssertion { upperLimit != 0L }
        val lowerLength=lengthOfWeight(lowerLimit);val upperLength=lengthOfWeight(upperLimit)
        checkAssertion { lowerLength>=middleLength }
        if(lowerLimit>=upperLimit) return false
        if(lowerLength<upperLength && lowerLimit == truncateWeight(upperLimit,lowerLength)) return false
        val lower=arrayOfNulls<WeightRange>(5);val middle=WeightRange();val upper=arrayOfNulls<WeightRange>(5)
        var weight=lowerLimit
        for(length in lowerLength downTo middleLength+1) {
            val trail=getWeightTrail(weight,length)
            if(trail<maxBytes[length]) lower[length]=WeightRange().also {
                it.start=incWeightTrail(weight,length);it.end=setWeightTrail(weight,length,maxBytes[length])
                it.length=length;it.count=maxBytes[length]-trail
            }
            weight=truncateWeight(weight,length-1)
        }
        middle.start=if(weight<0xff000000L) incWeightTrail(weight,middleLength) else 0xffffffffL
        weight=upperLimit
        for(length in upperLength downTo middleLength+1) {
            val trail=getWeightTrail(weight,length)
            if(trail>minBytes[length]) upper[length]=WeightRange().also {
                it.start=setWeightTrail(weight,length,minBytes[length]);it.end=decWeightTrail(weight,length)
                it.length=length;it.count=trail-minBytes[length]
            }
            weight=truncateWeight(weight,length-1)
        }
        middle.end=decWeightTrail(weight,middleLength);middle.length=middleLength
        if(middle.end>=middle.start) middle.count=((middle.end-middle.start) shr (8*(4-middleLength))).toInt()+1
        else {
            var length=4
            while(length>middleLength) {
                val lo=lower[length];val hi=upper[length]
                if(lo != null && hi != null && lo.count>0 && hi.count>0) {
                    val lowerEnd=lo.end;val upperStart=hi.start;var merged=false
                    if(lowerEnd>upperStart) {
                        checkAssertion { truncateWeight(lowerEnd,length-1) == truncateWeight(upperStart,length-1) }
                        lo.end=hi.end;lo.count=getWeightTrail(lo.end,length)-getWeightTrail(lo.start,length)+1;merged=true
                    } else if(lowerEnd == upperStart) checkAssertion { minBytes[length]<maxBytes[length] }
                    else if(incWeight(lowerEnd,length) == upperStart) { lo.end=hi.end;lo.count+=hi.count;merged=true }
                    if(merged) {
                        hi.count=0;--length
                        while(length>middleLength) { lower[length]=null;upper[length]=null;--length }
                        break
                    }
                }
                --length
            }
        }
        rangeCount=0
        if(middle.count>0) { ranges[0]=middle;rangeCount=1 }
        for(length in middleLength+1..4) {
            upper[length]?.let { if(it.count>0) ranges[rangeCount++]=it }
            lower[length]?.let { if(it.count>0) ranges[rangeCount++]=it }
        }
        return rangeCount>0
    }
    private fun allocWeightsInShortRanges(initialN: Int,minLength: Int): Boolean {
        var n=initialN;var i=0
        while(i<rangeCount && ranges[i]!!.length<=minLength+1) {
            if(n<=ranges[i]!!.count) {
                if(ranges[i]!!.length>minLength) ranges[i]!!.count=n
                rangeCount=i+1
                if(rangeCount>1) ranges.sortWith(Comparator { a,b -> a!!.compareTo(b!!) },0,rangeCount)
                return true
            }
            n-=ranges[i]!!.count;++i
        }
        return false
    }
    private fun allocWeightsInMinLengthRanges(n: Int,minLength: Int): Boolean {
        var count=0;var minLengthRangeCount=0
        while(minLengthRangeCount<rangeCount && ranges[minLengthRangeCount]!!.length == minLength) {
            count+=ranges[minLengthRangeCount]!!.count;++minLengthRangeCount
        }
        val nextCountBytes=countBytes(minLength+1)
        if(n>count*nextCountBytes) return false
        var start=ranges[0]!!.start;var end=ranges[0]!!.end
        for(i in 1 until minLengthRangeCount) { if(ranges[i]!!.start<start) start=ranges[i]!!.start;if(ranges[i]!!.end>end) end=ranges[i]!!.end }
        var count2=(n-count)/(nextCountBytes-1);var count1=count-count2
        if(count2 == 0 || count1+count2*nextCountBytes<n) { ++count2;--count1;checkAssertion { count1+count2*nextCountBytes>=n } }
        ranges[0]!!.start=start
        if(count1 == 0) { ranges[0]!!.end=end;ranges[0]!!.count=count;lengthenRange(ranges[0]!!);rangeCount=1 }
        else {
            ranges[0]!!.end=incWeightByOffset(start,minLength,count1-1);ranges[0]!!.count=count1
            if(ranges[1] == null) ranges[1]=WeightRange()
            ranges[1]!!.start=incWeight(ranges[0]!!.end,minLength);ranges[1]!!.end=end
            ranges[1]!!.length=minLength;ranges[1]!!.count=count2;lengthenRange(ranges[1]!!);rangeCount=2
        }
        return true
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
    companion object {
        private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationWeights::class)
        fun lengthOfWeight(weight: Long): Int = when { weight and 0xffffffL == 0L -> 1;weight and 0xffffL == 0L -> 2;weight and 255L == 0L -> 3;else -> 4 }
        private fun getWeightTrail(weight: Long,length: Int): Int = (weight shr (8*(4-length))).toInt() and 255
        private fun setWeightTrail(weight: Long,length: Int,trail: Int): Long { val shift=8*(4-length);return (weight and (0xffffff00L shl shift)) or (trail.toLong() shl shift) }
        private fun getWeightByte(weight: Long,idx: Int): Int = getWeightTrail(weight,idx)
        private fun setWeightByte(weight: Long,idx: Int,b: Int): Long {
            val bits=idx*8;var mask=if(bits<32) 0xffffffffL shr bits else 0L
            val shift=32-bits;mask=mask or (0xffffff00L shl shift)
            return (weight and mask) or (b.toLong() shl shift)
        }
        private fun truncateWeight(weight: Long,length: Int): Long = weight and (0xffffffffL shl (8*(4-length)))
        private fun incWeightTrail(weight: Long,length: Int): Long = weight+(1L shl (8*(4-length)))
        private fun decWeightTrail(weight: Long,length: Int): Long = weight-(1L shl (8*(4-length)))
    }
}
