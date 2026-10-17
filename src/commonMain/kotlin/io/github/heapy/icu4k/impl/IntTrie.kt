// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

open class IntTrie : Trie {
    private var initial: Int
        get() = legacyInitial
        set(value) { legacyInitial=value }
    private lateinit var data: IntArray
    constructor(bytes: BinaryBuffer,dataManipulate: DataManipulate?) : super(bytes,dataManipulate) {
        if(!isIntTrie())throw IllegalArgumentException("Data given does not belong to a int trie.")
    }
    constructor(initialValue: Int,leadUnitValue: Int,dataManipulate: DataManipulate?) : super(CharArray(2080),0x200,dataManipulate) {
        m_dataLength_=if(leadUnitValue!=initialValue)288 else 256
        data=IntArray(m_dataLength_);initial=initialValue;data.fill(initial,0,256)
        if(leadUnitValue!=initialValue){m_index_.fill(64.toChar(),0xd800 shr 5,0xdc00 shr 5);data.fill(leadUnitValue,256,288)}
    }
    internal constructor(index: CharArray,data: IntArray,initialValue: Int,options: Int,dataManipulate: DataManipulate?) : super(index,options,dataManipulate) {
        this.data=data;m_dataLength_=data.size;initial=initialValue
    }
    fun getCodePointValue(ch: Int): Int {val offset=getCodePointOffset(ch);return if(offset>=0)at(offset) else initial}
    fun getLeadValue(ch: Char): Int = at(getLeadOffset(ch))
    fun getBMPValue(ch: Char): Int = at(getBMPOffset(ch))
    fun getSurrogateValue(lead: Char,trail: Char): Int {
        if(!lead.isHighSurrogate() || !trail.isLowSurrogate())throw IllegalArgumentException("Argument characters do not form a supplementary character")
        return getSurrogateOffset(lead,trail).let {if(it>0)at(it) else initial}
    }
    fun getTrailValue(leadvalue: Int,trail: Char): Int {
        val offset=manipulate().getFoldingOffset(leadvalue)
        return if(offset>0)at(getRawOffset(offset,(trail.code and 0x3ff).toChar())) else initial
    }
    fun getLatin1LinearValue(ch: Char): Int = at(32+ch.code)
    override fun equals(other: Any?): Boolean = super.equals(other) && other is IntTrie && initial==other.initial && data.contentEquals(other.data)
    override fun hashCode(): Int = legacyTrieHashCode(2)
    final override fun unserialize(bytes: BinaryBuffer) {super.unserialize(bytes);data=ICUBinary.getInts(bytes,m_dataLength_,0);initial=at(0)}
    final override fun getSurrogateOffset(lead: Char,trail: Char): Int {
        val offset=manipulate().getFoldingOffset(getLeadValue(lead))
        return if(offset>0)getRawOffset(offset,(trail.code and 0x3ff).toChar()) else -1
    }
    final override fun getValue(index: Int): Int = at(index)
    final override fun getInitialValue(): Int = initial
    private fun at(index: Int): Int {
        if(index !in data.indices)throw IndexOutOfBoundsException()
        return data[index]
    }
    private fun manipulate(): DataManipulate = m_dataManipulate_?:throw NullPointerException("The field DataManipulate in this Trie is null")
}
