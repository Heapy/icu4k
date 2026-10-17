// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.RangeValueIterator

/** Original folded-trie range iterator, retaining block skipping and the extract hook. */
open class TrieIterator(trie: Trie?) : RangeValueIterator {
    private val trie=trie?:throw IllegalArgumentException("Argument trie cannot be null")
    private val initial=extract(this.trie.iteratorInitial())
    private var current=0
    private var nextPoint=0
    private var nextValue=0
    private var nextIndex=0
    private var nextBlock=0
    private var blockIndex=0
    private var trailIndex=0
    init {reset()}
    final override fun next(element: RangeValueIterator.Element): Boolean {
        if(nextPoint>0x10ffff)return false
        if(nextPoint<0x10000 && nextBMP(element))return true
        nextSupplementary(element);return true
    }
    final override fun reset() {
        current=0;nextPoint=0;nextIndex=0
        nextBlock=trie.iteratorIndex(0) shl 2
        nextValue=if(nextBlock==trie.iteratorDataOffset())initial else extract(trie.iteratorValue(nextBlock))
        blockIndex=0;trailIndex=32
    }
    protected open fun extract(value: Int): Int = value
    private fun result(element: RangeValueIterator.Element,start: Int,limit: Int,value: Int) {
        element.start=start;element.limit=limit;element.value=value
    }
    private fun nextBMP(element: RangeValueIterator.Element): Boolean {
        val value=nextValue;current=nextPoint;nextPoint++;blockIndex++
        if(!checkDetail(value)){result(element,current,nextPoint,value);return true}
        while(nextPoint<0x10000) {
            nextIndex=when(nextPoint){0xd800->2048;0xdc00->nextPoint shr 5;else->nextIndex+1}
            blockIndex=0
            if(!checkBlock(value)){result(element,current,nextPoint,value);return true}
        }
        nextPoint--;blockIndex--;return false
    }
    private fun nextSupplementary(element: RangeValueIterator.Element) {
        val value=nextValue;nextPoint++;blockIndex++
        if(UTF16.getTrailSurrogate(nextPoint)!='\udc00') {
            if(!checkNullTrailIndex() && !checkDetail(value)){result(element,current,nextPoint,value);current=nextPoint;return}
            nextIndex++;trailIndex++
            if(!checkTrailBlock(value)){result(element,current,nextPoint,value);current=nextPoint;return}
        }
        var lead=UTF16.getLeadSurrogate(nextPoint).code
        while(lead<0xdc00) {
            val leadBlock=trie.iteratorIndex(lead shr 5) shl 2
            if(leadBlock==trie.iteratorDataOffset()) {
                if(value!=initial){nextValue=initial;nextBlock=leadBlock;blockIndex=0;result(element,current,nextPoint,value);current=nextPoint;return}
                lead+=32
                nextPoint=(lead-0xd800)*1024+0x10000
                continue
            }
            nextIndex=trie.iteratorFold(trie.iteratorValue(leadBlock+(lead and 31)))
            if(nextIndex<=0) {
                if(value!=initial){nextValue=initial;nextBlock=trie.iteratorDataOffset();blockIndex=0;result(element,current,nextPoint,value);current=nextPoint;return}
                nextPoint+=1024
            }else {
                trailIndex=0
                if(!checkTrailBlock(value)){result(element,current,nextPoint,value);current=nextPoint;return}
            }
            lead++
        }
        result(element,current,0x110000,value)
    }
    private fun checkDetail(value: Int): Boolean {
        while(blockIndex<32) {
            nextValue=extract(trie.iteratorValue(nextBlock+blockIndex))
            if(nextValue!=value)return false
            blockIndex++;nextPoint++
        }
        return true
    }
    private fun checkBlock(value: Int): Boolean {
        val previous=nextBlock;nextBlock=trie.iteratorIndex(nextIndex) shl 2
        if(nextBlock==previous && nextPoint-current>=32)nextPoint+=32
        else if(nextBlock==trie.iteratorDataOffset()) {
            if(value!=initial){nextValue=initial;blockIndex=0;return false}
            nextPoint+=32
        }else if(!checkDetail(value))return false
        return true
    }
    private fun checkTrailBlock(value: Int): Boolean {
        while(trailIndex<32) {
            blockIndex=0
            if(!checkBlock(value))return false
            trailIndex++;nextIndex++
        }
        return true
    }
    private fun checkNullTrailIndex(): Boolean {
        if(nextIndex<=0) {
            nextPoint+=1023
            val lead=UTF16.getLeadSurrogate(nextPoint).code
            val block=trie.iteratorIndex(lead shr 5) shl 2
            nextIndex=trie.iteratorFold(trie.iteratorValue(block+(lead and 31)))-1
            blockIndex=32;return true
        }
        return false
    }
}
