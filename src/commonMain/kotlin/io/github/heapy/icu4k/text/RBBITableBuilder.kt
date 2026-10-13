// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2002-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.RBBIDataWrapper

/** Original position-set DFA construction, optimization and safe reverse table. */
internal class RBBITableBuilder(
    private val fRB: RBBIRuleParseResult,
    private val fSetBuilder: RBBISetBuilder,
    private val numRules: Int,
    private val fRootIx: Int = 0,
) {
    class IntPair(var first: Int = 0, var second: Int = 0)
    class RBBIStateDescriptor(maxInputSymbol: Int) {
        var fMarked = false
        var fAccepting = 0
        var fLookAhead = 0
        val fTagVals = HashSet<Int>()
        var fTagsIdx = 0
        var fPositions: MutableSet<RBBINode> = HashSet()
        var fDtran = IntArray(maxInputSymbol + 1)
    }
    val fDStates = ArrayList<RBBIStateDescriptor>()
    var fSafeTable: MutableList<ShortArray>? = null
    lateinit var fLookAheadRuleMap: IntArray
    var fLASlotsInUse = RBBIDataWrapper.ACCEPTING_UNCONDITIONAL
    private fun debug(key: String): Boolean = fRB.fDebugEnv?.contains(key) == true
    private fun root(): RBBINode = fRB.fTreeRoots[fRootIx]!!

    fun buildForwardTable() {
        if (fRB.fTreeRoots[fRootIx] == null) return
        fRB.fTreeRoots[fRootIx] = root().flattenVariables(0)
        if (debug("ftree")) { println("Parse tree after flattening variable references."); root().printTree(true) }
        if (fSetBuilder.sawBOF()) {
            val top = RBBINode(RBBINode.opCat)
            val leaf = RBBINode(RBBINode.leafChar)
            top.fLeftChild = leaf; top.fRightChild = root()
            leaf.fParent = top; leaf.fVal = 2
            fRB.fTreeRoots[fRootIx] = top
        }
        val cat = RBBINode(RBBINode.opCat)
        cat.fLeftChild = root(); root().fParent = cat
        val end = RBBINode(RBBINode.endMark)
        cat.fRightChild = end; end.fParent = cat
        fRB.fTreeRoots[fRootIx] = cat
        root().flattenSets()
        if (debug("stree")) { println("Parse tree after flattening Unicode Set references."); root().printTree(true) }
        calcNullable(root()); calcFirstPos(root()); calcLastPos(root()); calcFollowPos(root())
        if (debug("pos")) { print("\n"); printPosSets(root()) }
        if (fRB.fChainRules) calcChainedFollowPos(root(), end)
        if (fSetBuilder.sawBOF()) bofFixup()
        buildStateTable(); mapLookAheadRules(); flagAcceptingStates(); flagLookAheadStates(); flagTaggedStates(); mergeRuleStatusVals()
    }

    fun calcNullable(n: RBBINode?) {
        if (n == null) return
        if (n.fType == RBBINode.setRef || n.fType == RBBINode.endMark) { n.fNullable = false; return }
        if (n.fType == RBBINode.lookAhead || n.fType == RBBINode.tag) { n.fNullable = true; return }
        calcNullable(n.fLeftChild); calcNullable(n.fRightChild)
        n.fNullable = when (n.fType) {
            RBBINode.opOr -> n.fLeftChild!!.fNullable || n.fRightChild!!.fNullable
            RBBINode.opCat -> n.fLeftChild!!.fNullable && n.fRightChild!!.fNullable
            RBBINode.opStar, RBBINode.opQuestion -> true
            else -> false
        }
    }
    fun calcFirstPos(n: RBBINode?) {
        if (n == null) return
        if (isPosition(n)) { n.fFirstPosSet.add(n); return }
        calcFirstPos(n.fLeftChild); calcFirstPos(n.fRightChild)
        when (n.fType) {
            RBBINode.opOr -> { n.fFirstPosSet.addAll(n.fLeftChild!!.fFirstPosSet); n.fFirstPosSet.addAll(n.fRightChild!!.fFirstPosSet) }
            RBBINode.opCat -> {
                n.fFirstPosSet.addAll(n.fLeftChild!!.fFirstPosSet)
                if (n.fLeftChild!!.fNullable) n.fFirstPosSet.addAll(n.fRightChild!!.fFirstPosSet)
            }
            RBBINode.opStar, RBBINode.opQuestion, RBBINode.opPlus -> n.fFirstPosSet.addAll(n.fLeftChild!!.fFirstPosSet)
        }
    }
    fun calcLastPos(n: RBBINode?) {
        if (n == null) return
        if (isPosition(n)) { n.fLastPosSet.add(n); return }
        calcLastPos(n.fLeftChild); calcLastPos(n.fRightChild)
        when (n.fType) {
            RBBINode.opOr -> { n.fLastPosSet.addAll(n.fLeftChild!!.fLastPosSet); n.fLastPosSet.addAll(n.fRightChild!!.fLastPosSet) }
            RBBINode.opCat -> {
                n.fLastPosSet.addAll(n.fRightChild!!.fLastPosSet)
                if (n.fRightChild!!.fNullable) n.fLastPosSet.addAll(n.fLeftChild!!.fLastPosSet)
            }
            RBBINode.opStar, RBBINode.opQuestion, RBBINode.opPlus -> n.fLastPosSet.addAll(n.fLeftChild!!.fLastPosSet)
        }
    }
    private fun isPosition(n: RBBINode): Boolean = n.fType == RBBINode.leafChar || n.fType == RBBINode.endMark || n.fType == RBBINode.lookAhead || n.fType == RBBINode.tag
    fun calcFollowPos(n: RBBINode?) {
        if (n == null || n.fType == RBBINode.leafChar || n.fType == RBBINode.endMark) return
        calcFollowPos(n.fLeftChild); calcFollowPos(n.fRightChild)
        if (n.fType == RBBINode.opCat) for (i in n.fLeftChild!!.fLastPosSet) i.fFollowPos.addAll(n.fRightChild!!.fFirstPosSet)
        if (n.fType == RBBINode.opStar || n.fType == RBBINode.opPlus) for (i in n.fLastPosSet) i.fFollowPos.addAll(n.fFirstPosSet)
    }
    fun addRuleRootNodes(dest: MutableList<RBBINode>, node: RBBINode?) {
        if (node == null) return
        if (node.fRuleRoot) { dest.add(node); return }
        addRuleRootNodes(dest, node.fLeftChild); addRuleRootNodes(dest, node.fRightChild)
    }
    fun calcChainedFollowPos(tree: RBBINode, endMarkNode: RBBINode) {
        val leaves = ArrayList<RBBINode>(); tree.findNodes(leaves, RBBINode.leafChar)
        val rules = ArrayList<RBBINode>(); addRuleRootNodes(rules, tree)
        val starts = HashSet<RBBINode>()
        for (rule in rules) if (rule.fChainIn) starts.addAll(rule.fFirstPosSet)
        for (end in leaves) {
            if (!end.fFollowPos.contains(endMarkNode)) continue
            for (start in starts) if (start.fType == RBBINode.leafChar && end.fVal == start.fVal) end.fFollowPos.addAll(start.fFollowPos)
        }
    }
    fun bofFixup() {
        val bof = root().fLeftChild!!.fLeftChild!!
        check(bof.fType == RBBINode.leafChar && bof.fVal == 2) { "assert failed" }
        for (start in root().fLeftChild!!.fRightChild!!.fFirstPosSet) {
            if (start.fType == RBBINode.leafChar && start.fVal == bof.fVal) bof.fFollowPos.addAll(start.fFollowPos)
        }
    }
    fun buildStateTable() {
        val last = fSetBuilder.getNumCharCategories() - 1
        fDStates.add(RBBIStateDescriptor(last))
        fDStates.add(RBBIStateDescriptor(last).also { it.fPositions.addAll(root().fFirstPosSet) })
        while (true) {
            var state: RBBIStateDescriptor? = null
            for (i in 1 until fDStates.size) if (!fDStates[i].fMarked) { state = fDStates[i]; break }
            if (state == null) break
            state.fMarked = true
            for (a in 1..last) {
                var positions: MutableSet<RBBINode>? = null
                for (p in state.fPositions) if (p.fType == RBBINode.leafChar && p.fVal == a) {
                    if (positions == null) positions = HashSet()
                    positions.addAll(p.fFollowPos)
                }
                if (positions != null) {
                    check(positions.isNotEmpty()) { "assert failed" }
                    var destination = fDStates.indexOfFirst { it.fPositions == positions }
                    if (destination < 0) {
                        fDStates.add(RBBIStateDescriptor(last).also { it.fPositions = positions })
                        destination = fDStates.lastIndex
                    }
                    state.fDtran[a] = destination
                }
            }
        }
    }
    fun mapLookAheadRules() {
        fLookAheadRuleMap = IntArray(numRules + 1)
        for (state in fDStates) {
            var slot = 0
            var saw = false
            for (node in state.fPositions) if (node.fType == RBBINode.lookAhead) {
                saw = true
                val existing = fLookAheadRuleMap[node.fVal]
                if (existing != 0 && slot == 0) slot = existing
            }
            if (!saw) continue
            if (slot == 0) slot = ++fLASlotsInUse
            for (node in state.fPositions) if (node.fType == RBBINode.lookAhead) fLookAheadRuleMap[node.fVal] = slot
        }
    }
    fun flagAcceptingStates() {
        val ends = ArrayList<RBBINode>(); root().findNodes(ends, RBBINode.endMark)
        for (end in ends) for (state in fDStates) if (state.fPositions.contains(end)) {
            if (state.fAccepting == 0) {
                state.fAccepting = fLookAheadRuleMap[end.fVal]
                if (state.fAccepting == 0) state.fAccepting = RBBIDataWrapper.ACCEPTING_UNCONDITIONAL
            }
            if (state.fAccepting == RBBIDataWrapper.ACCEPTING_UNCONDITIONAL && end.fVal != 0) state.fAccepting = fLookAheadRuleMap[end.fVal]
        }
    }
    fun flagLookAheadStates() {
        val nodes = ArrayList<RBBINode>(); root().findNodes(nodes, RBBINode.lookAhead)
        for (node in nodes) for (state in fDStates) if (state.fPositions.contains(node)) state.fLookAhead = fLookAheadRuleMap[node.fVal]
    }
    fun flagTaggedStates() {
        val tags = ArrayList<RBBINode>(); root().findNodes(tags, RBBINode.tag)
        for (tag in tags) for (state in fDStates) if (state.fPositions.contains(tag)) state.fTagVals.add(tag.fVal)
    }
    fun mergeRuleStatusVals() {
        if (fRB.fRuleStatusVals.isEmpty()) {
            fRB.fRuleStatusVals.add(1); fRB.fRuleStatusVals.add(0)
            fRB.fStatusSets[emptySet()] = 0; fRB.fStatusSets[setOf(0)] = 0
        }
        for (state in fDStates) {
            val values = state.fTagVals
            var index = fRB.fStatusSets[values]
            if (index == null) {
                index = fRB.fRuleStatusVals.size
                fRB.fStatusSets[values] = index
                fRB.fRuleStatusVals.add(values.size); fRB.fRuleStatusVals.addAll(values.sorted())
            }
            state.fTagsIdx = index
        }
    }

    fun findDuplCharClassFrom(categories: IntPair): Boolean {
        val cols = fSetBuilder.getNumCharCategories()
        var base = 0; var duplicate = 0
        while (categories.first < cols - 1) {
            val limit = if (categories.first < fSetBuilder.getDictCategoriesStart()) fSetBuilder.getDictCategoriesStart() else cols
            categories.second = categories.first + 1
            while (categories.second < limit) {
                for (state in fDStates) {
                    base = state.fDtran[categories.first]; duplicate = state.fDtran[categories.second]
                    if (base != duplicate) break
                }
                if (base == duplicate) return true
                categories.second++
            }
            categories.first++
        }
        return false
    }
    fun removeColumn(column: Int) {
        for (state in fDStates) {
            val array = state.fDtran.copyOf(state.fDtran.size - 1)
            state.fDtran.copyInto(array, column, column + 1, state.fDtran.size)
            state.fDtran = array
        }
    }
    private fun equivalent(a: Int, b: Int, pair: IntPair): Boolean = a == b ||
        (a == pair.first || a == pair.second) && (b == pair.first || b == pair.second)
    fun findDuplicateState(states: IntPair): Boolean {
        while (states.first < fDStates.size - 1) {
            val first = fDStates[states.first]
            states.second = states.first + 1
            while (states.second < fDStates.size) {
                val other = fDStates[states.second]
                if (first.fAccepting == other.fAccepting && first.fLookAhead == other.fLookAhead && first.fTagsIdx == other.fTagsIdx &&
                    (0 until fSetBuilder.getNumCharCategories()).all { equivalent(first.fDtran[it], other.fDtran[it], states) }) return true
                states.second++
            }
            states.first++
        }
        return false
    }
    fun findDuplicateSafeState(states: IntPair): Boolean {
        val table = fSafeTable!!
        while (states.first < table.size - 1) {
            val first = table[states.first]
            states.second = states.first + 1
            while (states.second < table.size) {
                val other = table[states.second]
                if (first.indices.all { equivalent(first[it].toInt(), other[it].toInt(), states) }) return true
                states.second++
            }
            states.first++
        }
        return false
    }
    fun removeState(pair: IntPair) {
        fDStates.removeAt(pair.second)
        for (state in fDStates) for (col in 0 until fSetBuilder.getNumCharCategories()) {
            val old = state.fDtran[col]
            if (old == pair.second) state.fDtran[col] = pair.first else if (old > pair.second) state.fDtran[col]--
        }
    }
    fun removeSafeState(pair: IntPair) {
        val table = fSafeTable!!
        table.removeAt(pair.second)
        for (row in table) for (col in row.indices) {
            val old = row[col].toInt()
            row[col] = (if (old == pair.second) pair.first else if (old > pair.second) old - 1 else old).toShort()
        }
    }
    fun removeDuplicateStates(): Int {
        val pair = IntPair(3, 0)
        var count = 0
        while (findDuplicateState(pair)) { removeState(pair); count++ }
        return count
    }
    private fun tableSize(rows: Int, cols: Int): Int =
        (RBBIDataWrapper.RBBIStateTable.fHeaderSize + rows * (if (rows <= 255) 1 else 2) * (RBBIDataWrapper.NEXTSTATES + cols) + 7) and -8
    fun getTableSize(): Int = if (fRB.fTreeRoots[fRootIx] == null) 0 else tableSize(fDStates.size, fSetBuilder.getNumCharCategories())
    fun exportTable(): RBBIDataWrapper.RBBIStateTable {
        val table = RBBIDataWrapper.RBBIStateTable()
        if (fRB.fTreeRoots[fRootIx] == null) return table
        check(fSetBuilder.getNumCharCategories() < 0x7fff && fDStates.size < 0x7fff) { "assert failed" }
        table.fNumStates = fDStates.size
        table.fDictCategoriesStart = fSetBuilder.getDictCategoriesStart()
        table.fLookAheadResultsSize = if (fLASlotsInUse == RBBIDataWrapper.ACCEPTING_UNCONDITIONAL) 0 else fLASlotsInUse + 1
        val bytes = table.fNumStates <= 255
        val rowLen = RBBIDataWrapper.NEXTSTATES + fSetBuilder.getNumCharCategories()
        table.fRowLen = rowLen * if (bytes) 1 else 2
        table.fTable = CharArray((getTableSize() - RBBIDataWrapper.RBBIStateTable.fHeaderSize) / if (bytes) 1 else 2)
        if (fRB.fLookAheadHardBreak) table.fFlags = table.fFlags or RBBIDataWrapper.RBBI_LOOKAHEAD_HARD_BREAK
        if (fSetBuilder.sawBOF()) table.fFlags = table.fFlags or RBBIDataWrapper.RBBI_BOF_REQUIRED
        if (bytes) table.fFlags = table.fFlags or RBBIDataWrapper.RBBI_8BITS_ROWS
        for ((index, state) in fDStates.withIndex()) {
            val max = if (bytes) 255 else 0xffff
            check(state.fAccepting in 0..max && state.fLookAhead in 0..max) { "assert failed" }
            val row = index * rowLen
            table.fTable[row + RBBIDataWrapper.ACCEPTING] = state.fAccepting.toChar()
            table.fTable[row + RBBIDataWrapper.LOOKAHEAD] = state.fLookAhead.toChar()
            table.fTable[row + RBBIDataWrapper.TAGSIDX] = state.fTagsIdx.toChar()
            for (col in state.fDtran.indices) {
                if (bytes) check(state.fDtran[col] in 0..255) { "assert failed" }
                table.fTable[row + RBBIDataWrapper.NEXTSTATES + col] = state.fDtran[col].toChar()
            }
        }
        return table
    }
    fun buildSafeReverseTable() {
        val pairs = StringBuilder()
        val cols = fSetBuilder.getNumCharCategories()
        for (c1 in 0 until cols) for (c2 in 0 until cols) {
            var wanted = -1; var end = 0
            for (start in 1 until fDStates.size) {
                end = fDStates[fDStates[start].fDtran[c1]].fDtran[c2]
                if (wanted < 0) wanted = end else if (wanted != end) break
            }
            if (wanted == end) { pairs.append(c1.toChar()); pairs.append(c2.toChar()) }
        }
        val table = ArrayList<ShortArray>()
        fSafeTable = table
        repeat(cols + 2) { table.add(ShortArray(cols)) }
        for (c in 0 until cols) table[1][c] = (c + 2).toShort()
        for (row in 2 until cols + 2) table[1].copyInto(table[row])
        for (i in pairs.indices step 2) table[pairs[i + 1].code + 2][pairs[i].code] = 0
        val states = IntPair(1, 0)
        while (findDuplicateSafeState(states)) removeSafeState(states)
    }
    fun getSafeTableSize(): Int = fSafeTable?.let { tableSize(it.size, it[0].size) } ?: 0
    fun exportSafeTable(): RBBIDataWrapper.RBBIStateTable {
        val safe = fSafeTable!!
        val table = RBBIDataWrapper.RBBIStateTable()
        table.fNumStates = safe.size
        val bytes = safe.size <= 255
        val cols = safe[0].size
        val rowLen = RBBIDataWrapper.NEXTSTATES + cols
        table.fRowLen = rowLen * if (bytes) 1 else 2
        table.fTable = CharArray((getSafeTableSize() - RBBIDataWrapper.RBBIStateTable.fHeaderSize) / if (bytes) 1 else 2)
        if (bytes) table.fFlags = table.fFlags or RBBIDataWrapper.RBBI_8BITS_ROWS
        for ((state, row) in safe.withIndex()) for (col in 0 until cols) {
            if (bytes) check(row[col] <= 255) { "assert failed" }
            table.fTable[state * rowLen + RBBIDataWrapper.NEXTSTATES + col] = row[col].toInt().toChar()
        }
        return table
    }
    fun printSet(set: Collection<RBBINode>) { for (node in set) RBBINode.printInt(node.fSerialNum, 8); println() }
    fun printPosSets(node: RBBINode?) {
        if (node == null) return
        RBBINode.printNode(node)
        print("         Nullable:  ${node.fNullable}")
        print("         firstpos:  "); printSet(node.fFirstPosSet)
        print("         lastpos:   "); printSet(node.fLastPosSet)
        print("         followpos: "); printSet(node.fFollowPos)
        printPosSets(node.fLeftChild); printPosSets(node.fRightChild)
    }
    fun printStates() {
        val cols = fSetBuilder.getNumCharCategories()
        print("state |           i n p u t     s y m b o l s \n      | Acc  LA    Tag")
        for (c in 0 until cols) RBBINode.printInt(c, 4)
        print("\n      |---------------"); repeat(cols) { print("----") }; print("\n")
        for ((index, state) in fDStates.withIndex()) {
            RBBINode.printInt(index, 5); print(" | ")
            RBBINode.printInt(state.fAccepting, 3); RBBINode.printInt(state.fLookAhead, 4); RBBINode.printInt(state.fTagsIdx, 6); print(" ")
            for (c in 0 until cols) RBBINode.printInt(state.fDtran[c], 4)
            print("\n")
        }
        print("\n\n")
    }
    fun printReverseTable() {
        print("    Safe Reverse Table \n")
        val safe = fSafeTable ?: run { print("   --- nullptr ---\n"); return }
        val cols = safe[0].size
        print("state |           i n p u t     s y m b o l s \n      | Acc  LA    Tag")
        for (c in 0 until cols) print(" " + c.toString().padStart(2))
        print("\n      |---------------"); repeat(cols) { print("---") }; print("\n")
        for ((index, row) in safe.withIndex()) {
            print("  " + index.toString().padStart(3) + " | ")
            print("  0   0     0 ")
            for (c in 0 until cols) print(" " + row[c].toString().padStart(2))
            print("\n")
        }
        print("\n\n")
    }
    fun printRuleStatusTable() {
        var next = 0
        print("index |  tags \n-------------------\n")
        while (next < fRB.fRuleStatusVals.size) {
            val start = next
            next = start + fRB.fRuleStatusVals[start] + 1
            RBBINode.printInt(start, 7)
            for (i in start + 1 until next) RBBINode.printInt(fRB.fRuleStatusVals[i], 7)
            print("\n")
        }
        print("\n\n")
    }
}
