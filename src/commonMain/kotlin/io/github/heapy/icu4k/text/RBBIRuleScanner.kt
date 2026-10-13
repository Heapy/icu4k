// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.Utility
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_ILLEGAL_CHAR_FOUND
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_INTERNAL_ERROR
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_HEX_DIGITS_EXPECTED
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_RULE_SYNTAX
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_ASSIGN_ERROR
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_MISMATCHED_PAREN
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_NEW_LINE_IN_QUOTED_STRING
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_UNDEFINED_VARIABLE
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_RULE_EMPTY_SET
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_UNRECOGNIZED_OPTION
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_MALFORMED_RULE_TAG
import io.github.heapy.icu4k.text.RBBIRuleCompilerError.U_BRK_MALFORMED_SET
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doCheckVarDef
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doDotAny
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doEndAssign
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doEndOfRule
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doEndVariableName
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doExit
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doExprCatOperator
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doExprFinished
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doExprOrOperator
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doExprRParen
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doExprStart
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doLParen
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doNOP
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doNoChain
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doOptionEnd
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doOptionStart
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doReverseDir
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doRuleChar
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doRuleError
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doRuleErrorAssignExpr
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doScanUnicodeSet
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doSlash
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doStartAssign
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doStartTagValue
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doStartVariableName
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doTagDigit
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doTagExpectedError
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doTagValue
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doUnaryOpPlus
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doUnaryOpQuestion
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doUnaryOpStar
import io.github.heapy.icu4k.text.RBBIRuleParseTable.doVariableNameExpectedErr
import io.github.heapy.icu4k.text.RBBIRuleParseTable.kRuleSet_digit_char
import io.github.heapy.icu4k.text.RBBIRuleParseTable.kRuleSet_name_char
import io.github.heapy.icu4k.text.RBBIRuleParseTable.kRuleSet_name_start_char
import io.github.heapy.icu4k.text.RBBIRuleParseTable.kRuleSet_rule_char
import io.github.heapy.icu4k.text.RBBIRuleParseTable.kRuleSet_white_space
import io.github.heapy.icu4k.text.RBBIRuleParseTable.gRuleParseStateTable

/** Original table-driven rule compiler scanner and expression-tree construction. */
internal class RBBIRuleScanner(val fRB: RBBIRuleParseResult) {
    class RBBIRuleChar(var fChar: Int = 0, var fEscaped: Boolean = false)
    var fScanIndex = 0
    var fNextIndex = 0
    var fQuoteMode = false
    var fLineNum = 1
    var fCharNum = 0
    var fLastChar = 0
    val fC = RBBIRuleChar()
    val fStack = IntArray(kStackSize)
    var fStackPtr = 0
    val fNodeStack = arrayOfNulls<RBBINode>(kStackSize)
    var fNodeStackPtr = 0
    var fReverseRule = false
    var fLookAheadRule = false
    var fNoChainInRule = false
    val fSymbolTable = RBBISymbolTable(::error)
    val fSetTable = HashMap<String, RBBINode>()
    val fRuleSets = arrayOfNulls<UnicodeSet>(10)
    var fRuleNum = 0
    var fOptionStart = 0

    init {
        fRuleSets[kRuleSet_rule_char - 128] = UnicodeSet(gRuleSet_rule_char_pattern)
        fRuleSets[kRuleSet_white_space - 128] = UnicodeSet("[\\p{Pattern_White_Space}]")
        fRuleSets[kRuleSet_name_char - 128] = UnicodeSet("[_\\p{L}\\p{N}]")
        fRuleSets[kRuleSet_name_start_char - 128] = UnicodeSet("[_\\p{L}]")
        fRuleSets[kRuleSet_digit_char - 128] = UnicodeSet("[0-9]")
    }

