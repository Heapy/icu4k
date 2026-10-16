// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2011, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** CalendarCache's reentrant synchronized, double-hashed long table. */
open class CalendarCache {
    private val lock=createCalendarCacheLock(this)
    private var primeIndex=0
    private var size=0
    private var arraySize=PRIMES[0]
    private var threshold=(arraySize*0.75).toInt()
    private var keys=LongArray(arraySize)
    private var values=LongArray(arraySize) { EMPTY }
    open fun get(key: Long): Long=lock.withLock { values[findIndex(key)] }
    open fun put(key: Long,value: Long) { lock.withLock {
        if(size>=threshold) rehash()
        val index=findIndex(key)
        keys[index]=key; values[index]=value; size++
    } }
    private fun findIndex(key: Long): Int {
        var index=((key*15821+1)%arraySize).toInt()
        if(index<0) index+=arraySize
        var delta=0
        while(values[index]!=EMPTY && keys[index]!=key) {
            if(delta==0) delta=arraySize-2-(key%(arraySize-2)).toInt()
            index=(index+delta)%arraySize
        }
        return index
    }
    private fun rehash() {
        val oldKeys=keys; val oldValues=values
        arraySize=if(primeIndex<PRIMES.lastIndex) PRIMES[++primeIndex] else arraySize*2+1
        keys=LongArray(arraySize); values=LongArray(arraySize) { EMPTY }
        threshold=(arraySize*0.75).toInt(); size=0
        for(index in oldValues.indices) if(oldValues[index]!=EMPTY) put(oldKeys[index],oldValues[index])
    }
    companion object {
        @kotlin.jvm.JvmField var EMPTY: Long=Long.MIN_VALUE
        private val PRIMES=intArrayOf(61,127,509,1021,2039,4093,8191,16381,32749,65521,131071,262139)
    }
}

internal expect fun createCalendarCacheLock(owner: Any): ResourceCacheLock
