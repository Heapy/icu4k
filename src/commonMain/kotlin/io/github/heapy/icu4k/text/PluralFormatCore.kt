// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Formatting and RBNF parsing engine from ICU4J 78.3 PluralFormat.
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

/**
 * Shared execution state for PluralFormat and RBNF plural substitutions.
 */
internal class PluralFormatCore(var rules: PluralRules?, var numberFormat: NumberFormat?) {
    var pattern: String? = null
        private set
    var msgPattern: MessagePattern? = null
        private set
    var offset = 0.0
        private set
    private var selector = PluralSelector { context, _ -> rules!!.select(context as PluralRules.IFixedDecimal?) }

    fun resetPattern() {
        pattern = null
        msgPattern?.clear()
        offset = 0.0
    }
    fun applyPattern(pattern: String?) {
        this.pattern = pattern
        if (msgPattern == null) msgPattern = MessagePattern()
        try {
            msgPattern!!.parsePluralStyle(pattern)
            offset = msgPattern!!.getPluralOffset(0)
        } catch (e: RuntimeException) { resetPattern(); throw e }
    }
    /** Shallow Format.clone semantics, including the selector bound to the original owner. */
    fun copy(): PluralFormatCore = PluralFormatCore(rules, numberFormat).also {
        it.pattern = pattern
        it.msgPattern = msgPattern
        it.offset = offset
        it.selector = selector
    }
    fun format(number: Double): String = format(number, number)
    fun formatAny(input: Any?): String {
        val number = numberFormatInputOrNull(input) ?: throw IllegalArgumentException("'$input' is not a Number")
        return format(number, numberFormatDoubleValue(number))
    }
    private fun format(numberObject: Number, number: Double): String {
        if (msgPattern == null || msgPattern!!.countParts() == 0) return numberFormat!!.format(numberObject)
        val numberMinusOffset = number - offset
        val numberString: String
        val dec: PluralRules.IFixedDecimal?
        if (numberFormat is DecimalFormat) {
            val f = (numberFormat as DecimalFormat).toNumberFormatter()
            val result = if (offset == 0.0) f.format(numberObject) else f.format(numberMinusOffset)
            numberString = result.toString()
            dec = result.getFixedDecimal()
        } else {
            numberString = if (offset == 0.0) numberFormat!!.format(numberObject) else numberFormat!!.format(numberMinusOffset)
            dec = PluralRules.FixedDecimal(numberMinusOffset)
        }
        var partIndex = findSubMessage(msgPattern!!, 0, selector, dec, number)
        var result: StringBuilder? = null
        var prevIndex = msgPattern!!.getPart(partIndex).getLimit()
        while (true) {
            val part = msgPattern!!.getPart(++partIndex)
            val type = part.getType()
            var index = part.getIndex()
            if (type == MessagePattern.Part.Type.MSG_LIMIT) {
                return if (result == null) pattern!!.substring(prevIndex, index) else result.append(pattern!!, prevIndex, index).toString()
            } else if (type == MessagePattern.Part.Type.REPLACE_NUMBER ||
                (type == MessagePattern.Part.Type.SKIP_SYNTAX && msgPattern!!.jdkAposMode())) {
                if (result == null) result = StringBuilder()
                result.append(pattern!!, prevIndex, index)
                if (type == MessagePattern.Part.Type.REPLACE_NUMBER) result.append(numberString)
                prevIndex = part.getLimit()
            } else if (type == MessagePattern.Part.Type.ARG_START) {
                if (result == null) result = StringBuilder()
                result.append(pattern!!, prevIndex, index)
                prevIndex = index
                partIndex = msgPattern!!.getLimitPartIndex(partIndex)
                index = msgPattern!!.getPart(partIndex).getLimit()
                MessagePattern.appendReducedApostrophes(pattern!!, prevIndex, index, result)
                prevIndex = index
            }
        }
    }

    fun parseType(source: String?, scanner: RbnfLenientScanner?, pos: FormatFieldPosition?): String? {
        if (msgPattern == null || msgPattern!!.countParts() == 0) {
            pos!!.setBeginIndex(-1)
            pos.setEndIndex(-1)
            return null
        }
        var partIndex = 0
        val count = msgPattern!!.countParts()
        var startingAt = pos!!.getBeginIndex()
        if (startingAt < 0) startingAt = 0
        var keyword: String? = null
        var matchedWord: String? = null
        var matchedIndex = -1
        while (partIndex < count) {
            val partSelector = msgPattern!!.getPart(partIndex++)
            if (partSelector.getType() != MessagePattern.Part.Type.ARG_SELECTOR) continue
            val partStart = msgPattern!!.getPart(partIndex++)
            if (partStart.getType() != MessagePattern.Part.Type.MSG_START) continue
            val partLimit = msgPattern!!.getPart(partIndex++)
            if (partLimit.getType() != MessagePattern.Part.Type.MSG_LIMIT) continue
            val currArg = pattern!!.substring(partStart.getLimit(), partLimit.getIndex())
            val currMatchIndex = if (scanner != null) {
                val tempPos = source!!.indexOf(currArg, startingAt)
                if (tempPos >= 0) tempPos else scanner.findText(source, currArg, startingAt)!![0]
            } else source!!.indexOf(currArg, startingAt)
            if (currMatchIndex >= 0 && currMatchIndex >= matchedIndex && (matchedWord == null || currArg.length > matchedWord.length)) {
                matchedIndex = currMatchIndex
                matchedWord = currArg
                keyword = pattern!!.substring(partStart.getLimit(), partLimit.getIndex())
            }
        }
        if (keyword != null) {
            pos.setBeginIndex(matchedIndex)
            pos.setEndIndex(matchedIndex + matchedWord!!.length)
            return keyword
        }
        pos.setBeginIndex(-1)
        pos.setEndIndex(-1)
        return null
    }

    fun interface PluralSelector { fun select(context: Any?, number: Double): String? }

    companion object {
        fun findSubMessage(pattern: MessagePattern, initialPartIndex: Int, selector: PluralSelector?, context: Any?, number: Double): Int {
            val count = pattern.countParts()
            var partIndex = initialPartIndex
            var part = pattern.getPart(partIndex)
            val offset: Double
            if (part.getType().hasNumericValue()) { offset = pattern.getNumericValue(part); ++partIndex } else offset = 0.0
            var keyword: String? = null
            var haveKeywordMatch = false
            var msgStart = 0
            do {
                part = pattern.getPart(partIndex++)
                if (part.getType() == MessagePattern.Part.Type.ARG_LIMIT) break
                if (pattern.getPartType(partIndex).hasNumericValue()) {
                    part = pattern.getPart(partIndex++)
                    if (number == pattern.getNumericValue(part)) return partIndex
                } else if (!haveKeywordMatch) {
                    if (pattern.partSubstringMatches(part, "other")) {
                        if (msgStart == 0) {
                            msgStart = partIndex
                            if (keyword != null && keyword == "other") haveKeywordMatch = true
                        }
                    } else {
                        if (keyword == null) {
                            keyword = selector!!.select(context, number - offset)
                            if (msgStart != 0 && keyword!!.equals("other")) haveKeywordMatch = true
                        }
                        if (!haveKeywordMatch && pattern.partSubstringMatches(part, keyword)) {
                            msgStart = partIndex
                            haveKeywordMatch = true
                        }
                    }
                }
                partIndex = pattern.getLimitPartIndex(partIndex)
                ++partIndex
            } while (partIndex < count)
            return msgStart
        }
    }
}