    fun doParseActions(action: Int): Boolean {
        when (action) {
            doExprStart -> { pushNewNode(RBBINode.opStart); fRuleNum++ }
            doNoChain -> fNoChainInRule = true
            doExprOrOperator, doExprCatOperator -> {
                fixOpStack(RBBINode.precOpCat)
                val operand = fNodeStack[fNodeStackPtr--]!!
                val op = pushNewNode(if (action == doExprOrOperator) RBBINode.opOr else RBBINode.opCat)
                op.fLeftChild = operand
                operand.fParent = op
            }
            doLParen -> pushNewNode(RBBINode.opLParen)
            doExprRParen -> fixOpStack(RBBINode.precLParen)
            doNOP, doExprFinished -> Unit
            doStartAssign -> {
                fNodeStack[fNodeStackPtr - 1]!!.fFirstPos = fNextIndex
                pushNewNode(RBBINode.opStart)
            }
            doEndAssign -> {
                fixOpStack(RBBINode.precStart)
                val start = fNodeStack[fNodeStackPtr - 2]!!
                val variable = fNodeStack[fNodeStackPtr - 1]!!
                val rhs = fNodeStack[fNodeStackPtr]!!
                rhs.fFirstPos = start.fFirstPos
                rhs.fLastPos = fScanIndex
                rhs.fText = fRB.fRules.substring(rhs.fFirstPos, rhs.fLastPos)
                variable.fLeftChild = rhs
                rhs.fParent = variable
                fSymbolTable.addEntry(variable.fText!!, variable)
                fNodeStackPtr -= 3
            }
            doEndOfRule -> {
                fixOpStack(RBBINode.precStart)
                if (debug("rtree")) printNodeStack("end of rule")
                check(fNodeStackPtr == 1) { "assert failed" }
                var rule = fNodeStack[fNodeStackPtr]!!
                if (fLookAheadRule) {
                    val end = pushNewNode(RBBINode.endMark)
                    val cat = pushNewNode(RBBINode.opCat)
                    fNodeStackPtr -= 2
                    cat.fLeftChild = rule
                    cat.fRightChild = end
                    fNodeStack[fNodeStackPtr] = cat
                    end.fVal = fRuleNum
                    end.fLookAheadEnd = true
                    rule = cat
                }
                rule.fRuleRoot = true
                if (fRB.fChainRules && !fNoChainInRule) rule.fChainIn = true
                val dest = if (fReverseRule) 3 else fRB.fDefaultTree
                val previous = fRB.fTreeRoots[dest]
                if (previous != null) {
                    rule = fNodeStack[fNodeStackPtr]!!
                    val or = pushNewNode(RBBINode.opOr)
                    or.fLeftChild = previous
                    previous.fParent = or
                    or.fRightChild = rule
                    rule.fParent = or
                    fRB.fTreeRoots[dest] = or
                } else {
                    fRB.fTreeRoots[dest] = fNodeStack[fNodeStackPtr]
                }
                fReverseRule = false
                fLookAheadRule = false
                fNoChainInRule = false
                fNodeStackPtr = 0
            }
            doRuleError, doVariableNameExpectedErr -> error(U_BRK_RULE_SYNTAX)
            doUnaryOpPlus, doUnaryOpQuestion, doUnaryOpStar -> {
                val operand = fNodeStack[fNodeStackPtr--]!!
                val type = when (action) {
                    doUnaryOpPlus -> RBBINode.opPlus
                    doUnaryOpQuestion -> RBBINode.opQuestion
                    else -> RBBINode.opStar
                }
                val op = pushNewNode(type)
                op.fLeftChild = operand
                operand.fParent = op
            }
            doRuleChar, doDotAny -> {
                val node = pushNewNode(RBBINode.setRef)
                // Original scanner narrows literal rule characters to one UTF-16 unit.
                val key = if (action == doDotAny) "any" else fC.fChar.toChar().toString()
                findSetFor(key, node, null)
                setSource(node)
            }
            doSlash -> {
                val node = pushNewNode(RBBINode.lookAhead)
                node.fVal = fRuleNum
                setSource(node)
                fLookAheadRule = true
            }
            doStartTagValue -> {
                val node = pushNewNode(RBBINode.tag)
                node.fVal = 0
                node.fFirstPos = fScanIndex
                node.fLastPos = fNextIndex
            }
            doTagDigit -> {
                val node = fNodeStack[fNodeStackPtr]!!
                val update = node.fVal.toLong() * 10 + UCharacter.digit(fC.fChar.toChar().code, 10)
                if (update > Int.MAX_VALUE) error(U_BRK_RULE_SYNTAX)
                node.fVal = update.toInt()
            }
            doTagValue -> {
                val node = fNodeStack[fNodeStackPtr]!!
                node.fLastPos = fNextIndex
                node.fText = fRB.fRules.substring(node.fFirstPos, node.fLastPos)
            }
            doTagExpectedError -> error(U_BRK_MALFORMED_RULE_TAG)
            doOptionStart -> fOptionStart = fScanIndex
            doOptionEnd -> when (fRB.fRules.substring(fOptionStart, fScanIndex)) {
                "chain" -> fRB.fChainRules = true
                "forward" -> fRB.fDefaultTree = 0
                "reverse" -> fRB.fDefaultTree = 1
                "safe_forward" -> fRB.fDefaultTree = 2
                "safe_reverse" -> fRB.fDefaultTree = 3
                "lookAheadHardBreak" -> fRB.fLookAheadHardBreak = true
                "quoted_literals_only" -> fRuleSets[kRuleSet_rule_char - 128]!!.clear()
                "unquoted_literals" -> fRuleSets[kRuleSet_rule_char - 128]!!.applyPattern(gRuleSet_rule_char_pattern)
                else -> error(U_BRK_UNRECOGNIZED_OPTION)
            }
            doReverseDir -> fReverseRule = true
            doStartVariableName -> pushNewNode(RBBINode.varRef).fFirstPos = fScanIndex
            doEndVariableName -> {
                val node = fNodeStack[fNodeStackPtr]
                if (node == null || node.fType != RBBINode.varRef) error(U_BRK_INTERNAL_ERROR)
                node.fLastPos = fScanIndex
                node.fText = fRB.fRules.substring(node.fFirstPos + 1, node.fLastPos)
                node.fLeftChild = fSymbolTable.lookupNode(node.fText!!)
            }
            doCheckVarDef -> if (fNodeStack[fNodeStackPtr]!!.fLeftChild == null) error(U_BRK_UNDEFINED_VARIABLE)
            doRuleErrorAssignExpr -> error(U_BRK_ASSIGN_ERROR)
            doExit -> return false
            doScanUnicodeSet -> scanSet()
            else -> error(U_BRK_INTERNAL_ERROR)
        }
        return true
    }

