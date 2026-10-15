// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2013-2015, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.coll

/** Stable-index tailoring graph extracted from ICU4J CollationBuilder. */
internal class CollationNodeGraph(private val baseData: CollationData,private val assertionsEnabled: Boolean=collationIteratorClassAssertionsEnabled(CollationNodeGraph::class)) {
    val rootElements=CollationRootElements(baseData.rootElements)
    val rootPrimaryIndexes=UVector32()
    val nodes=UVector64()
    fun findOrInsertNodeForRootCE(ce: Long,strength: Int): Int {
        checkAssertion { (ce ushr 56).toInt()!=Collation.UNASSIGNED_IMPLICIT_BYTE };checkAssertion { ce and 0xc0L==0L }
        var index=findOrInsertNodeForPrimary(ce ushr 32)
        if(strength>=1) { val lower=ce.toInt();index=findOrInsertWeakNode(index,lower ushr 16,1);if(strength>=2) index=findOrInsertWeakNode(index,lower and Collation.ONLY_TERTIARY_MASK,2) }
        return index
    }
    fun findOrInsertNodeForPrimary(p: Long): Int {
        val rootIndex=binarySearchForRootPrimaryNode(rootPrimaryIndexes.getBuffer(),rootPrimaryIndexes.size(),nodes.getBuffer(),p)
        if(rootIndex>=0) return rootPrimaryIndexes.elementAti(rootIndex)
        val index=nodes.size();nodes.addElement(nodeFromWeight32(p));rootPrimaryIndexes.insertElementAt(index,rootIndex.inv());return index
    }
    fun findOrInsertWeakNode(initialIndex: Int,weight16: Int,level: Int): Int {
        var index=initialIndex;checkAssertion { index>=0 && index<nodes.size() };checkAssertion { level in 1..2 }
        if(weight16==Collation.COMMON_WEIGHT16) return findCommonNode(index,level)
        var node=nodes.elementAti(index);checkAssertion { strengthFromNode(node)<level }
        if(weight16!=0 && weight16<Collation.COMMON_WEIGHT16) {
            val flag=if(level==1) HAS_BEFORE2 else HAS_BEFORE3
            if(node and flag.toLong()==0L) {
                var common=nodeFromWeight16(Collation.COMMON_WEIGHT16) or nodeFromStrength(level)
                if(level==1) { common=common or (node and HAS_BEFORE3.toLong());node=node and HAS_BEFORE3.toLong().inv() }
                nodes.setElementAt(node or flag.toLong(),index);val next=nextIndexFromNode(node)
                node=nodeFromWeight16(weight16) or nodeFromStrength(level);index=insertNodeBetween(index,next,node);insertNodeBetween(index,next,common);return index
            }
        }
        var next: Int
        while(true) {
            next=nextIndexFromNode(node);if(next==0) break
            node=nodes.elementAti(next);val strength=strengthFromNode(node)
            if(strength<=level) {
                if(strength<level) break
                if(!isTailoredNode(node)) { val weight=weight16FromNode(node);if(weight==weight16) return next;if(weight>weight16) break }
            }
            index=next
        }
        return insertNodeBetween(index,next,nodeFromWeight16(weight16) or nodeFromStrength(level))
    }
    fun insertTailoredNodeAfter(initialIndex: Int,strength: Int): Int {
        var index=initialIndex;checkAssertion { index>=0 && index<nodes.size() }
        if(strength>=1) { index=findCommonNode(index,1);if(strength>=2) index=findCommonNode(index,2) }
        var node=nodes.elementAti(index);var next: Int
        while(true) { next=nextIndexFromNode(node);if(next==0) break;node=nodes.elementAti(next);if(strengthFromNode(node)<=strength) break;index=next }
        return insertNodeBetween(index,next,IS_TAILORED.toLong() or nodeFromStrength(strength))
    }
    fun insertNodeBetween(index: Int,nextIndex: Int,initialNode: Long): Int {
        checkAssertion { previousIndexFromNode(initialNode)==0 };checkAssertion { nextIndexFromNode(initialNode)==0 };checkAssertion { nextIndexFromNode(nodes.elementAti(index))==nextIndex }
        val newIndex=nodes.size();var node=initialNode or nodeFromPreviousIndex(index) or nodeFromNextIndex(nextIndex);nodes.addElement(node)
        node=nodes.elementAti(index);nodes.setElementAt(changeNodeNextIndex(node,newIndex),index)
        if(nextIndex!=0) { node=nodes.elementAti(nextIndex);nodes.setElementAt(changeNodePreviousIndex(node,newIndex),nextIndex) }
        return newIndex
    }
    fun findCommonNode(initialIndex: Int,strength: Int): Int {
        checkAssertion { strength in 1..2 };var index=initialIndex;var node=nodes.elementAti(index)
        if(strengthFromNode(node)>=strength) return index
        if(if(strength==1) !nodeHasBefore2(node) else !nodeHasBefore3(node)) return index
        index=nextIndexFromNode(node);node=nodes.elementAti(index)
        checkAssertion { !isTailoredNode(node) && strengthFromNode(node)==strength && weight16FromNode(node)<Collation.COMMON_WEIGHT16 }
        do { index=nextIndexFromNode(node);node=nodes.elementAti(index);checkAssertion { strengthFromNode(node)>=strength } } while(isTailoredNode(node) || strengthFromNode(node)>strength || weight16FromNode(node)<Collation.COMMON_WEIGHT16)
        checkAssertion { weight16FromNode(node)==Collation.COMMON_WEIGHT16 };return index
    }
    fun getWeight16Before(initialIndex: Int,initialNode: Long,level: Int): Int {
        var index=initialIndex;var node=initialNode;checkAssertion { strengthFromNode(node)<level || !isTailoredNode(node) }
        val t=if(strengthFromNode(node)==2) weight16FromNode(node) else Collation.COMMON_WEIGHT16
        while(strengthFromNode(node)>1) { index=previousIndexFromNode(node);node=nodes.elementAti(index) }
        if(isTailoredNode(node)) return Collation.BEFORE_WEIGHT16
        val s=if(strengthFromNode(node)==1) weight16FromNode(node) else Collation.COMMON_WEIGHT16
        while(strengthFromNode(node)>0) { index=previousIndexFromNode(node);node=nodes.elementAti(index) }
        if(isTailoredNode(node)) return Collation.BEFORE_WEIGHT16
        val p=weight32FromNode(node)
        return if(level==1) rootElements.getSecondaryBefore(p,s) else rootElements.getTertiaryBefore(p,s,t).also { weight -> checkAssertion { weight and Collation.ONLY_TERTIARY_MASK.inv()==0 } }
    }
    /** Replaces tailored graph nodes with final CEs, destroying their links as in ICU4J. */
    fun makeTailoredCEs() {
        val primaries=CollationWeights();val secondaries=CollationWeights();val tertiaries=CollationWeights();val array=nodes.getBuffer()
        for(rpi in 0 until rootPrimaryIndexes.size()) {
            var i=rootPrimaryIndexes.elementAti(rpi);var node=array[i];var p=weight32FromNode(node);var s=if(p==0L) 0 else Collation.COMMON_WEIGHT16;var t=s;var q=0
            var pTailored=false;var sTailored=false;var tTailored=false;val pIndex=if(p==0L) 0 else rootElements.findPrimary(p);var next=nextIndexFromNode(node)
            while(next!=0) {
                i=next;node=array[i];next=nextIndexFromNode(node);val strength=strengthFromNode(node)
                if(strength==3) { checkAssertion { isTailoredNode(node) };if(q==3) throw UnsupportedOperationException("quaternary tailoring gap too small");++q }
                else {
                    if(strength==2) {
                        if(isTailoredNode(node)) {
                            if(!tTailored) {
                                val count=countTailoredNodes(array,next,2)+1
                                val limit=when { t==0 -> { t=rootElements.getTertiaryBoundary()-0x100;rootElements.getFirstTertiaryCE().toInt() and Collation.ONLY_TERTIARY_MASK };!pTailored && !sTailored -> rootElements.getTertiaryAfter(pIndex,s,t);t==Collation.BEFORE_WEIGHT16 -> Collation.COMMON_WEIGHT16;else -> { checkAssertion { t==Collation.COMMON_WEIGHT16 };rootElements.getTertiaryBoundary() } }
                                checkAssertion { limit==0x4000 || limit and Collation.ONLY_TERTIARY_MASK.inv()==0 };tertiaries.initForTertiary()
                                if(!tertiaries.allocWeights(t.toLong(),limit.toLong(),count)) throw UnsupportedOperationException("tertiary tailoring gap too small")
                                tTailored=true
                            }
                            t=tertiaries.nextWeight().toInt();checkAssertion { t!=-1 }
                        } else { t=weight16FromNode(node);tTailored=false }
                    } else {
                        if(strength==1) {
                            if(isTailoredNode(node)) {
                                if(!sTailored) {
                                    val count=countTailoredNodes(array,next,1)+1
                                    val limit=when { s==0 -> { s=rootElements.getSecondaryBoundary()-0x100;(rootElements.getFirstSecondaryCE() shr 16).toInt() };!pTailored -> rootElements.getSecondaryAfter(pIndex,s);s==Collation.BEFORE_WEIGHT16 -> Collation.COMMON_WEIGHT16;else -> { checkAssertion { s==Collation.COMMON_WEIGHT16 };rootElements.getSecondaryBoundary() } }
                                    if(s==Collation.COMMON_WEIGHT16) s=rootElements.getLastCommonSecondary()
                                    secondaries.initForSecondary();if(!secondaries.allocWeights(s.toLong(),limit.toLong(),count)) throw UnsupportedOperationException("secondary tailoring gap too small");sTailored=true
                                }
                                s=secondaries.nextWeight().toInt();checkAssertion { s!=-1 }
                            } else { s=weight16FromNode(node);sTailored=false }
                        } else {
                            checkAssertion { isTailoredNode(node) }
                            if(!pTailored) {
                                val count=countTailoredNodes(array,next,0)+1;val compressible=baseData.isCompressiblePrimary(p);val limit=rootElements.getPrimaryAfter(p,pIndex,compressible)
                                primaries.initForPrimary(compressible);if(!primaries.allocWeights(p,limit,count)) throw UnsupportedOperationException("primary tailoring gap too small");pTailored=true
                            }
                            p=primaries.nextWeight();checkAssertion { p!=0xffffffffL };s=Collation.COMMON_WEIGHT16;sTailored=false
                        }
                        t=if(s==0) 0 else Collation.COMMON_WEIGHT16;tTailored=false
                    }
                    q=0
                }
                if(isTailoredNode(node)) array[i]=Collation.makeCE(p,s,t,q)
            }
        }
    }
    private inline fun checkAssertion(condition: () -> Boolean) { if(assertionsEnabled && !condition()) throw AssertionError() }
    companion object {
        const val MAX_INDEX=0xfffff
        const val HAS_BEFORE2=0x40
        const val HAS_BEFORE3=0x20
        const val IS_TAILORED=8
        fun tempCEFromIndexAndStrength(index: Int,strength: Int): Long = 0x4040000006002000L+((index and 0xfe000).toLong() shl 43)+((index and 0x1fc0).toLong() shl 42)+((index and 0x3f) shl 24).toLong()+(strength shl 8).toLong()
        fun indexFromTempCE(initial: Long): Int { val ce=initial-0x4040000006002000L;return ((ce shr 43).toInt() and 0xfe000) or ((ce shr 42).toInt() and 0x1fc0) or ((ce shr 24).toInt() and 0x3f) }
        fun strengthFromTempCE(ce: Long): Int = (ce.toInt() shr 8) and 3
        fun isTempCE(ce: Long): Boolean = (ce.toInt() ushr 24) in 6..0x45
        fun indexFromTempCE32(initial: Int): Int { val ce=initial-0x40400620;return ((ce shr 11) and 0xfe000) or ((ce shr 10) and 0x1fc0) or ((ce shr 8) and 0x3f) }
        fun isTempCE32(ce32: Int): Boolean = ce32 and 255>=2 && ((ce32 shr 8) and 255) in 6..0x45
        fun ceStrength(ce: Long): Int = when { isTempCE(ce) -> strengthFromTempCE(ce);ce and -72057594037927936L!=0L -> 0;ce.toInt() and -16777216!=0 -> 1;ce!=0L -> 2;else -> 15 }
        fun nodeFromWeight32(weight: Long): Long = weight shl 32
        fun nodeFromWeight16(weight: Int): Long = weight.toLong() shl 48
        fun nodeFromPreviousIndex(previous: Int): Long = previous.toLong() shl 28
        // Source shifts as Int before widening to Long.
        fun nodeFromNextIndex(next: Int): Long = (next shl 8).toLong()
        fun nodeFromStrength(strength: Int): Long = strength.toLong()
        fun weight32FromNode(node: Long): Long = node ushr 32
        fun weight16FromNode(node: Long): Int = (node shr 48).toInt() and 0xffff
        fun previousIndexFromNode(node: Long): Int = (node shr 28).toInt() and MAX_INDEX
        fun nextIndexFromNode(node: Long): Int = (node.toInt() shr 8) and MAX_INDEX
        fun strengthFromNode(node: Long): Int = node.toInt() and 3
        fun nodeHasBefore2(node: Long): Boolean = node and HAS_BEFORE2.toLong()!=0L
        fun nodeHasBefore3(node: Long): Boolean = node and HAS_BEFORE3.toLong()!=0L
        fun nodeHasAnyBefore(node: Long): Boolean = node and (HAS_BEFORE2 or HAS_BEFORE3).toLong()!=0L
        fun isTailoredNode(node: Long): Boolean = node and IS_TAILORED.toLong()!=0L
        fun changeNodePreviousIndex(node: Long,previous: Int): Long = (node and -281474708275201L) or nodeFromPreviousIndex(previous)
        fun changeNodeNextIndex(node: Long,next: Int): Long = (node and -268435201L) or nodeFromNextIndex(next)
        fun binarySearchForRootPrimaryNode(indexes: IntArray,n: Int,array: LongArray,p: Long): Int {
            if(n==0) return 0.inv();var start=0;var limit=n
            while(true) { val i=((start.toLong()+limit.toLong())/2).toInt();val primary=array[indexes[i]] ushr 32
                if(p==primary) return i
                if(p<primary) { if(i==start) return start.inv();limit=i } else { if(i==start) return (start+1).inv();start=i }
            }
        }
        fun countTailoredNodes(array: LongArray,start: Int,strength: Int): Int {
            var i=start;var count=0
            while(i!=0) { val node=array[i];if(strengthFromNode(node)<strength) break;if(strengthFromNode(node)==strength) { if(isTailoredNode(node)) ++count else break };i=nextIndexFromNode(node) };return count
        }
    }
}
