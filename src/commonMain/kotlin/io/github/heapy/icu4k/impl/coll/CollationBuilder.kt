// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

import io.github.heapy.icu4k.impl.Norm2AllModes
import io.github.heapy.icu4k.impl.Normalizer2Impl.Hangul
import io.github.heapy.icu4k.text.CanonicalIterator
import io.github.heapy.icu4k.text.Normalizer2
import io.github.heapy.icu4k.text.UnicodeSet
import io.github.heapy.icu4k.text.UnicodeSetIterator
import io.github.heapy.icu4k.text.UTF16
import io.github.heapy.icu4k.util.ICUInputTooLongException
import io.github.heapy.icu4k.util.ULocale
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.ceStrength
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.isTempCE
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.indexFromTempCE
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.tempCEFromIndexAndStrength
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.isTempCE32
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.indexFromTempCE32
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.strengthFromNode
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.previousIndexFromNode
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.nextIndexFromNode
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.weight32FromNode
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.weight16FromNode
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.isTailoredNode
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.nodeHasBefore2
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.nodeHasBefore3
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.nodeHasAnyBefore
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.nodeFromWeight16
import io.github.heapy.icu4k.impl.coll.CollationNodeGraph.Companion.nodeFromStrength

/** Compiles collation rules into runtime data, settings and canonical closure mappings. */
internal class CollationBuilder(b: CollationTailoring?) : CollationRuleParser.Sink() {
    private val nfd=Normalizer2.getNFDInstance()
    private val fcd=Norm2AllModes.getFCDNormalizer2()
    private val nfcImpl=Norm2AllModes.getNFCInstance().impl
    private val base=b!!
    private val baseData=base.data!!
    private val graph=CollationNodeGraph(baseData,assertionsEnabled)
    private val rootElements get()=graph.rootElements
    private val nodes get()=graph.nodes
    private var variableTop=0L
    private var dataBuilder: CollationDataBuilder?=CollationDataBuilder()
    private var fastLatinEnabled=true
    private val optimizeSet=UnicodeSet()
    private val ces=LongArray(Collation.MAX_EXPANSION_LENGTH)
    private var cesLength=0
    init { nfcImpl.ensureCanonIterData();dataBuilder!!.initForTailoring(baseData) }
    fun parseAndBuild(ruleString: String?): CollationTailoring {
        if(baseData.rootElements==null) throw UnsupportedOperationException("missing root elements data, tailoring not supported")
        val tailoring=CollationTailoring(base.settings);val parser=CollationRuleParser(baseData)
        variableTop=base.settings!!.readOnly()!!.variableTop;parser.setSink(this)
        parser.setImporter { locale,type -> CollationLoader.loadRules(ULocale(locale),type) }
        val settings=tailoring.settings!!.copyOnWrite();parser.parse(ruleString,settings)
        if(dataBuilder!!.hasMappings()) {
            graph.makeTailoredCEs();closeOverComposites();finalizeCEs();optimizeSet.add(0,0x7f);optimizeSet.add(0xc0,0xff);optimizeSet.remove(Hangul.HANGUL_BASE,Hangul.HANGUL_END)
            dataBuilder!!.optimize(optimizeSet);tailoring.ensureOwnedData();if(fastLatinEnabled) dataBuilder!!.enableFastLatin();dataBuilder!!.build(tailoring.ownedData);dataBuilder=null
        } else tailoring.data=baseData
        settings.fastLatinOptions=CollationFastLatin.getOptions(tailoring.data,settings,settings.fastLatinPrimaries)
        tailoring.setRules(ruleString);tailoring.setVersion(base.version,0);return tailoring
    }
    override fun addReset(initialStrength: Int,str: CharSequence) {
        var strength=initialStrength;checkAssertion { str.isNotEmpty() }
        if(str[0]==CollationRuleParser.POS_LEAD) { ces[0]=getSpecialResetPosition(str);cesLength=1;checkAssertion { ces[0] and Collation.CASE_AND_QUATERNARY_MASK.toLong()==0L } }
        else { cesLength=dataBuilder!!.getCEs(nfd.normalize(str),ces,0);if(cesLength>Collation.MAX_EXPANSION_LENGTH) throw IllegalArgumentException("reset position maps to too many collation elements (more than 31)") }
        if(strength==15) return
        checkAssertion { strength in 0..2 };var index=findOrInsertNodeForCEs(strength);var node=nodes.elementAti(index)
        while(strengthFromNode(node)>strength) { index=previousIndexFromNode(node);node=nodes.elementAti(index) }
        if(strengthFromNode(node)==strength && isTailoredNode(node)) index=previousIndexFromNode(node)
        else if(strength==0) {
            var p=weight32FromNode(node)
            if(p==0L) throw UnsupportedOperationException("reset primary-before ignorable not possible")
            if(p<=rootElements.getFirstPrimary()) throw UnsupportedOperationException("reset primary-before first non-ignorable not supported")
            if(p==Collation.FIRST_TRAILING_PRIMARY) throw UnsupportedOperationException("reset primary-before [first trailing] not supported")
            p=rootElements.getPrimaryBefore(p,baseData.isCompressiblePrimary(p));index=graph.findOrInsertNodeForPrimary(p)
            while(true) { node=nodes.elementAti(index);val next=nextIndexFromNode(node);if(next==0) break;index=next }
        } else {
            index=graph.findCommonNode(index,1);if(strength>=2) index=graph.findCommonNode(index,2);node=nodes.elementAti(index)
            if(strengthFromNode(node)==strength) {
                var weight=weight16FromNode(node)
                if(weight==0) throw UnsupportedOperationException(if(strength==1) "reset secondary-before secondary ignorable not possible" else "reset tertiary-before completely ignorable not possible")
                checkAssertion { weight>Collation.BEFORE_WEIGHT16 };weight=graph.getWeight16Before(index,node,strength)
                val previous=previousIndexFromNode(node);var i=previous;val previousWeight: Int
                while(true) {
                    node=nodes.elementAti(i);val previousStrength=strengthFromNode(node)
                    if(previousStrength<strength) { checkAssertion { weight>=Collation.COMMON_WEIGHT16 || i==previous };previousWeight=Collation.COMMON_WEIGHT16;break }
                    if(previousStrength==strength && !isTailoredNode(node)) { previousWeight=weight16FromNode(node);break }
                    i=previousIndexFromNode(node)
                }
                index=if(previousWeight==weight) previous else graph.insertNodeBetween(previous,index,nodeFromWeight16(weight) or nodeFromStrength(strength))
            } else index=graph.findOrInsertWeakNode(index,graph.getWeight16Before(index,node,strength),strength)
            strength=ceStrength(longAt(ces,cesLength-1))
        }
        setLong(ces,cesLength-1,tempCEFromIndexAndStrength(index,strength))
    }
    private fun getSpecialResetPosition(str: CharSequence): Long {
        checkAssertion { str.length==2 };val posIndex=str[1].code-CollationRuleParser.POS_BASE.code
        if(posIndex !in CollationRuleParser.POSITION_VALUES.indices) throw IndexOutOfBoundsException()
        val pos=CollationRuleParser.POSITION_VALUES[posIndex];var strength=0;var boundary=false;var ce: Long
        when(pos) {
            CollationRuleParser.Position.FIRST_TERTIARY_IGNORABLE,CollationRuleParser.Position.LAST_TERTIARY_IGNORABLE -> return 0
            CollationRuleParser.Position.FIRST_SECONDARY_IGNORABLE -> {
                var index=graph.findOrInsertNodeForRootCE(0,2);var node=nodes.elementAti(index);index=nextIndexFromNode(node)
                if(index!=0) { node=nodes.elementAti(index);checkAssertion { strengthFromNode(node)<=2 };if(isTailoredNode(node) && strengthFromNode(node)==2) return tempCEFromIndexAndStrength(index,2) }
                return rootElements.getFirstTertiaryCE()
            }
            CollationRuleParser.Position.LAST_SECONDARY_IGNORABLE -> { ce=rootElements.getLastTertiaryCE();strength=2 }
            CollationRuleParser.Position.FIRST_PRIMARY_IGNORABLE -> {
                var index=graph.findOrInsertNodeForRootCE(0,1);var node=nodes.elementAti(index)
                while(true) {
                    index=nextIndexFromNode(node);if(index==0) break;node=nodes.elementAti(index);strength=strengthFromNode(node)
                    if(strength<1) break
                    if(strength==1) {
                        if(isTailoredNode(node)) { if(nodeHasBefore3(node)) { index=nextIndexFromNode(nodes.elementAti(nextIndexFromNode(node)));checkAssertion { isTailoredNode(nodes.elementAti(index)) } };return tempCEFromIndexAndStrength(index,1) }
                        break
                    }
                }
                ce=rootElements.getFirstSecondaryCE();strength=1
            }
            CollationRuleParser.Position.LAST_PRIMARY_IGNORABLE -> { ce=rootElements.getLastSecondaryCE();strength=1 }
            CollationRuleParser.Position.FIRST_VARIABLE -> { ce=rootElements.getFirstPrimaryCE();boundary=true }
            CollationRuleParser.Position.LAST_VARIABLE -> ce=rootElements.lastCEWithPrimaryBefore(variableTop+1)
            CollationRuleParser.Position.FIRST_REGULAR -> { ce=rootElements.firstCEWithPrimaryAtLeast(variableTop+1);boundary=true }
            CollationRuleParser.Position.LAST_REGULAR -> ce=rootElements.firstCEWithPrimaryAtLeast(baseData.getFirstPrimaryForGroup(17))
            CollationRuleParser.Position.FIRST_IMPLICIT -> ce=baseData.getSingleCE(0x4e00)
            CollationRuleParser.Position.LAST_IMPLICIT -> throw UnsupportedOperationException("reset to [last implicit] not supported")
            CollationRuleParser.Position.FIRST_TRAILING -> { ce=Collation.makeCE(Collation.FIRST_TRAILING_PRIMARY);boundary=true }
            CollationRuleParser.Position.LAST_TRAILING -> throw IllegalArgumentException("LDML forbids tailoring to U+FFFF")
        }
        var index=graph.findOrInsertNodeForRootCE(ce,strength);var node=nodes.elementAti(index)
        if(pos.ordinal and 1==0) {
            if(!nodeHasAnyBefore(node) && boundary) {
                index=nextIndexFromNode(node)
                if(index!=0) { node=nodes.elementAti(index);checkAssertion { isTailoredNode(node) };ce=tempCEFromIndexAndStrength(index,strength) }
                else {
                    checkAssertion { strength==0 };var p=ce ushr 32;val pIndex=rootElements.findPrimary(p);p=rootElements.getPrimaryAfter(p,pIndex,baseData.isCompressiblePrimary(p));ce=Collation.makeCE(p)
                    index=graph.findOrInsertNodeForRootCE(ce,0);node=nodes.elementAti(index)
                }
            }
            if(nodeHasAnyBefore(node)) {
                if(nodeHasBefore2(node)) { index=nextIndexFromNode(nodes.elementAti(nextIndexFromNode(node)));node=nodes.elementAti(index) }
                if(nodeHasBefore3(node)) index=nextIndexFromNode(nodes.elementAti(nextIndexFromNode(node)))
                checkAssertion { isTailoredNode(nodes.elementAti(index)) };ce=tempCEFromIndexAndStrength(index,strength)
            }
        } else {
            while(true) { val next=nextIndexFromNode(node);if(next==0) break;val nextNode=nodes.elementAti(next);if(strengthFromNode(nextNode)<strength) break;index=next;node=nextNode }
            if(isTailoredNode(node)) ce=tempCEFromIndexAndStrength(index,strength)
        }
        return ce
    }
    override fun addRelation(strength: Int,prefix: CharSequence,str: CharSequence,extension: CharSequence) {
        val nfdPrefix=if(prefix.isEmpty()) "" else nfd.normalize(prefix);val nfdString=nfd.normalize(str);val length=nfdString.length
        if(length>=2) {
            var c=nfdString[0].code
            if(Hangul.isJamoL(c) || Hangul.isJamoV(c)) throw UnsupportedOperationException("contractions starting with conjoining Jamo L or V not supported")
            c=nfdString[length-1].code
            if(Hangul.isJamoL(c) || (Hangul.isJamoV(c) && Hangul.isJamoL(nfdString[length-2].code))) throw UnsupportedOperationException("contractions ending with conjoining Jamo L or L+V not supported")
        }
        if(strength!=15) {
            var index=findOrInsertNodeForCEs(strength);checkAssertion { cesLength>0 };val ce=longAt(ces,cesLength-1)
            if(strength==0 && !isTempCE(ce) && ce ushr 32==0L) throw UnsupportedOperationException("tailoring primary after ignorables not supported")
            if(strength==3 && ce==0L) throw UnsupportedOperationException("tailoring quaternary after tertiary ignorables not supported")
            index=graph.insertTailoredNodeAfter(index,strength);var tempStrength=ceStrength(ce);if(strength<tempStrength) tempStrength=strength
            setLong(ces,cesLength-1,tempCEFromIndexAndStrength(index,tempStrength))
        }
        setCaseBits(nfdString);val beforeExtension=cesLength
        if(extension.isNotEmpty()) { cesLength=dataBuilder!!.getCEs(nfd.normalize(extension),ces,cesLength);if(cesLength>Collation.MAX_EXPANSION_LENGTH) throw IllegalArgumentException("extension string adds too many collation elements (more than 31 total)") }
        var ce32=Collation.UNASSIGNED_CE32
        if((!contentEquals(nfdPrefix,prefix) || !contentEquals(nfdString,str)) && !ignorePrefix(prefix) && !ignoreString(str)) ce32=addIfDifferent(prefix,str,ces,cesLength,ce32)
        addWithClosure(nfdPrefix,nfdString,ces,cesLength,ce32);cesLength=beforeExtension
    }
    private fun findOrInsertNodeForCEs(strength: Int): Int {
        checkAssertion { strength in 0..3 };val ce: Long
        while(true) {
            if(cesLength==0) { ces[0]=0;cesLength=1;ce=0;break }
            val current=longAt(ces,cesLength-1)
            if(ceStrength(current)<=strength) { ce=current;break };--cesLength
        }
        if(isTempCE(ce)) return indexFromTempCE(ce)
        if((ce ushr 56).toInt()==Collation.UNASSIGNED_IMPLICIT_BYTE) throw UnsupportedOperationException("tailoring relative to an unassigned code point not supported")
        return graph.findOrInsertNodeForRootCE(ce,strength)
    }
    private fun setCaseBits(nfdString: CharSequence) {
        var count=0;for(i in 0 until cesLength) if(ceStrength(longAt(ces,i))==0) ++count;checkAssertion { count<=31 };var cases=0L
        if(count>0) {
            val iter=UTF16CollationIterator(baseData,false,nfdString,0);val length=iter.fetchCEs()-1;checkAssertion { length>=0 && iter.getCE(length)==Collation.NO_CE }
            var lastCase=0;var baseCount=0
            for(i in 0 until length) {
                val ce=iter.getCE(i)
                if(ce ushr 32!=0L) { ++baseCount;val c=(ce.toInt() shr 14) and 3;checkAssertion { c==0 || c==2 }
                    if(baseCount<count) cases=cases or (c.toLong() shl ((baseCount-1)*2)) else if(baseCount==count) lastCase=c else if(c!=lastCase) { lastCase=1;break }
                }
            }
            if(baseCount>=count) cases=cases or (lastCase.toLong() shl ((count-1)*2))
        }
        for(i in 0 until cesLength) { var ce=longAt(ces,i) and -49153L;val strength=ceStrength(ce)
            if(strength==0) { ce=ce or ((cases and 3L) shl 14);cases=cases ushr 2 } else if(strength==2) ce=ce or 0x8000L
            setLong(ces,i,ce)
        }
    }
    override fun suppressContractions(set: UnicodeSet) { dataBuilder!!.suppressContractions(set) }
    override fun optimize(set: UnicodeSet) { optimizeSet.addAll(set) }
    private fun addWithClosure(prefix: CharSequence,str: CharSequence,newCEs: LongArray,length: Int,initial: Int): Int {
        var ce32=addIfDifferent(prefix,str,newCEs,length,initial);ce32=addOnlyClosure(prefix,str,newCEs,length,ce32);addTailComposites(prefix,str);return ce32
    }
    private fun addOnlyClosure(prefix: CharSequence,str: CharSequence,newCEs: LongArray,length: Int,initial: Int): Int {
        var ce32=initial;var loop=0
        if(prefix.isEmpty()) {
            val iter=CanonicalIterator(str.toString())
            while(true) { val s=iter.next() ?: break;if(ignoreString(s) || contentEquals(s,str)) continue
                if(loop++>kClosureLoopLimit) throw ICUInputTooLongException("Too many closure")
                ce32=addIfDifferent("",s,newCEs,length,ce32)
            }
        } else {
            val prefixes=CanonicalIterator(prefix.toString());val strings=CanonicalIterator(str.toString())
            while(true) {
                val p=prefixes.next() ?: break;if(ignorePrefix(p)) continue;val same=contentEquals(p,prefix)
                while(true) { val s=strings.next() ?: break;if(ignoreString(s) || (same && contentEquals(s,str))) continue
                    if(loop++>kClosureLoopLimit) throw ICUInputTooLongException("Too many closure")
                    ce32=addIfDifferent(p,s,newCEs,length,ce32)
                }
                strings.reset()
            }
        }
        return ce32
    }
    private fun addTailComposites(prefix: CharSequence,str: CharSequence) {
        var after=str.length;val lastStarter: Int
        while(true) { if(after==0) return;val c=cpBefore(str,after);if(nfd.getCombiningClass(c)==0) { lastStarter=c;break };after-=charCount(c) }
        if(Hangul.isJamoL(lastStarter)) return
        val composites=UnicodeSet();if(!nfcImpl.getCanonStartSet(lastStarter,composites)) return
        val newNFD=StringBuilder();val newString=StringBuilder();val newCEs=LongArray(Collation.MAX_EXPANSION_LENGTH);val iter=UnicodeSetIterator(composites)
        while(iter.next()) {
            checkAssertion { iter.codepoint!=UnicodeSetIterator.IS_STRING };val composite=iter.codepoint;val decomp=nfd.getDecomposition(composite)
            if(!mergeCompositeIntoString(str,after,composite,decomp!!,newNFD,newString)) continue
            val length=dataBuilder!!.getCEs(prefix,newNFD,newCEs,0);if(length>Collation.MAX_EXPANSION_LENGTH) continue
            val ce32=addIfDifferent(prefix,newString,newCEs,length,Collation.UNASSIGNED_CE32)
            if(ce32!=Collation.UNASSIGNED_CE32) addOnlyClosure(prefix,newNFD,newCEs,length,ce32)
        }
    }
    private fun mergeCompositeIntoString(str: CharSequence,after: Int,composite: Int,decomp: CharSequence,newNFD: StringBuilder,newString: StringBuilder): Boolean {
        checkAssertion { cpBefore(str,after)==cpAt(decomp,0) };val starterLength=charCount(cpAt(decomp,0))
        if(starterLength==decomp.length) return false
        if(equalSubSequences(str,after,decomp,starterLength)) return false
        newNFD.setLength(0);newNFD.append(str,0,after);newString.setLength(0);newString.append(str,0,after-starterLength);UTF16.append(newString,composite)
        var sourceIndex=after;var decompIndex=starterLength;var sourceChar=Collation.SENTINEL_CP;var sourceCC=0;var decompCC=0
        while(true) {
            if(sourceChar<0) { if(sourceIndex>=str.length) break;sourceChar=cpAt(str,sourceIndex);sourceCC=nfd.getCombiningClass(sourceChar);checkAssertion { sourceCC!=0 } }
            if(decompIndex>=decomp.length) break
            val c=cpAt(decomp,decompIndex);decompCC=nfd.getCombiningClass(c)
            if(decompCC==0 || sourceCC<decompCC) return false
            if(decompCC<sourceCC) { UTF16.append(newNFD,c);decompIndex+=charCount(c) }
            else if(c!=sourceChar) return false
            else { UTF16.append(newNFD,c);decompIndex+=charCount(c);sourceIndex+=charCount(c);sourceChar=Collation.SENTINEL_CP }
        }
        if(sourceChar>=0) { if(sourceCC<decompCC) return false;newNFD.append(str,sourceIndex,str.length);newString.append(str,sourceIndex,str.length) }
        else if(decompIndex<decomp.length) newNFD.append(decomp,decompIndex,decomp.length)
        checkAssertion { nfd.isNormalized(newNFD) };checkAssertion { fcd.isNormalized(newString) };checkAssertion { nfd.normalize(newString)==newNFD.toString() };return true
    }
    private fun equalSubSequences(left: CharSequence,start: Int,right: CharSequence,rightStart: Int): Boolean {
        val length=left.length;if(length-start!=right.length-rightStart) return false
        var i=start;var j=rightStart;while(i<length) if(left[i++]!=right[j++]) return false;return true
    }
    private fun ignorePrefix(s: CharSequence): Boolean = !fcd.isNormalized(s)
    private fun ignoreString(s: CharSequence): Boolean = !fcd.isNormalized(s) || Hangul.isHangul(s[0].code)
    private fun closeOverComposites() {
        val iter=UnicodeSetIterator(COMPOSITES)
        while(iter.next()) {
            checkAssertion { iter.codepoint!=UnicodeSetIterator.IS_STRING };val nfdString=nfd.getDecomposition(iter.codepoint);cesLength=dataBuilder!!.getCEs(nfdString,ces,0)
            if(cesLength>Collation.MAX_EXPANSION_LENGTH) continue
            addIfDifferent("",iter.getString()!!,ces,cesLength,Collation.UNASSIGNED_CE32)
        }
    }
    private fun addIfDifferent(prefix: CharSequence,str: CharSequence,newCEs: LongArray,length: Int,initial: Int): Int {
        var ce32=initial;val oldCEs=LongArray(Collation.MAX_EXPANSION_LENGTH);val oldLength=dataBuilder!!.getCEs(prefix,str,oldCEs,0)
        if(!sameCEs(newCEs,length,oldCEs,oldLength)) { if(ce32==Collation.UNASSIGNED_CE32) ce32=dataBuilder!!.encodeCEs(newCEs,length);dataBuilder!!.addCE32(prefix,str,ce32) };return ce32
    }
    private class CEFinalizer(private val finalCEs: LongArray): CollationDataBuilder.CEModifier {
        override fun modifyCE32(ce32: Int): Long { checkAssertion { !Collation.isSpecialCE32(ce32) };return if(isTempCE32(ce32)) longAt(finalCEs,indexFromTempCE32(ce32)) or ((ce32 and 0xc0) shl 8).toLong() else Collation.NO_CE }
        override fun modifyCE(ce: Long): Long = if(isTempCE(ce)) longAt(finalCEs,indexFromTempCE(ce)) or (ce and 0xc000L) else Collation.NO_CE
    }
    private fun finalizeCEs() { val builder=CollationDataBuilder();builder.initForTailoring(baseData);builder.copyFrom(dataBuilder,CEFinalizer(nodes.getBuffer()));dataBuilder=builder }
    companion object {
        private val assertionsEnabled=collationIteratorClassAssertionsEnabled(CollationBuilder::class)
        private val COMPOSITES=UnicodeSet("[:NFD_QC=N:]").remove(Hangul.HANGUL_BASE,Hangul.HANGUL_END)
        private var kClosureLoopLimit=6560
        private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
        private fun sameCEs(a: LongArray,n: Int,b: LongArray,m: Int): Boolean { if(n!=m) return false;checkAssertion { n<=Collation.MAX_EXPANSION_LENGTH };for(i in 0 until n) if(longAt(a,i)!=longAt(b,i)) return false;return true }
        private fun contentEquals(a: CharSequence,b: CharSequence): Boolean { if(a.length!=b.length) return false;for(i in a.indices) if(a[i]!=b[i]) return false;return true }
        private fun longAt(a: LongArray,i: Int): Long { if(i !in a.indices) throw IndexOutOfBoundsException();return a[i] }
        private fun setLong(a: LongArray,i: Int,value: Long) { if(i !in a.indices) throw IndexOutOfBoundsException();a[i]=value }
        private fun charCount(c: Int): Int = if(c>=0x10000) 2 else 1
        private fun cpAt(s: CharSequence,i: Int): Int {
            if(i !in s.indices) throw IndexOutOfBoundsException();val c=s[i].code
            if(c in 0xd800..0xdbff && i+1<s.length) { val t=s[i+1].code;if(t in 0xdc00..0xdfff) return ((c-0xd800) shl 10)+t-0xdc00+0x10000 };return c
        }
        private fun cpBefore(s: CharSequence,i: Int): Int {
            if(i-1 !in s.indices) throw IndexOutOfBoundsException();val c=s[i-1].code
            if(c in 0xdc00..0xdfff && i>1) { val lead=s[i-2].code;if(lead in 0xd800..0xdbff) return ((lead-0xd800) shl 10)+c-0xdc00+0x10000 };return c
        }
    }
}