    private fun setSource(node: RBBINode) {
        node.fFirstPos = fScanIndex
        node.fLastPos = fNextIndex
        node.fText = fRB.fRules.substring(node.fFirstPos, node.fLastPos)
    }

    fun error(e: Int): Nothing = throw IllegalArgumentException("Error $e at line $fLineNum column $fCharNum")

    fun fixOpStack(p: Int) {
        var node: RBBINode
        while (true) {
            node = fNodeStack[fNodeStackPtr - 1]!!
            if (node.fPrecedence == 0) {
                print("RBBIRuleScanner.fixOpStack, bad operator node")
                error(U_BRK_INTERNAL_ERROR)
            }
            if (node.fPrecedence < p || node.fPrecedence <= RBBINode.precLParen) break
            node.fRightChild = fNodeStack[fNodeStackPtr]
            fNodeStack[fNodeStackPtr]!!.fParent = node
            fNodeStackPtr--
        }
        if (p <= RBBINode.precLParen) {
            if (node.fPrecedence != p) error(U_BRK_MISMATCHED_PAREN)
            fNodeStack[fNodeStackPtr - 1] = fNodeStack[fNodeStackPtr]
            fNodeStackPtr--
        }
    }

    fun findSetFor(s: String, node: RBBINode, setToAdopt: UnicodeSet?) {
        val existing = fSetTable[s]
        if (existing != null) {
            node.fLeftChild = existing
            check(existing.fType == RBBINode.uset) { "assert failed" }
            return
        }
        val set = setToAdopt ?: if (s == "any") UnicodeSet(0, 0x10ffff) else {
            val c = UTF16.charAt(s, 0)
            UnicodeSet(c, c)
        }
        val uset = RBBINode(RBBINode.uset)
        uset.fInputSet = set
        uset.fParent = node
        node.fLeftChild = uset
        uset.fText = s
        fRB.fUSetNodes.add(uset)
        fSetTable[s] = uset
    }

