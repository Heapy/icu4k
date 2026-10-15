// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.Trie2_32
import io.github.heapy.icu4k.impl.USerializedSet
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUException

/** UCol version-5 reader; input cursor and partially assigned tailoring are retained on errors. */
internal object CollationDataReader {
    const val IX_INDEXES_LENGTH=0;const val IX_OPTIONS=1;const val IX_RESERVED2=2;const val IX_RESERVED3=3;const val IX_JAMO_CE32S_START=4
    const val IX_REORDER_CODES_OFFSET=5;const val IX_REORDER_TABLE_OFFSET=6;const val IX_TRIE_OFFSET=7;const val IX_RESERVED8_OFFSET=8
    const val IX_CES_OFFSET=9;const val IX_RESERVED10_OFFSET=10;const val IX_CE32S_OFFSET=11;const val IX_ROOT_ELEMENTS_OFFSET=12
    const val IX_CONTEXTS_OFFSET=13;const val IX_UNSAFE_BWD_OFFSET=14;const val IX_FAST_LATIN_TABLE_OFFSET=15;const val IX_SCRIPTS_OFFSET=16
    const val IX_COMPRESSIBLE_BYTES_OFFSET=17;const val IX_RESERVED18_OFFSET=18;const val IX_TOTAL_SIZE=19
    private const val DATA_FORMAT=0x55436f6c
    private val IS_ACCEPTABLE=ICUBinary.Authenticate { it[0].toInt() == 5 }
    private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationDataReader::class)
    fun read(base: CollationTailoring?, inBytes: BinaryBuffer?, tailoring: CollationTailoring?) {
        val version=ICUBinary.readHeader(inBytes!!,DATA_FORMAT,IS_ACCEPTABLE)
        tailoring!!.version=version
        if (base != null && base.getUCAVersion() != tailoring.getUCAVersion()) throw ICUException("Tailoring UCA version differs from base data UCA version")
        val inLength=inBytes.remaining
        if (inLength < 8) throw ICUException("not enough bytes")
        val indexesLength=inBytes.readInt()
        if (indexesLength < 2 || inLength < indexesLength*4) throw ICUException("not enough indexes")
        val ix=IntArray(IX_TOTAL_SIZE+1);ix[0]=indexesLength
        for (i in 1 until minOf(indexesLength,ix.size)) ix[i]=inBytes.readInt()
        for (i in indexesLength until ix.size) ix[i]=-1
        if (indexesLength > ix.size) ICUBinary.skipBytes(inBytes,(indexesLength-ix.size)*4)
        var length=if (indexesLength > IX_TOTAL_SIZE) ix[IX_TOTAL_SIZE] else if (indexesLength > IX_REORDER_CODES_OFFSET) ix[indexesLength-1] else 0
        if (inLength < length) throw ICUException("not enough bytes")
        val baseData=base?.data
        fun section(i: Int): Int = ix[i+1]-ix[i]
        val reorderCodes: IntArray;var codesLength: Int
        length=section(IX_REORDER_CODES_OFFSET)
        if (length >= 4) {
            if (baseData == null) throw ICUException("Collation base data must not reorder scripts")
            codesLength=length/4;reorderCodes=ICUBinary.getInts(inBytes,codesLength,length and 3)
            var rangesLength=0
            while (rangesLength < codesLength && (reorderCodes[codesLength-rangesLength-1] and -65536) != 0) ++rangesLength
            checkAssertion { rangesLength < codesLength };codesLength-=rangesLength
        } else { reorderCodes=IntArray(0);codesLength=0;ICUBinary.skipBytes(inBytes,length) }
        var reorderTable: ByteArray?=null
        length=section(IX_REORDER_TABLE_OFFSET)
        if (length >= 256) {
            if (codesLength == 0) throw ICUException("Reordering table without reordering codes")
            reorderTable=inBytes.readBytes(256);length-=256
        }
        ICUBinary.skipBytes(inBytes,length)
        if (baseData != null && baseData.numericPrimary != (ix[IX_OPTIONS].toLong() and 0xff000000L)) throw ICUException("Tailoring numeric primary weight differs from base data")
        var data: CollationData?=null
        length=section(IX_TRIE_OFFSET)
        if (length >= 8) {
            tailoring.ensureOwnedData();data=tailoring.ownedData!!;data.base=baseData;data.numericPrimary=ix[IX_OPTIONS].toLong() and 0xff000000L
            val trie=Trie2_32.createFromSerialized(inBytes);tailoring.trie=trie;data.trie=trie
            val trieLength=data.trie!!.getSerializedLength()
            if (trieLength > length) throw ICUException("Not enough bytes for the mappings trie")
            length-=trieLength
        } else if (baseData != null) tailoring.data=baseData else throw ICUException("Missing collation data mappings")
        ICUBinary.skipBytes(inBytes,length)
        ICUBinary.skipBytes(inBytes,section(IX_RESERVED8_OFFSET))
        length=section(IX_CES_OFFSET)
        if (length >= 8) { if (data == null) throw ICUException("Tailored ces without tailored trie");data.ces=ICUBinary.getLongs(inBytes,length/8,length and 7) } else ICUBinary.skipBytes(inBytes,length)
        ICUBinary.skipBytes(inBytes,section(IX_RESERVED10_OFFSET))
        length=section(IX_CE32S_OFFSET)
        if (length >= 4) { if (data == null) throw ICUException("Tailored ce32s without tailored trie");data.ce32s=ICUBinary.getInts(inBytes,length/4,length and 3) } else ICUBinary.skipBytes(inBytes,length)
        val jamoStart=ix[IX_JAMO_CE32S_START]
        if (jamoStart >= 0) {
            if (data?.ce32s == null) throw ICUException("JamoCE32sStart index into non-existent ce32s[]")
            data.jamoCE32s=IntArray(CollationData.JAMO_CE32S_LENGTH)
            val a=data.ce32s!!
            if (jamoStart > a.size-CollationData.JAMO_CE32S_LENGTH) throw IndexOutOfBoundsException()
            a.copyInto(data.jamoCE32s!!,0,jamoStart,jamoStart+CollationData.JAMO_CE32S_LENGTH)
        } else if (data != null) {
            if (baseData != null) data.jamoCE32s=baseData.jamoCE32s else throw ICUException("Missing Jamo CE32s for Hangul processing")
        }
        length=section(IX_ROOT_ELEMENTS_OFFSET)
        if (length >= 4) {
            val count=length/4
            if (data == null) throw ICUException("Root elements but no mappings")
            if (count <= CollationRootElements.IX_SEC_TER_BOUNDARIES) throw ICUException("Root elements array too short")
            data.rootElements=LongArray(count)
            for (i in 0 until count) data.rootElements!![i]=inBytes.readInt().toLong() and 0xffffffffL
            if (data.rootElements!![CollationRootElements.IX_COMMON_SEC_AND_TER_CE] != Collation.COMMON_SEC_AND_TER_CE.toLong()) throw ICUException("Common sec/ter weights in base data differ from the hardcoded value")
            // CollationKeys.SEC_COMMON_HIGH = COMMON_BYTE + 0x40.
            if ((data.rootElements!![CollationRootElements.IX_SEC_TER_BOUNDARIES] ushr 24) < Collation.COMMON_BYTE+0x40) throw ICUException("[fixed last secondary common byte] is too low")
            length=length and 3
        }
        ICUBinary.skipBytes(inBytes,length)
        length=section(IX_CONTEXTS_OFFSET)
        if (length >= 2) { if (data == null) throw ICUException("Tailored contexts without tailored trie");data.contexts=ICUBinary.getString(inBytes,length/2,length and 1) } else ICUBinary.skipBytes(inBytes,length)
        length=section(IX_UNSAFE_BWD_OFFSET)
        if (length >= 2) {
            if (data == null) throw ICUException("Unsafe-backward-set but no mappings")
            if (baseData == null) { tailoring.unsafeBackwardSet=UnicodeSet(0xdc00,0xdfff);data.nfcImpl!!.addLcccChars(tailoring.unsafeBackwardSet!!) }
            else tailoring.unsafeBackwardSet=baseData.unsafeBackwardSet!!.cloneAsThawed()
            val set=USerializedSet();val unsafe=ICUBinary.getChars(inBytes,length/2,length and 1);length=0
            set.getSet(unsafe,0);val range=IntArray(2)
            for (i in 0 until set.countRanges()) { set.getRange(i,range);tailoring.unsafeBackwardSet!!.add(range[0],range[1]) }
            var c=0x10000
            for (lead in 0xd800 until 0xdc00) { if (!tailoring.unsafeBackwardSet!!.containsNone(c,c+0x3ff)) tailoring.unsafeBackwardSet!!.add(lead);c+=0x400 }
            tailoring.unsafeBackwardSet!!.freeze();data.unsafeBackwardSet=tailoring.unsafeBackwardSet
        } else if (data != null) {
            if (baseData != null) data.unsafeBackwardSet=baseData.unsafeBackwardSet else throw ICUException("Missing unsafe-backward-set")
        }
        ICUBinary.skipBytes(inBytes,length)
        length=section(IX_FAST_LATIN_TABLE_OFFSET)
        if (data != null) {
            data.fastLatinTable=null;data.fastLatinTableHeader=null
            if (((ix[IX_OPTIONS] shr 16) and 0xff) == CollationFastLatin.VERSION) {
                if (length >= 2) {
                    val first=inBytes.readUnsignedShort();val headerLength=first and 0xff
                    data.fastLatinTableHeader=CharArray(headerLength);data.fastLatinTableHeader!!.drSet(0,first.toChar())
                    for (i in 1 until headerLength) data.fastLatinTableHeader!!.drSet(i,inBytes.readUnsignedShort().toChar())
                    val tableLength=length/2-headerLength
                    if (tableLength < 0) throw settingsNegativeArraySizeException()
                    data.fastLatinTable=ICUBinary.getChars(inBytes,tableLength,length and 1);length=0
                    if ((first shr 8) != CollationFastLatin.VERSION) throw ICUException("Fast-Latin table version differs from version in data header")
                } else if (baseData != null) { data.fastLatinTable=baseData.fastLatinTable;data.fastLatinTableHeader=baseData.fastLatinTableHeader }
            }
        }
        ICUBinary.skipBytes(inBytes,length)
        length=section(IX_SCRIPTS_OFFSET)
        if (length >= 2) {
            if (data == null) throw ICUException("Script order data but no mappings")
            val scriptsLength=length/2;val chars=inBytes.slice()
            data.numScripts=chars.readUnsignedShort();val startsLength=scriptsLength-(1+data.numScripts+16)
            if (startsLength <= 2) throw ICUException("Script order data too short")
            data.scriptsIndex=CharArray(data.numScripts+16);readCharsInto(chars,data.scriptsIndex!!)
            data.scriptStarts=CharArray(startsLength);readCharsInto(chars,data.scriptStarts!!)
            val starts=data.scriptStarts!!
            if (!(starts.drAt(0) == 0 && starts.drAt(1) == ((Collation.MERGE_SEPARATOR_BYTE+1) shl 8) && starts.drAt(startsLength-1) == (Collation.TRAIL_WEIGHT_BYTE shl 8))) throw ICUException("Script order data not valid")
        } else if (data != null && baseData != null) { data.numScripts=baseData.numScripts;data.scriptsIndex=baseData.scriptsIndex;data.scriptStarts=baseData.scriptStarts }
        ICUBinary.skipBytes(inBytes,length)
        length=section(IX_COMPRESSIBLE_BYTES_OFFSET)
        if (length >= 256) {
            if (data == null) throw ICUException("Data for compressible primary lead bytes but no mappings")
            data.compressibleBytes=BooleanArray(256)
            for (i in 0 until 256) data.compressibleBytes!![i]=inBytes.readByte().toInt() != 0
            length-=256
        } else if (data != null) {
            if (baseData != null) data.compressibleBytes=baseData.compressibleBytes else throw ICUException("Missing data for compressible primary lead bytes")
        }
        ICUBinary.skipBytes(inBytes,length);ICUBinary.skipBytes(inBytes,section(IX_RESERVED18_OFFSET))
        val ts=tailoring.settings!!.readOnly();val options=ix[IX_OPTIONS] and 0xffff;val primaries=CharArray(CollationFastLatin.LATIN_LIMIT)
        val fastOptions=CollationFastLatin.getOptions(tailoring.data,ts,primaries)
        if (options == ts!!.options && ts.variableTop != 0L && reorderCodes.contentEquals(ts.reorderCodes) && fastOptions == ts.fastLatinOptions && (fastOptions < 0 || primaries.contentEquals(ts.fastLatinPrimaries))) return
        val settings=tailoring.settings!!.copyOnWrite();settings.options=options
        settings.variableTop=tailoring.data!!.getLastPrimaryForGroup(4096+settings.getMaxVariable())
        if (settings.variableTop == 0L) throw ICUException("The maxVariable could not be mapped to a variableTop")
        if (codesLength != 0) settings.aliasReordering(baseData,reorderCodes,codesLength,reorderTable)
        settings.fastLatinOptions=CollationFastLatin.getOptions(tailoring.data,settings,settings.fastLatinPrimaries)
    }
    private fun readCharsInto(bytes: BinaryBuffer,a: CharArray) { val values=ICUBinary.getChars(bytes,a.size,0);values.copyInto(a) }
    private inline fun checkAssertion(condition: () -> Boolean) { if (assertionsEnabled && !condition()) throw AssertionError() }
}
private fun CharArray.drAt(i: Int): Int { if (i < 0 || i >= size) throw IndexOutOfBoundsException();return this[i].code }
private fun CharArray.drSet(i: Int,c: Char) { if (i < 0 || i >= size) throw IndexOutOfBoundsException();this[i]=c }
