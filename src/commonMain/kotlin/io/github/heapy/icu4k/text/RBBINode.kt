// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2001-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package io.github.heapy.icu4k.text

import kotlin.concurrent.atomics.AtomicInt

/** Syntax-tree node and original variable/set expansion for the RBBI compiler. */
internal class RBBINode {
    var fType: Int
    var fParent: RBBINode? = null
    var fLeftChild: RBBINode? = null
    var fRightChild: RBBINode? = null
    var fInputSet: UnicodeSet? = null
    var fPrecedence = precZero
    var fText: String? = null
    var fFirstPos = 0
    var fLastPos = 0
    var fNullable = false
    var fVal = 0
    var fLookAheadEnd = false
    var fRuleRoot = false
    var fChainIn = false
    val fFirstPosSet: MutableSet<RBBINode>
    val fLastPosSet: MutableSet<RBBINode>
    val fFollowPos: MutableSet<RBBINode>
    var fSerialNum: Int
    constructor(type: Int) {
        check(type < nodeTypeLimit) { "assert failed" }
        fSerialNum = lastSerial.fetchAndAdd(1) + 1
        fType = type
        fFirstPosSet = HashSet(); fLastPosSet = HashSet(); fFollowPos = HashSet()
        fPrecedence = when (type) { opCat -> precOpCat; opOr -> precOpOr; opStart -> precStart; opLParen -> precLParen; else -> precZero }
    }
    constructor(other: RBBINode) {
        fSerialNum = lastSerial.fetchAndAdd(1) + 1
        fType = other.fType; fInputSet = other.fInputSet; fPrecedence = other.fPrecedence
        fText = other.fText; fFirstPos = other.fFirstPos; fLastPos = other.fLastPos
        fNullable = other.fNullable; fVal = other.fVal; fChainIn = other.fChainIn
        // Rule-root and lookahead-end flags are intentionally not copied by ICU4J.
        fFirstPosSet = HashSet(other.fFirstPosSet)
        fLastPosSet = HashSet(other.fLastPosSet)
        fFollowPos = HashSet(other.fFollowPos)
    }
    fun cloneTree(): RBBINode {
        if (fType == varRef) return fLeftChild!!.cloneTree()
        if (fType == uset) return this
        val node = RBBINode(this)
        fLeftChild?.let { node.fLeftChild = it.cloneTree().also { child -> child.fParent = node } }
        fRightChild?.let { node.fRightChild = it.cloneTree().also { child -> child.fParent = node } }
        return node
    }
    fun flattenVariables(depth: Int): RBBINode {
        if (depth > 3500) throw IllegalArgumentException("The input is too long")
        if (fType == varRef) return fLeftChild!!.cloneTree().also { it.fRuleRoot = fRuleRoot; it.fChainIn = fChainIn }
        fLeftChild?.let { fLeftChild = it.flattenVariables(depth + 1).also { child -> child.fParent = this } }
        fRightChild?.let { fRightChild = it.flattenVariables(depth + 1).also { child -> child.fParent = this } }
        return this
    }
    fun flattenSets() {
        check(fType != setRef) { "assert failed" }
        fLeftChild?.let { child ->
            if (child.fType == setRef) fLeftChild = child.fLeftChild!!.fLeftChild!!.cloneTree().also { it.fParent = this }
            else child.flattenSets()
        }
        fRightChild?.let { child ->
            if (child.fType == setRef) fRightChild = child.fLeftChild!!.fLeftChild!!.cloneTree().also { it.fParent = this }
            else child.flattenSets()
        }
    }
    fun findNodes(dest: MutableList<RBBINode>, kind: Int) {
        if (fType == kind) dest.add(this)
        fLeftChild?.findNodes(dest, kind); fRightChild?.findNodes(dest, kind)
    }
    fun printTree(printHeading: Boolean) {
        if (printHeading) {
            println("-------------------------------------------------------------------")
            println("    Serial       type     Parent  LeftChild  RightChild    position  value")
        }
        printNode(this)
        if (fType != varRef) { fLeftChild?.printTree(false); fRightChild?.printTree(false) }
    }
    companion object {
        const val setRef = 0
        const val uset = 1
        const val varRef = 2
        const val leafChar = 3
        const val lookAhead = 4
        const val tag = 5
        const val endMark = 6
        const val opStart = 7
        const val opCat = 8
        const val opOr = 9
        const val opStar = 10
        const val opPlus = 11
        const val opQuestion = 12
        const val opBreak = 13
        const val opReverse = 14
        const val opLParen = 15
        const val nodeTypeLimit = 16
        const val precZero = 0
        const val precStart = 1
        const val precLParen = 2
        const val precOpOr = 3
        const val precOpCat = 4
        val nodeTypeNames = arrayOf("setRef", "uset", "varRef", "leafChar", "lookAhead", "tag", "endMark", "opStart",
            "opCat", "opOr", "opStar", "opPlus", "opQuestion", "opBreak", "opReverse", "opLParen")
        private val lastSerial = AtomicInt(0)
        fun printString(s: String, minWidth: Int) {
            for (i in minWidth until 0) print(' ')
            for (i in s.length until minWidth) print(' ')
            print(s)
        }
        fun printInt(i: Int, minWidth: Int) {
            val s = i.toString(); printString(s, maxOf(minWidth, s.length + 1))
        }
        fun printHex(i: Int, minWidth: Int) {
            val s = i.toString(16); printString("00000".substring(0, maxOf(0, 5 - s.length)) + s, minWidth)
        }
        fun printNode(node: RBBINode?) {
            if (node == null) print(" -- null --\n")
            else {
                printInt(node.fSerialNum, 10); printString(nodeTypeNames[node.fType], 11)
                printInt(node.fParent?.fSerialNum ?: 0, 11); printInt(node.fLeftChild?.fSerialNum ?: 0, 11)
                printInt(node.fRightChild?.fSerialNum ?: 0, 12); printInt(node.fFirstPos, 12); printInt(node.fVal, 7)
                if (node.fType == varRef) print(" " + node.fText)
            }
            println("")
        }
    }
}
