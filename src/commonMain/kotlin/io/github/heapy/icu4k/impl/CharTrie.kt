// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 1996-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.util.BinaryBuffer

open class CharTrie : Trie {
    private var initial: Char
        get() = legacyInitial.toChar()
        set(value) { legacyInitial=value.code }
    private lateinit var data: CharArray
    constructor(bytes: BinaryBuffer,dataManipulate: DataManipulate?) : super(bytes,dataManipulate) {
        if(!isCharTrie())throw IllegalArgumentException("Data given does not belong to a char trie.")
    }
    constructor(initialValue: Int,leadUnitValue: Int,dataManipulate: DataManipulate?) : super(CharArray(2080),0x200,dataManipulate) {
        m_dataLength_=if(leadUnitValue!=initialValue)288 else 256
        data=CharArray(m_dataLength_);initial=initialValue.toChar();data.fill(initial,0,256)
        if(leadUnitValue!=initialValue){m_index_.fill(64.toChar(),0xd800 shr 5,0xdc00 shr 5);data.fill(leadUnitValue.toChar(),256,288)}
    }
    fun getCodePointValue(ch: Int): Char {
        val offset=getCodePointOffset(ch);return if(offset>=0)at(offset) else initial
    }
    fun getLeadValue(ch: Char): Char = at(getLeadOffset(ch))
    fun getBMPValue(ch: Char): Char = at(getBMPOffset(ch))
    fun getSurrogateValue(lead: Char,trail: Char): Char = getSurrogateOffset(lead,trail).let {if(it>0)at(it) else initial}
    fun getTrailValue(leadvalue: Int,trail: Char): Char {
        val offset=manipulate().getFoldingOffset(leadvalue)
        return if(offset>0)at(getRawOffset(offset,(trail.code and 0x3ff).toChar())) else initial
    }
    fun getLatin1LinearValue(ch: Char): Char = at(32+m_dataOffset_+ch.code)
    override fun equals(other: Any?): Boolean = super.equals(other) && other is CharTrie && initial==other.initial
    override fun hashCode(): Int = legacyTrieHashCode(1)
    final override fun unserialize(bytes: BinaryBuffer) {
        m_index_=ICUBinary.getChars(bytes,m_dataOffset_+m_dataLength_,0);data=m_index_;initial=at(m_dataOffset_)
    }
    final override fun getSurrogateOffset(lead: Char,trail: Char): Int {
        val offset=manipulate().getFoldingOffset(getLeadValue(lead).code)
        return if(offset>0)getRawOffset(offset,(trail.code and 0x3ff).toChar()) else -1
    }
    final override fun getValue(index: Int): Int = at(index).code
    final override fun getInitialValue(): Int = initial.code
    private fun at(index: Int): Char {
        if(index !in data.indices)throw IndexOutOfBoundsException()
        return data[index]
    }
    private fun manipulate(): DataManipulate = m_dataManipulate_?:throw NullPointerException("The field DataManipulate in this Trie is null")
}
