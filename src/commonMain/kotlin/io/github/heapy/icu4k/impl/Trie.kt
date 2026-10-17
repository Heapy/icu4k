// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.BinaryBuffer

/** ICU's original folded trie format, distinct from Trie2 and CodePointTrie. */
abstract class Trie {
    fun interface DataManipulate { fun getFoldingOffset(value: Int): Int }
    protected lateinit var m_index_: CharArray
    protected var m_dataManipulate_: DataManipulate?
    protected var m_dataOffset_: Int
    protected var m_dataLength_=0
    protected var legacyInitial=0
    private val options: Int
    protected constructor(bytes: BinaryBuffer,dataManipulate: DataManipulate?) {
        val signature=bytes.readInt();options=bytes.readInt()
        if(signature!=HEADER_SIGNATURE_ || options and 15!=5 || (options shr 4) and 15!=2)throw IllegalArgumentException("ICU data file error: Trie header authentication failed, please check if you have the most updated ICU data file")
        m_dataManipulate_=dataManipulate?:DataManipulate {it}
        m_dataOffset_=bytes.readInt();m_dataLength_=bytes.readInt()
        unserialize(bytes)
    }
    protected constructor(index: CharArray,options: Int,dataManipulate: DataManipulate?) {
        this.options=options;m_index_=index;m_dataOffset_=index.size
        m_dataManipulate_=dataManipulate?:DataManipulate {it}
    }
    fun isLatin1Linear(): Boolean = options and HEADER_OPTIONS_LATIN1_IS_LINEAR_MASK_!=0
    override fun equals(other: Any?): Boolean = other===this || other is Trie && isLatin1Linear()==other.isLatin1Linear() && options==other.options && m_dataLength_==other.m_dataLength_ && m_index_.contentEquals(other.m_index_)
    override fun hashCode(): Int = legacyTrieHashCode(0)
    fun getSerializedDataSize(): Int = 16+2*m_dataOffset_+m_dataLength_*(if(isCharTrie())2 else 4)
    protected abstract fun getSurrogateOffset(lead: Char,trail: Char): Int
    protected abstract fun getValue(index: Int): Int
    protected abstract fun getInitialValue(): Int
    protected fun getRawOffset(offset: Int,ch: Char): Int = (m_index_[offset+(ch.code shr 5)].code shl 2)+(ch.code and 31)
    protected fun getBMPOffset(ch: Char): Int = getRawOffset(if(ch.isHighSurrogate())LEAD_INDEX_OFFSET_ else 0,ch)
    protected fun getLeadOffset(ch: Char): Int = getRawOffset(0,ch)
    protected fun getCodePointOffset(ch: Int): Int = when {
        ch<0->-1
        ch<0xd800->getRawOffset(0,ch.toChar())
        ch<0x10000->getBMPOffset(ch.toChar())
        ch<=0x10ffff->getSurrogateOffset(UTF16.getLeadSurrogate(ch),(ch and 0x3ff).toChar())
        else->-1
    }
    protected open fun unserialize(bytes: BinaryBuffer) {m_index_=ICUBinary.getChars(bytes,m_dataOffset_,0)}
    protected fun isIntTrie(): Boolean = options and 0x100!=0
    protected fun isCharTrie(): Boolean = options and 0x100==0
    protected companion object {
        const val LEAD_INDEX_OFFSET_=0x2800 shr 5
        const val INDEX_STAGE_1_SHIFT_=5
        const val INDEX_STAGE_2_SHIFT_=2
        const val DATA_BLOCK_LENGTH=32
        const val INDEX_STAGE_3_MASK_=31
        const val SURROGATE_BLOCK_BITS=5
        const val SURROGATE_BLOCK_COUNT=32
        const val BMP_INDEX_LENGTH=2048
        const val SURROGATE_MASK_=0x3ff
        const val HEADER_LENGTH_=16
        const val HEADER_OPTIONS_LATIN1_IS_LINEAR_MASK_=0x200
        const val HEADER_SIGNATURE_=0x54726965
        const val HEADER_OPTIONS_INDEX_SHIFT_=4
        const val HEADER_OPTIONS_DATA_IS_32_BIT_=0x100
    }
}
internal expect fun legacyTrieHashCode(kind: Int): Int
