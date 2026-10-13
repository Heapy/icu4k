// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2011, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.CodePointTrie
import io.github.heapy.icu4k.util.MutableCodePointTrie

/** Partitions the scanner's sets by membership and assigns DFA input categories. */
internal class RBBISetBuilder(val fRB: RBBIRuleParseResult) {
    class RangeDescriptor() {
        var fStartChar = 0
        var fEndChar = 0
        var fNum = 0
        var fIncludesDict = false
        var fFirstInGroup = false
        val fIncludesSets = ArrayList<RBBINode>()
        var fNext: RangeDescriptor? = null

        constructor(other: RangeDescriptor) : this() {
            fStartChar = other.fStartChar
            fEndChar = other.fEndChar
            fNum = other.fNum
            fIncludesDict = other.fIncludesDict
            fFirstInGroup = other.fFirstInGroup
            fIncludesSets.addAll(other.fIncludesSets)
        }

        fun split(where: Int) {
            check(where > fStartChar && where <= fEndChar) { "assert failed" }
            val next = RangeDescriptor(this)
            next.fStartChar = where
            fEndChar = where - 1
            next.fNext = fNext
            fNext = next
        }

        fun isDictionaryRange(): Boolean = fIncludesSets.any { setName(it, "") == "dictionary" }
    }

    var fRangeList: RangeDescriptor? = null
    var fTrie: MutableCodePointTrie? = null
    var fFrozenTrie: CodePointTrie? = null
    var fGroupCount = 0
    var fDictCategoriesStart = 0
    var fSawBOF = false

    fun buildRanges() {
        if (debug("usets")) printSets()
        fRangeList = RangeDescriptor().also { it.fEndChar = 0x10ffff }
        for (uset in fRB.fUSetNodes) {
            val input = uset.fInputSet!!
            var inputIndex = 0
            var range = fRangeList
            while (inputIndex < input.getRangeCount()) {
                val start = input.getRangeStart(inputIndex)
                val end = input.getRangeEnd(inputIndex)
                while (range!!.fEndChar < start) range = range.fNext
                val current = range
                if (current.fStartChar < start) {
                    current.split(start)
                    continue
                }
                if (current.fEndChar > end) current.split(end + 1)
                if (!current.fIncludesSets.contains(uset)) current.fIncludesSets.add(uset)
                if (end == current.fEndChar) inputIndex++
                range = current.fNext
            }
        }
        if (debug("range")) printRanges()
        var dictGroupCount = 0
        var range = fRangeList
        while (range != null) {
            var search = fRangeList
            while (search !== range) {
                if (range.fIncludesSets == search!!.fIncludesSets) {
                    range.fNum = search.fNum
                    range.fIncludesDict = search.fIncludesDict
                    break
                }
                search = search.fNext
            }
            if (range.fNum == 0) {
                range.fFirstInGroup = true
                if (range.isDictionaryRange()) {
                    range.fNum = ++dictGroupCount
                    range.fIncludesDict = true
                } else {
                    fGroupCount++
                    range.fNum = fGroupCount + 2
                    addValToSets(range.fIncludesSets, range.fNum)
                }
            }
            range = range.fNext
        }
        fDictCategoriesStart = fGroupCount + 3
        range = fRangeList
        while (range != null) {
            if (range.fIncludesDict) {
                range.fNum += fDictCategoriesStart - 1
                if (range.fFirstInGroup) addValToSets(range.fIncludesSets, range.fNum)
            }
            range = range.fNext
        }
        fGroupCount += dictGroupCount
        for (uset in fRB.fUSetNodes) {
            val input = uset.fInputSet!!
            if (input.contains("eof")) addValToSet(uset, 1)
            if (input.contains("bof")) {
                addValToSet(uset, 2)
                fSawBOF = true
            }
        }
        if (debug("rgroup")) printRangeGroups()
        if (debug("esets")) printSets()
    }

    fun buildTrie() {
        val trie = MutableCodePointTrie(0, 0)
        fTrie = trie
        var range = fRangeList
        while (range != null) {
            trie.setRange(range.fStartChar, range.fEndChar, range.fNum)
            range = range.fNext
        }
    }

