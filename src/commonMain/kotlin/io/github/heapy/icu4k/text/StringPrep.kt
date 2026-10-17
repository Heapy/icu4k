// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2015, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.CharTrie
import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.StringPrepDataReader
import io.github.heapy.icu4k.impl.UBiDiProps
import io.github.heapy.icu4k.impl.CacheReference
import io.github.heapy.icu4k.impl.createResourceCacheLock
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UCharacterDirection as D
import io.github.heapy.icu4k.util.BinaryBuffer
import io.github.heapy.icu4k.util.ICUUncheckedIOException

/** RFC StringPrep profiles backed by ICU's original Unicode 3.2 mapping tables. */
class StringPrep(bytes: BinaryBuffer) {
    private val indexes: IntArray
    private val trie: CharTrie
    private val mapping: CharArray
    private val doNFKC: Boolean
    private val checkBiDi: Boolean
    init {
        val reader=StringPrepDataReader(bytes)
        indexes=reader.readIndexes(16)
        trie=CharTrie(bytes,null)
        mapping=reader.read(indexes[1]/2)
        doNFKC=indexes[7] and 1>0;checkBiDi=indexes[7] and 2>0
        val sprepVersion=reader.getUnicodeVersion().let {io.github.heapy.icu4k.util.VersionInfo.getInstance(it[0].toInt(),it[1].toInt(),it[2].toInt(),it[3].toInt())}
        val correctionVersion=ICUBinary.getVersionInfoFromCompactInt(indexes[2])
        val normVersion=UCharacter.getUnicodeVersion()
        if(normVersion<sprepVersion && normVersion<correctionVersion && doNFKC)throw ICUUncheckedIOException("Normalization Correction version not supported")
        if(checkBiDi)UBiDiProps.INSTANCE
    }
    constructor(bytes: ByteArray) : this(BinaryBuffer(bytes))
    private class Values {
        var isIndex=false;var value=0;var type=-1
        fun read(word: Char) {
            isIndex=false;value=0;type=-1
            val w=word.code
            if(w==0)type=4
            else if(w>=0xfff0)type=w-0xfff0
            else {
                type=1
                if(w and 2>0){isIndex=true;value=w shr 2}
                else value=(w shl 16 shr 16) shr 2
                if(w shr 2==0x3fbf){type=3;isIndex=false;value=0}
            }
        }
    }
    private fun map(iter: UCharacterIterator,options: Int): StringBuilder {
        val values=Values();val dest=StringBuilder();val allowUnassigned=options and ALLOW_UNASSIGNED>0
        while(true) {
            var ch=iter.nextCodePoint();if(ch==UCharacterIterator.DONE)break
            values.read(trie.getCodePointValue(ch))
            if(values.type==0 && !allowUnassigned)throw StringPrepParseException("An unassigned code point was found in the input",StringPrepParseException.UNASSIGNED_ERROR,iter.getText(),iter.getIndex())
            else if(values.type==1) {
                if(values.isIndex) {
                    var index=values.value
                    val length=when(index) {
                        in indexes[3] until indexes[4]->1
                        in indexes[4] until indexes[5]->2
                        in indexes[5] until indexes[6]->3
                        else->mapping[index++].code
                    }
                    dest.append(mapping.concatToString(index,index+length));continue
                }else ch-=values.value
            }else if(values.type==3)continue
            UTF16.append(dest,ch)
        }
        return dest
    }
    fun prepare(src: UCharacterIterator,options: Int): StringBuilder {
        val mapped=map(src,options)
        val normalized=if(doNFKC)StringBuilder(Normalizer.normalize(mapped.toString(),Normalizer.NFKC,Normalizer.UNICODE_3_2)) else mapped
        val iter=UCharacterIterator.getInstance(normalized)
        val values=Values();var direction=D.CHAR_DIRECTION_COUNT;var first=D.CHAR_DIRECTION_COUNT
        var rtlPos=-1;var ltrPos=-1;var rtl=false;var ltr=false
        while(true) {
            val ch=iter.nextCodePoint();if(ch==UCharacterIterator.DONE)break
            values.read(trie.getCodePointValue(ch))
            if(values.type==2)throw StringPrepParseException("A prohibited code point was found in the input",StringPrepParseException.PROHIBITED_ERROR,iter.getText(),values.value)
            if(checkBiDi) {
                direction=UBiDiProps.INSTANCE.getClass(ch)
                if(first==D.CHAR_DIRECTION_COUNT)first=direction
                if(direction==D.LEFT_TO_RIGHT){ltr=true;ltrPos=iter.getIndex()-1}
                if(direction==D.RIGHT_TO_LEFT || direction==D.RIGHT_TO_LEFT_ARABIC){rtl=true;rtlPos=iter.getIndex()-1}
            }
        }
        if(checkBiDi && (ltr && rtl || rtl && !((first==D.RIGHT_TO_LEFT || first==D.RIGHT_TO_LEFT_ARABIC) && (direction==D.RIGHT_TO_LEFT || direction==D.RIGHT_TO_LEFT_ARABIC))))throw StringPrepParseException("The input does not conform to the rules for BiDi code points.",StringPrepParseException.CHECK_BIDI_ERROR,iter.getText(),maxOf(rtlPos,ltrPos))
        return normalized
    }
    fun prepare(src: String,options: Int): String = prepare(UCharacterIterator.getInstance(src),options).toString()
    companion object {
        const val DEFAULT=0
        const val ALLOW_UNASSIGNED=1
        const val RFC3491_NAMEPREP=0
        const val RFC3530_NFS4_CS_PREP=1
        const val RFC3530_NFS4_CS_PREP_CI=2
        const val RFC3530_NFS4_CIS_PREP=3
        const val RFC3530_NFS4_MIXED_PREP_PREFIX=4
        const val RFC3530_NFS4_MIXED_PREP_SUFFIX=5
        const val RFC3722_ISCSI=6
        const val RFC3920_NODEPREP=7
        const val RFC3920_RESOURCEPREP=8
        const val RFC4011_MIB=9
        const val RFC4013_SASLPREP=10
        const val RFC4505_TRACE=11
        const val RFC4518_LDAP=12
        const val RFC4518_LDAP_CI=13
        private val names=arrayOf("rfc3491","rfc3530cs","rfc3530csci","rfc3491","rfc3530mixp","rfc3491","rfc3722","rfc3920node","rfc3920res","rfc4011","rfc4013","rfc4505","rfc4518","rfc4518ci")
        private val cache=arrayOfNulls<CacheReference<StringPrep>>(14)
        private val lock=createResourceCacheLock()
        fun getInstance(profile: Int): StringPrep {
            if(profile !in 0..13)throw IllegalArgumentException("Bad profile type")
            return lock.withLock {cache[profile]?.get()?:StringPrep(ICUBinary.getRequiredData(names[profile]+".spp")).also {cache[profile]=createStringPrepReference(it)}}
        }
    }
}
internal expect fun createStringPrepReference(value: StringPrep): CacheReference<StringPrep>