    fun nextCharLL(): Int {
        if (fNextIndex >= fRB.fRules.length) return -1
        val ch = UTF16.charAt(fRB.fRules, fNextIndex)
        if (ch in 0xd800..0xdfff) error(U_ILLEGAL_CHAR_FOUND)
        fNextIndex = UTF16.moveCodePointOffset(fRB.fRules, fNextIndex, 1)
        if (ch == 13 || ch == chNEL || ch == chLS || ch == 10 && fLastChar != 13) {
            fLineNum++
            fCharNum = 0
            if (fQuoteMode) {
                error(U_BRK_NEW_LINE_IN_QUOTED_STRING)
            }
        } else if (ch != 10) {
            fCharNum++
        }
        fLastChar = ch
        return ch
    }

    fun nextChar(c: RBBIRuleChar) {
        fScanIndex = fNextIndex
        c.fChar = nextCharLL()
        c.fEscaped = false
        if (c.fChar == 39) {
            if (fNextIndex < fRB.fRules.length && UTF16.charAt(fRB.fRules, fNextIndex) == 39) {
                c.fChar = nextCharLL()
                c.fEscaped = true
            } else {
                fQuoteMode = !fQuoteMode
                c.fChar = if (fQuoteMode) 40 else 41
                return
            }
        }
        if (c.fChar == -1) return
        if (fQuoteMode) {
            c.fEscaped = true
        } else {
            if (c.fChar == 35) {
                val commentStart = fScanIndex
                while (true) {
                    c.fChar = nextCharLL()
                    if (c.fChar == -1 || c.fChar == 13 || c.fChar == 10 || c.fChar == chNEL || c.fChar == chLS) break
                }
                for (i in commentStart until fNextIndex - 1) fRB.fStrippedRules[i] = ' '
            }
            if (c.fChar == -1) return
            if (c.fChar == 92) {
                c.fEscaped = true
                val packed = Utility.unescapeAndLengthAt(fRB.fRules, fNextIndex)
                if (packed < 0) error(U_BRK_HEX_DIGITS_EXPECTED)
                c.fChar = Utility.cpFromCodePointAndLength(packed)
                val length = Utility.lengthFromCodePointAndLength(packed)
                fCharNum += length
                fNextIndex += length
            }
        }
    }

    private fun debug(key: String): Boolean = fRB.fDebugEnv?.contains(key) == true