    /** Called after equivalent DFA columns are removed, before building the trie. */
    fun mergeCategories(first: Int, second: Int) {
        var range = fRangeList
        while (range != null) {
            val number = range.fNum
            if (number == second) range.fNum = first else if (number > second) range.fNum--
            range = range.fNext
        }
        fGroupCount--
        if (second <= fDictCategoriesStart) fDictCategoriesStart--
    }

    fun freezeTrieIfNotYet() {
        if (fFrozenTrie == null) {
            val width = if (getNumCharCategories() <= 255) CodePointTrie.ValueWidth.BITS_8 else CodePointTrie.ValueWidth.BITS_16
            fFrozenTrie = fTrie!!.buildImmutable(CodePointTrie.Type.FAST, width)
            fTrie = null
        }
    }

    fun getTrieSize(): Int {
        freezeTrieIfNotYet()
        return fFrozenTrie!!.toBinary().size
    }

    fun serializeTrie(): ByteArray {
        freezeTrieIfNotYet()
        return fFrozenTrie!!.toBinary()
    }

    fun addValToSets(sets: List<RBBINode>, value: Int) {
        for (uset in sets) addValToSet(uset, value)
    }

    fun addValToSet(uset: RBBINode, value: Int) {
        val leaf = RBBINode(RBBINode.leafChar).also { it.fVal = value }
        val previous = uset.fLeftChild
        if (previous == null) {
            uset.fLeftChild = leaf
            leaf.fParent = uset
        } else {
            val or = RBBINode(RBBINode.opOr)
            or.fLeftChild = previous
            or.fRightChild = leaf
            previous.fParent = or
            leaf.fParent = or
            uset.fLeftChild = or
            or.fParent = uset
        }
    }

    fun getNumCharCategories(): Int = fGroupCount + 3
    fun getDictCategoriesStart(): Int = fDictCategoriesStart
    fun sawBOF(): Boolean = fSawBOF
    fun getFirstChar(category: Int): Int {
        var range = fRangeList
        while (range != null) {
            if (range.fNum == category) return range.fStartChar
            range = range.fNext
        }
        return -1
    }

    private fun debug(key: String): Boolean = fRB.fDebugEnv?.contains(key) == true

    fun printRanges() {
        print("\n\n Nonoverlapping Ranges ...\n")
        var range = fRangeList
        while (range != null) {
            print(range.fStartChar.toString(16).padStart(4, '0') + "-" + range.fEndChar.toString(16).padStart(4, '0') + " ")
            for (uset in range.fIncludesSets) print("${setName(uset, "anon")}  ")
            println()
            range = range.fNext
        }
    }

    fun printRangeGroups() {
        print("\nRanges grouped by Unicode Set Membership...\n")
        var range = fRangeList
        while (range != null) {
            if (range.fFirstInGroup) {
                val group = range.fNum
                if (group < 10) print(" ")
                print("$group ")
                if (group >= fDictCategoriesStart) print(" <DICT> ")
                for (uset in range.fIncludesSets) print("${setName(uset, "anon")} ")
                var i = 0
                var next = range
                while (next != null) {
                    if (next.fNum == range.fNum) {
                        if (i++ % 5 == 0) print("\n    ")
                        RBBINode.printHex(next.fStartChar, -1)
                        print("-")
                        RBBINode.printHex(next.fEndChar, 0)
                    }
                    next = next.fNext
                }
                print("\n")
            }
            range = range.fNext
        }
        print("\n")
    }

    fun printSets() {
        print("\n\nUnicode Sets List\n------------------\n")
        for ((i, uset) in fRB.fUSetNodes.withIndex()) {
            RBBINode.printInt(2, i)
            print("  ${setName(uset, "anonymous")}   ${uset.fText}\n")
            uset.fLeftChild?.printTree(true)
        }
        print("\n")
    }

    companion object {
        private fun setName(uset: RBBINode, fallback: String): String? {
            val variable = uset.fParent?.fParent
            return if (variable != null && variable.fType == RBBINode.varRef) variable.fText else fallback
        }
    }
}
