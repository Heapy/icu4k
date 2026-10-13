// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2002-2016, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.ICUBinary
import io.github.heapy.icu4k.impl.RBBIDataWrapper
import io.github.heapy.icu4k.util.BinaryBuffer

/** Complete original break-rule compiler, emitting ICU's big-endian binary format. */
internal class RBBIRuleBuilder(rules: String, debug: String? = null) {
    val fParseResult = RBBIRuleParseResult(rules, debug)
    val fScanner = RBBIRuleScanner(fParseResult)
    val fSetBuilder = RBBISetBuilder(fParseResult)
    lateinit var fForwardTable: RBBITableBuilder

    fun build(): ByteArray {
        fScanner.parse()
        fSetBuilder.buildRanges()
        fForwardTable = RBBITableBuilder(fParseResult, fSetBuilder, fScanner.numRules())
        fForwardTable.buildForwardTable()
        optimizeTables()
        fForwardTable.buildSafeReverseTable()
        if (fParseResult.fDebugEnv?.contains("states") == true) {
            fForwardTable.printStates(); fForwardTable.printRuleStatusTable(); fForwardTable.printReverseTable()
        }
        fSetBuilder.buildTrie()
        return flattenData()
    }

    fun optimizeTables() {
        var changed: Boolean
        do {
            changed = false
            val pair = RBBITableBuilder.IntPair(3, 0)
            while (fForwardTable.findDuplCharClassFrom(pair)) {
                fSetBuilder.mergeCategories(pair.first, pair.second)
                fForwardTable.removeColumn(pair.second)
                changed = true
            }
            while (fForwardTable.removeDuplicateStates() > 0) changed = true
        } while (changed)
    }

    fun flattenData(): ByteArray {
        val strippedRules = RBBIRuleScanner.stripRules(fParseResult.fStrippedRules.toString())
        val headerSize = RBBIDataWrapper.DH_SIZE * 4
        val forwardSize = align8(fForwardTable.getTableSize())
        val reverseSize = align8(fForwardTable.getSafeTableSize())
        val trieSize = align8(fSetBuilder.getTrieSize())
        val statusSize = align8(fParseResult.fRuleStatusVals.size * 4)
        val rules = javaCompatibleUtf8(strippedRules)
        val rulesSize = align8(rules.size + 1)
        val total = headerSize + forwardSize + reverseSize + trieSize + statusSize + rulesSize
        val dataHeader = ICUBinary.writeHeader(RBBIDataWrapper.DATA_FORMAT, RBBIDataWrapper.FORMAT_VERSION, 0)
        val output = BinaryBuffer(ByteArray(dataHeader.size + total))
        output.writeBytes(dataHeader)
        val header = IntArray(RBBIDataWrapper.DH_SIZE)
        header[RBBIDataWrapper.DH_MAGIC] = 0xb1a0
        header[RBBIDataWrapper.DH_FORMATVERSION] = RBBIDataWrapper.FORMAT_VERSION
        header[RBBIDataWrapper.DH_LENGTH] = total
        header[RBBIDataWrapper.DH_CATCOUNT] = fSetBuilder.getNumCharCategories()
        header[RBBIDataWrapper.DH_FTABLE] = headerSize
        header[RBBIDataWrapper.DH_FTABLELEN] = forwardSize
        header[RBBIDataWrapper.DH_RTABLE] = headerSize + forwardSize
        header[RBBIDataWrapper.DH_RTABLELEN] = reverseSize
        header[RBBIDataWrapper.DH_TRIE] = headerSize + forwardSize + reverseSize
        header[RBBIDataWrapper.DH_TRIELEN] = fSetBuilder.getTrieSize()
        header[RBBIDataWrapper.DH_STATUSTABLE] = header[RBBIDataWrapper.DH_TRIE] + trieSize
        header[RBBIDataWrapper.DH_STATUSTABLELEN] = statusSize
        header[RBBIDataWrapper.DH_RULESOURCE] = header[RBBIDataWrapper.DH_STATUSTABLE] + statusSize
        header[RBBIDataWrapper.DH_RULESOURCELEN] = rules.size
        for (value in header) output.writeInt(value)
        fun section(offset: Int) {
            check(output.position <= dataHeader.size + offset) { "assert failed" }
            // The zero-initialized buffer supplies alignment padding.
            output.position = dataHeader.size + offset
        }
        output.writeBytes(fForwardTable.exportTable().toBinary())
        section(header[RBBIDataWrapper.DH_RTABLE])
        output.writeBytes(fForwardTable.exportSafeTable().toBinary())
        section(header[RBBIDataWrapper.DH_TRIE])
        output.writeBytes(fSetBuilder.serializeTrie())
        section(header[RBBIDataWrapper.DH_STATUSTABLE])
        for (value in fParseResult.fRuleStatusVals) output.writeInt(value)
        section(header[RBBIDataWrapper.DH_RULESOURCE])
        output.writeBytes(rules)
        output.writeByte(0)
        return output.bytes
    }

    private fun BinaryBuffer.writeBytes(source: ByteArray) {
        requireRemaining(source.size)
        source.copyInto(bytes, arrayOffset + position)
        position += source.size
    }

    companion object {
        fun align8(i: Int): Int = (i + 7) and -8
        fun compileRules(rules: String): ByteArray = RBBIRuleBuilder(rules).build()

        // StandardCharsets.UTF_8 replaces an unpaired UTF-16 unit with '?'.
        // Make that policy explicit so Native and JS produce the same ICU binary.
        private fun javaCompatibleUtf8(text: String): ByteArray {
            val valid = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i++]
                when {
                    c in '\ud800'..'\udbff' && i < text.length && text[i] in '\udc00'..'\udfff' -> { valid.append(c); valid.append(text[i++]) }
                    c in '\ud800'..'\udfff' -> valid.append('?')
                    else -> valid.append(c)
                }
            }
            return valid.toString().encodeToByteArray()
        }
    }
}