    fun parse() {
        var state = 1
        nextChar(fC)
        while (state != 0) {
            var tableEl = gRuleParseStateTable[state]
            if (debug("scan")) println("char, line, col = ('${fC.fChar.toChar()}', $fLineNum, $fCharNum    state = ${tableEl.fStateName}")
            var row = state
            while (true) {
                tableEl = gRuleParseStateTable[row++]
                if (debug("scan")) print(".")
                val cls = tableEl.fCharClass
                if (cls < 127 && !fC.fEscaped && cls == fC.fChar || cls == 255 ||
                    cls == 254 && fC.fEscaped || cls == 253 && fC.fEscaped && (fC.fChar == 0x50 || fC.fChar == 0x70) ||
                    cls == 252 && fC.fChar == -1) break
                if (cls in 128 until 240 && !fC.fEscaped && fC.fChar != -1 && fRuleSets[cls - 128]!!.contains(fC.fChar)) break
            }
            if (debug("scan")) println()
            if (!doParseActions(tableEl.fAction)) break
            if (tableEl.fPushState != 0) {
                fStackPtr++
                if (fStackPtr >= kStackSize) {
                    println("RBBIRuleScanner.parse() - state stack overflow.")
                    error(U_BRK_INTERNAL_ERROR)
                }
                fStack[fStackPtr] = tableEl.fPushState
            }
            if (tableEl.fNextChar) nextChar(fC)
            if (tableEl.fNextState != 255) state = tableEl.fNextState else {
                state = fStack[fStackPtr]
                fStackPtr--
                if (fStackPtr < 0) {
                    println("RBBIRuleScanner.parse() - state stack underflow.")
                    error(U_BRK_INTERNAL_ERROR)
                }
            }
        }
        if (fRB.fTreeRoots[0] == null) error(U_BRK_RULE_SYNTAX)
        if (debug("symbols")) fSymbolTable.rbbiSymtablePrint()
        if (debug("ptree")) {
            println("Completed Forward Rules Parse Tree...")
            fRB.fTreeRoots[0]!!.printTree(true)
            println("\nCompleted Reverse Rules Parse Tree...")
            fRB.fTreeRoots[1]!!.printTree(true)
            println("\nCompleted Safe Point Forward Rules Parse Tree...")
            if (fRB.fTreeRoots[2] == null) println("  -- null -- ") else fRB.fTreeRoots[2]!!.printTree(true)
            println("\nCompleted Safe Point Reverse Rules Parse Tree...")
            if (fRB.fTreeRoots[3] == null) println("  -- null -- ") else fRB.fTreeRoots[3]!!.printTree(true)
        }
    }

    fun printNodeStack(title: String) {
        println("$title.  Dumping node stack...\n")
        for (i in fNodeStackPtr downTo 1) fNodeStack[i]!!.printTree(true)
    }

    fun pushNewNode(nodeType: Int): RBBINode {
        fNodeStackPtr++
        if (fNodeStackPtr >= kStackSize) {
            println("RBBIRuleScanner.pushNewNode - stack overflow.")
            error(U_BRK_INTERNAL_ERROR)
        }
        return RBBINode(nodeType).also { fNodeStack[fNodeStackPtr] = it }
    }

    fun scanSet() {
        val start = fScanIndex
        val pos = ParsePosition(start)
        val set = try { UnicodeSet(fRB.fRules, pos, fSymbolTable, UnicodeSet.IGNORE_SPACE) }
            catch (_: Exception) { error(U_BRK_MALFORMED_SET) }
        if (UnicodeSet(set).removeAllStrings().isEmpty()) error(U_BRK_RULE_EMPTY_SET)
        while (fNextIndex < pos.index) nextCharLL()
        val node = pushNewNode(RBBINode.setRef)
        node.fFirstPos = start
        node.fLastPos = fNextIndex
        node.fText = fRB.fRules.substring(start, fNextIndex)
        findSetFor(node.fText!!, node, set)
    }

    fun numRules(): Int = fRuleNum

    companion object {
        private const val kStackSize = 100
        const val chNEL = 0x85
        const val chLS = 0x2028
        private const val gRuleSet_rule_char_pattern = "[^[\\p{Z}\\u0020-\\u007f]-[\\p{L}]-[\\p{N}]]"
        fun stripRules(rules: String): String {
            val result = StringBuilder()
            var i = 0
            while (i < rules.length) {
                val cp = UTF16.charAt(rules, i)
                if (!UCharacter.hasBinaryProperty(cp, UProperty.PATTERN_WHITE_SPACE)) UTF16.append(result, cp)
                i += UTF16.getCharCount(cp)
            }
            return result.toString()
        }
    }
}
