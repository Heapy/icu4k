// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2002-2009, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.lang.UCharacter

/** Compiler variables; [error] is supplied by the rule scanner. */
internal class RBBISymbolTable(private val error: (Int) -> Unit) : SymbolTable {
    private val entries = HashMap<String, RBBINode>()
    private var cachedSetLookup: UnicodeSet? = null

    override fun lookup(s: String): CharArray? {
        var variable = entries[s] ?: return null
        while (variable.fLeftChild!!.fType == RBBINode.varRef) variable = variable.fLeftChild!!
        val expression = variable.fLeftChild!!
        val replacement = if (expression.fType == RBBINode.setRef) {
            cachedSetLookup = expression.fLeftChild!!.fInputSet
            "\uffff"
        } else {
            error(0x1020f) // U_BRK_MALFORMED_SET
            cachedSetLookup = null
            expression.fText!!
        }
        return replacement.toCharArray()
    }

    override fun lookupMatcher(ch: Int): UnicodeMatcher? {
        if (ch != 0xffff) return null
        val result = cachedSetLookup
        cachedSetLookup = null
        return result
    }

    override fun parseReference(text: String, pos: ParsePosition, limit: Int): String {
        val start = pos.index
        var i = start
        while (i < limit) {
            val c = UTF16.charAt(text, i)
            if ((i == start && !UCharacter.isUnicodeIdentifierStart(c)) ||
                !UCharacter.isUnicodeIdentifierPart(c)) break
            i += UTF16.getCharCount(c)
        }
        if (i == start) return ""
        pos.index = i
        return text.substring(start, i)
    }

    fun lookupNode(key: String): RBBINode? = entries[key]

    fun addEntry(key: String, node: RBBINode) {
        if (entries.containsKey(key)) {
            error(0x10207) // U_BRK_VARIABLE_REDFINITION
            return
        }
        entries[key] = node
    }

    fun rbbiSymtablePrint() {
        print("Variable Definitions\nName               Node Val     String Val\n" +
            "----------------------------------------------------------------------\n")
        val symbols = entries.entries.toList()
        for ((key, node) in symbols) {
            print("  $key    $node  ${node.fLeftChild!!.fText}\n")
        }
        println("\nParsed Variable Definitions\n")
        for ((key, node) in symbols) {
            print(key)
            node.fLeftChild!!.printTree(true)
            print("\n")
        }
    }
}
