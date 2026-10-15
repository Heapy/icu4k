// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2012-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

/** Sort-key encoding through quaternary level; the identical level uses BOCSU. */
internal object CollationKeys {
    abstract class SortKeyByteSink(protected var buffer_: ByteArray?) {
        private var appended_=0
        open fun setBufferAndAppended(dest: ByteArray?,app: Int) { buffer_=dest;appended_=app }
        open fun Append(bytes: ByteArray?,n: Int) {
            if(n<=0 || bytes==null) return
            val length=appended_;appended_+=n
            val available=buffer_!!.size-length
            if(n<=available) copy(bytes,0,buffer_,length,n) else AppendBeyondCapacity(bytes,0,n,length)
        }
        open fun Append(b: Int) {
            if(appended_<buffer_!!.size || Resize(1,appended_)) put(buffer_,appended_,b)
            ++appended_
        }
        open fun NumberOfBytesAppended(): Int = appended_
        open fun GetRemainingCapacity(): Int = buffer_!!.size-appended_
        open fun Overflowed(): Boolean = appended_>buffer_!!.size
        protected abstract fun AppendBeyondCapacity(bytes: ByteArray,start: Int,n: Int,length: Int)
        protected abstract fun Resize(appendCapacity: Int,length: Int): Boolean
    }
    open class LevelCallback { open fun needToWrite(level: Int): Boolean = true }
    val SIMPLE_LEVEL_FALLBACK=LevelCallback()
    private class SortKeyLevel {
        var buffer=ByteArray(40)
        var len=0
        fun isEmpty(): Boolean = len==0
        fun length(): Int = len
        fun getAt(index: Int): Byte = unit(buffer,index)
        fun data(): ByteArray = buffer
        fun appendByte(b: Int) { if(len<buffer.size || ensureCapacity(1)) put(buffer,len++,b) }
        fun appendWeight16(w: Int) {
            levelAssertion { w and 0xffff != 0 }
            val b0=(w ushr 8).toByte();val b1=w.toByte();val count=if(b1.toInt()==0) 1 else 2
            if(len+count<=buffer.size || ensureCapacity(count)) { put(buffer,len++,b0.toInt());if(b1.toInt()!=0) put(buffer,len++,b1.toInt()) }
        }
        fun appendWeight32(w: Long) {
            levelAssertion { w!=0L }
            val bytes=byteArrayOf((w ushr 24).toByte(),(w ushr 16).toByte(),(w ushr 8).toByte(),w.toByte())
            val count=if(bytes[1].toInt()==0) 1 else if(bytes[2].toInt()==0) 2 else if(bytes[3].toInt()==0) 3 else 4
            if(len+count<=buffer.size || ensureCapacity(count)) {
                put(buffer,len++,bytes[0].toInt())
                if(bytes[1].toInt()!=0) { put(buffer,len++,bytes[1].toInt());if(bytes[2].toInt()!=0) { put(buffer,len++,bytes[2].toInt());if(bytes[3].toInt()!=0) put(buffer,len++,bytes[3].toInt()) } }
            }
        }
        fun appendReverseWeight16(w: Int) {
            levelAssertion { w and 0xffff != 0 }
            val b0=(w ushr 8).toByte();val b1=w.toByte();val count=if(b1.toInt()==0) 1 else 2
            if(len+count<=buffer.size || ensureCapacity(count)) {
                if(b1.toInt()==0) put(buffer,len++,b0.toInt()) else { put(buffer,len,b1.toInt());put(buffer,len+1,b0.toInt());len+=2 }
            }
        }
        fun appendTo(sink: SortKeyByteSink?) { levelAssertion { len>0 && unit(buffer,len-1).toInt()==1 };sink!!.Append(buffer,len-1) }
        private fun ensureCapacity(appendCapacity: Int): Boolean {
            var capacity=2*buffer.size;val alt=len+2*appendCapacity
            if(capacity<alt) capacity=alt
            if(capacity<200) capacity=200
            if(capacity<0) throw settingsNegativeArraySizeException()
            val next=ByteArray(capacity);copy(buffer,0,next,0,len);buffer=next;return true
        }
    }
    private fun getSortKeyLevel(levels: Int,level: Int): SortKeyLevel? = if(levels and level != 0) SortKeyLevel() else null
    private const val SEC_COMMON_LOW=Collation.COMMON_BYTE
    private const val SEC_COMMON_MIDDLE=SEC_COMMON_LOW+0x20
    const val SEC_COMMON_HIGH=SEC_COMMON_LOW+0x40
    private const val SEC_COMMON_MAX_COUNT=0x21
    private const val CASE_LOWER_FIRST_COMMON_LOW=1
    private const val CASE_LOWER_FIRST_COMMON_MIDDLE=7
    private const val CASE_LOWER_FIRST_COMMON_HIGH=13
    private const val CASE_LOWER_FIRST_COMMON_MAX_COUNT=7
    private const val CASE_UPPER_FIRST_COMMON_LOW=3
    private const val CASE_UPPER_FIRST_COMMON_MAX_COUNT=13
    private const val TER_ONLY_COMMON_LOW=Collation.COMMON_BYTE
    private const val TER_ONLY_COMMON_MIDDLE=TER_ONLY_COMMON_LOW+0x60
    private const val TER_ONLY_COMMON_HIGH=TER_ONLY_COMMON_LOW+0xc0
    private const val TER_ONLY_COMMON_MAX_COUNT=0x61
    private const val TER_LOWER_FIRST_COMMON_LOW=Collation.COMMON_BYTE
    private const val TER_LOWER_FIRST_COMMON_MIDDLE=TER_LOWER_FIRST_COMMON_LOW+0x20
    private const val TER_LOWER_FIRST_COMMON_HIGH=TER_LOWER_FIRST_COMMON_LOW+0x40
    private const val TER_LOWER_FIRST_COMMON_MAX_COUNT=0x21
    private const val TER_UPPER_FIRST_COMMON_LOW=Collation.COMMON_BYTE+0x80
    private const val TER_UPPER_FIRST_COMMON_MIDDLE=TER_UPPER_FIRST_COMMON_LOW+0x20
    private const val TER_UPPER_FIRST_COMMON_HIGH=TER_UPPER_FIRST_COMMON_LOW+0x40
    private const val TER_UPPER_FIRST_COMMON_MAX_COUNT=0x21
    private const val QUAT_COMMON_LOW=0x1c
    private const val QUAT_COMMON_MIDDLE=QUAT_COMMON_LOW+0x70
    private const val QUAT_COMMON_HIGH=QUAT_COMMON_LOW+0xe0
    private const val QUAT_COMMON_MAX_COUNT=0x71
    private const val QUAT_SHIFTED_LIMIT_BYTE=QUAT_COMMON_LOW-1
    private val levelMasks=intArrayOf(2,6,0x16,0x36,0,0,0,0,0,0,0,0,0,0,0,0x36)
    fun writeSortKeyUpToQuaternary(iter: CollationIterator?,compressibleBytes: BooleanArray?,settings: CollationSettings?,sink: SortKeyByteSink?,minLevel: Int,callback: LevelCallback?,preflight: Boolean) {
        val options=settings!!.options;val strength=CollationSettings.getStrength(options)
        if(strength !in levelMasks.indices) throw IndexOutOfBoundsException()
        var levels=levelMasks[strength]
        if(options and CollationSettings.CASE_LEVEL != 0) levels=levels or Collation.CASE_LEVEL_FLAG
        levels=levels and ((1 shl minLevel)-1).inv()
        if(levels==0) return
        val variableTop=if(options and CollationSettings.ALTERNATE_MASK==0) 0L else settings.variableTop+1
        val tertiaryMask=CollationSettings.getTertiaryMask(options);val p234=ByteArray(3)
        val cases=getSortKeyLevel(levels,Collation.CASE_LEVEL_FLAG);val secondaries=getSortKeyLevel(levels,Collation.SECONDARY_LEVEL_FLAG)
        val tertiaries=getSortKeyLevel(levels,Collation.TERTIARY_LEVEL_FLAG);val quaternaries=getSortKeyLevel(levels,Collation.QUATERNARY_LEVEL_FLAG)
        var prevReorderedPrimary=0L;var commonCases=0;var commonSecondaries=0;var commonTertiaries=0;var commonQuaternaries=0
        var prevSecondary=0;var secSegmentStart=0
        while(true) {
            iter!!.clearCEsIfNoneRemaining();var ce=iter.nextCE();var p=ce ushr 32
            if(p<variableTop && p>Collation.MERGE_SEPARATOR_PRIMARY) {
                if(commonQuaternaries!=0) {
                    --commonQuaternaries
                    while(commonQuaternaries>=QUAT_COMMON_MAX_COUNT) { quaternaries!!.appendByte(QUAT_COMMON_MIDDLE);commonQuaternaries-=QUAT_COMMON_MAX_COUNT }
                    quaternaries!!.appendByte(QUAT_COMMON_LOW+commonQuaternaries);commonQuaternaries=0
                }
                do {
                    if(levels and Collation.QUATERNARY_LEVEL_FLAG != 0) {
                        if(settings.hasReordering()) p=settings.reorder(p)
                        if(p.toInt() ushr 24>=QUAT_SHIFTED_LIMIT_BYTE) quaternaries!!.appendByte(QUAT_SHIFTED_LIMIT_BYTE)
                        quaternaries!!.appendWeight32(p)
                    }
                    do { ce=iter.nextCE();p=ce ushr 32 } while(p==0L)
                } while(p<variableTop && p>Collation.MERGE_SEPARATOR_PRIMARY)
            }
            if(p>Collation.NO_CE_PRIMARY && levels and Collation.PRIMARY_LEVEL_FLAG != 0) {
                val index=p.toInt() ushr 24;val compressible=compressibleBytes!!
                if(index !in compressible.indices) throw IndexOutOfBoundsException()
                val isCompressible=compressible[index]
                if(settings.hasReordering()) p=settings.reorder(p)
                val p1=p.toInt() ushr 24
                if(!isCompressible || p1 != prevReorderedPrimary.toInt() ushr 24) {
                    if(prevReorderedPrimary!=0L) {
                        if(p<prevReorderedPrimary) { if(p1>Collation.MERGE_SEPARATOR_BYTE) sink!!.Append(Collation.PRIMARY_COMPRESSION_LOW_BYTE) }
                        else sink!!.Append(Collation.PRIMARY_COMPRESSION_HIGH_BYTE)
                    }
                    sink!!.Append(p1);prevReorderedPrimary=if(isCompressible) p else 0L
                }
                val p2=(p ushr 16).toByte()
                if(p2.toInt()!=0) {
                    p234[0]=p2;p234[1]=(p ushr 8).toByte();p234[2]=p.toByte()
                    sink!!.Append(p234,if(p234[1].toInt()==0) 1 else if(p234[2].toInt()==0) 2 else 3)
                }
                if(!preflight && sink!!.Overflowed()) return
            }
            val lower32=ce.toInt()
            if(lower32==0) continue
            if(levels and Collation.SECONDARY_LEVEL_FLAG != 0) {
                val s=lower32 ushr 16
                if(s==0) Unit
                else if(s==Collation.COMMON_WEIGHT16 && (options and CollationSettings.BACKWARD_SECONDARY==0 || p!=Collation.MERGE_SEPARATOR_PRIMARY)) ++commonSecondaries
                else if(options and CollationSettings.BACKWARD_SECONDARY==0) {
                    if(commonSecondaries!=0) {
                        --commonSecondaries
                        while(commonSecondaries>=SEC_COMMON_MAX_COUNT) { secondaries!!.appendByte(SEC_COMMON_MIDDLE);commonSecondaries-=SEC_COMMON_MAX_COUNT }
                        secondaries!!.appendByte(if(s<Collation.COMMON_WEIGHT16) SEC_COMMON_LOW+commonSecondaries else SEC_COMMON_HIGH-commonSecondaries);commonSecondaries=0
                    }
                    secondaries!!.appendWeight16(s)
                } else {
                    if(commonSecondaries!=0) {
                        --commonSecondaries;val remainder=commonSecondaries%SEC_COMMON_MAX_COUNT
                        secondaries!!.appendByte(if(prevSecondary<Collation.COMMON_WEIGHT16) SEC_COMMON_LOW+remainder else SEC_COMMON_HIGH-remainder)
                        commonSecondaries-=remainder
                        while(commonSecondaries>0) { secondaries.appendByte(SEC_COMMON_MIDDLE);commonSecondaries-=SEC_COMMON_MAX_COUNT }
                    }
                    if(0<p && p<=Collation.MERGE_SEPARATOR_PRIMARY) {
                        val secs=secondaries!!.data();var last=secondaries.length()-1
                        while(secSegmentStart<last) { val b=unit(secs,secSegmentStart);put(secs,secSegmentStart++,unit(secs,last).toInt());put(secs,last--,b.toInt()) }
                        secondaries.appendByte(if(p==Collation.NO_CE_PRIMARY) Collation.LEVEL_SEPARATOR_BYTE else Collation.MERGE_SEPARATOR_BYTE)
                        prevSecondary=0;secSegmentStart=secondaries.length()
                    } else { secondaries!!.appendReverseWeight16(s);prevSecondary=s }
                }
            }
            if(levels and Collation.CASE_LEVEL_FLAG != 0) {
                if(if(CollationSettings.getStrength(options)==0) p==0L else lower32 ushr 16==0) Unit
                else {
                    var c=(lower32 ushr 8) and 255;checkAssertion { c and 0xc0 != 0xc0 }
                    if(c and 0xc0==0 && c>Collation.LEVEL_SEPARATOR_BYTE) ++commonCases
                    else {
                        if(options and CollationSettings.UPPER_FIRST==0) {
                            if(commonCases!=0 && (c>Collation.LEVEL_SEPARATOR_BYTE || !cases!!.isEmpty())) {
                                --commonCases
                                while(commonCases>=CASE_LOWER_FIRST_COMMON_MAX_COUNT) { cases!!.appendByte(CASE_LOWER_FIRST_COMMON_MIDDLE shl 4);commonCases-=CASE_LOWER_FIRST_COMMON_MAX_COUNT }
                                val b=if(c<=Collation.LEVEL_SEPARATOR_BYTE) CASE_LOWER_FIRST_COMMON_LOW+commonCases else CASE_LOWER_FIRST_COMMON_HIGH-commonCases
                                cases!!.appendByte(b shl 4);commonCases=0
                            }
                            if(c>Collation.LEVEL_SEPARATOR_BYTE) c=(CASE_LOWER_FIRST_COMMON_HIGH+(c ushr 6)) shl 4
                        } else {
                            if(commonCases!=0) {
                                --commonCases
                                while(commonCases>=CASE_UPPER_FIRST_COMMON_MAX_COUNT) { cases!!.appendByte(CASE_UPPER_FIRST_COMMON_LOW shl 4);commonCases-=CASE_UPPER_FIRST_COMMON_MAX_COUNT }
                                cases!!.appendByte((CASE_UPPER_FIRST_COMMON_LOW+commonCases) shl 4);commonCases=0
                            }
                            if(c>Collation.LEVEL_SEPARATOR_BYTE) c=(CASE_UPPER_FIRST_COMMON_LOW-(c ushr 6)) shl 4
                        }
                        cases!!.appendByte(c)
                    }
                }
            }
            if(levels and Collation.TERTIARY_LEVEL_FLAG != 0) {
                var t=lower32 and tertiaryMask;checkAssertion { lower32 and 0xc000 != 0xc000 }
                if(t==Collation.COMMON_WEIGHT16) ++commonTertiaries
                else if(tertiaryMask and 0x8000==0) {
                    if(commonTertiaries!=0) {
                        --commonTertiaries
                        while(commonTertiaries>=TER_ONLY_COMMON_MAX_COUNT) { tertiaries!!.appendByte(TER_ONLY_COMMON_MIDDLE);commonTertiaries-=TER_ONLY_COMMON_MAX_COUNT }
                        tertiaries!!.appendByte(if(t<Collation.COMMON_WEIGHT16) TER_ONLY_COMMON_LOW+commonTertiaries else TER_ONLY_COMMON_HIGH-commonTertiaries);commonTertiaries=0
                    }
                    if(t>Collation.COMMON_WEIGHT16) t+=0xc000
                    tertiaries!!.appendWeight16(t)
                } else if(options and CollationSettings.UPPER_FIRST==0) {
                    if(commonTertiaries!=0) {
                        --commonTertiaries
                        while(commonTertiaries>=TER_LOWER_FIRST_COMMON_MAX_COUNT) { tertiaries!!.appendByte(TER_LOWER_FIRST_COMMON_MIDDLE);commonTertiaries-=TER_LOWER_FIRST_COMMON_MAX_COUNT }
                        tertiaries!!.appendByte(if(t<Collation.COMMON_WEIGHT16) TER_LOWER_FIRST_COMMON_LOW+commonTertiaries else TER_LOWER_FIRST_COMMON_HIGH-commonTertiaries);commonTertiaries=0
                    }
                    if(t>Collation.COMMON_WEIGHT16) t+=0x4000
                    tertiaries!!.appendWeight16(t)
                } else {
                    if(t<=Collation.NO_CE_WEIGHT16) Unit
                    else if(lower32 ushr 16 != 0) { t=t xor 0xc000;if(t<(TER_UPPER_FIRST_COMMON_HIGH shl 8)) t-=0x4000 }
                    else { checkAssertion { t in 0x8600..0xbfff };t+=0x4000 }
                    if(commonTertiaries!=0) {
                        --commonTertiaries
                        while(commonTertiaries>=TER_UPPER_FIRST_COMMON_MAX_COUNT) { tertiaries!!.appendByte(TER_UPPER_FIRST_COMMON_MIDDLE);commonTertiaries-=TER_UPPER_FIRST_COMMON_MAX_COUNT }
                        tertiaries!!.appendByte(if(t<(TER_UPPER_FIRST_COMMON_LOW shl 8)) TER_UPPER_FIRST_COMMON_LOW+commonTertiaries else TER_UPPER_FIRST_COMMON_HIGH-commonTertiaries);commonTertiaries=0
                    }
                    tertiaries!!.appendWeight16(t)
                }
            }
            if(levels and Collation.QUATERNARY_LEVEL_FLAG != 0) {
                var q=lower32 and 0xffff
                if(q and 0xc0==0 && q>Collation.NO_CE_WEIGHT16) ++commonQuaternaries
                else if(q==Collation.NO_CE_WEIGHT16 && options and CollationSettings.ALTERNATE_MASK==0 && quaternaries!!.isEmpty()) quaternaries.appendByte(Collation.LEVEL_SEPARATOR_BYTE)
                else {
                    q=if(q==Collation.NO_CE_WEIGHT16) Collation.LEVEL_SEPARATOR_BYTE else 0xfc+((q ushr 6) and 3)
                    if(commonQuaternaries!=0) {
                        --commonQuaternaries
                        while(commonQuaternaries>=QUAT_COMMON_MAX_COUNT) { quaternaries!!.appendByte(QUAT_COMMON_MIDDLE);commonQuaternaries-=QUAT_COMMON_MAX_COUNT }
                        quaternaries!!.appendByte(if(q<QUAT_COMMON_LOW) QUAT_COMMON_LOW+commonQuaternaries else QUAT_COMMON_HIGH-commonQuaternaries);commonQuaternaries=0
                    }
                    quaternaries!!.appendByte(q)
                }
            }
            if(lower32 ushr 24==Collation.LEVEL_SEPARATOR_BYTE) break
        }
        if(levels and Collation.SECONDARY_LEVEL_FLAG != 0) {
            if(!callback!!.needToWrite(Collation.SECONDARY_LEVEL)) return
            sink!!.Append(Collation.LEVEL_SEPARATOR_BYTE);secondaries!!.appendTo(sink)
        }
        if(levels and Collation.CASE_LEVEL_FLAG != 0) {
            if(!callback!!.needToWrite(Collation.CASE_LEVEL)) return
            sink!!.Append(Collation.LEVEL_SEPARATOR_BYTE)
            val length=cases!!.length()-1;var b: Byte=0
            for(i in 0 until length) {
                val c=cases.getAt(i);checkAssertion { c.toInt() and 15==0 && c.toInt()!=0 }
                if(b.toInt()==0) b=c else { sink.Append(b.toInt() or ((c.toInt() shr 4) and 15));b=0 }
            }
            if(b.toInt()!=0) sink.Append(b.toInt())
        }
        if(levels and Collation.TERTIARY_LEVEL_FLAG != 0) {
            if(!callback!!.needToWrite(Collation.TERTIARY_LEVEL)) return
            sink!!.Append(Collation.LEVEL_SEPARATOR_BYTE);tertiaries!!.appendTo(sink)
        }
        if(levels and Collation.QUATERNARY_LEVEL_FLAG != 0) {
            if(!callback!!.needToWrite(Collation.QUATERNARY_LEVEL)) return
            sink!!.Append(Collation.LEVEL_SEPARATOR_BYTE);quaternaries!!.appendTo(sink)
        }
    }
    private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationKeys::class)
    private val levelAssertionsEnabled=assertionsEnabled
    private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
    private inline fun levelAssertion(condition: () -> Boolean) { if(levelAssertionsEnabled && !condition()) throw AssertionError() }
    private fun unit(a: ByteArray?,index: Int): Byte { val bytes=a!!;if(index !in bytes.indices) throw IndexOutOfBoundsException();return bytes[index] }
    private fun put(a: ByteArray?,index: Int,value: Int) { val bytes=a!!;if(index !in bytes.indices) throw IndexOutOfBoundsException();bytes[index]=value.toByte() }
    private fun copy(source: ByteArray?,start: Int,dest: ByteArray?,offset: Int,count: Int) {
        val a=source!!;val b=dest!!
        if(start<0 || offset<0 || count<0 || count>a.size || count>b.size || start>a.size-count || offset>b.size-count) throw IndexOutOfBoundsException()
        a.copyInto(b,offset,start,start+count)
    }
}
