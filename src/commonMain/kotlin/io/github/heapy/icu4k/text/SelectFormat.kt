// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Port of ICU4J 78.3 SelectFormat (Unicode, Inc. and IBM).
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.PatternProps
import kotlin.jvm.Transient

/** Selects a message by a pattern identifier, falling back to the 'other' message. */
open class SelectFormat private constructor() : UFormatBase() {
    private var pattern: String? = null
    @Transient private var msgPattern: MessagePattern? = null

    constructor(pattern: String?) : this() { applyPattern(pattern) }

    private fun reset() { pattern = null; msgPattern?.clear() }
    open fun applyPattern(pattern: String?) {
        this.pattern = pattern
        if (msgPattern == null) msgPattern = MessagePattern()
        try { msgPattern!!.parseSelectStyle(pattern) } catch (e: RuntimeException) { reset(); throw e }
    }
    open fun toPattern(): String? = pattern

    fun format(keyword: String?): String {
        if (!PatternProps.isIdentifier(keyword!!)) throw IllegalArgumentException("Invalid formatting argument.")
        val parsed = msgPattern
        if (parsed == null || parsed.countParts() == 0) throw IllegalStateException("Invalid format error.")
        val msgStart = findSubMessage(parsed, 0, keyword)
        if (!parsed.jdkAposMode()) {
            val msgLimit = parsed.getLimitPartIndex(msgStart)
            return parsed.getPatternString()!!.substring(parsed.getPart(msgStart).getLimit(), parsed.getPatternIndex(msgLimit))
        }
        var result: StringBuilder? = null
        var prevIndex = parsed.getPart(msgStart).getLimit()
        var i = msgStart
        while (true) {
            val part = parsed.getPart(++i)
            val type = part.getType()
            var index = part.getIndex()
            if (type == MessagePattern.Part.Type.MSG_LIMIT) {
                return if (result == null) pattern!!.substring(prevIndex, index) else result.append(pattern!!, prevIndex, index).toString()
            } else if (type == MessagePattern.Part.Type.SKIP_SYNTAX) {
                if (result == null) result = StringBuilder()
                result.append(pattern!!, prevIndex, index)
                prevIndex = part.getLimit()
            } else if (type == MessagePattern.Part.Type.ARG_START) {
                if (result == null) result = StringBuilder()
                result.append(pattern!!, prevIndex, index)
                prevIndex = index
                i = parsed.getLimitPartIndex(i)
                index = parsed.getPart(i).getLimit()
                MessagePattern.appendReducedApostrophes(pattern!!, prevIndex, index, result)
                prevIndex = index
            }
        }
    }
    override fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        if (input !is String) throw IllegalArgumentException("'$input' is not a String")
        // Java evaluates the formatting argument before dereferencing the output buffer.
        val text = format(input)
        return output!!.append(text)
    }
    override fun parseObject(source: String?, position: ParsePosition?): Any? = throw UnsupportedOperationException()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectFormat || this::class != other::class) return false
        return msgPattern == other.msgPattern
    }
    override fun hashCode(): Int = pattern?.hashCode() ?: 0
    override fun toString(): String = "pattern='$pattern'"
    override fun clone(): Any = cloneHost() ?: SelectFormat().also {
        it.pattern = pattern
        it.msgPattern = msgPattern // Format.clone in ICU also shares the mutable parsed pattern.
    }
    private fun readObject(input: PluralObjectInputStream) {
        selectFormatReadFields(input)
        if (pattern != null) applyPattern(pattern)
    }
    companion object {
        private const val serialVersionUID: Long = 2993154333257524984L
        internal fun findSubMessage(pattern: MessagePattern, initialPartIndex: Int, keyword: String): Int {
            val count = pattern.countParts()
            var partIndex = initialPartIndex
            var msgStart = 0
            do {
                val part = pattern.getPart(partIndex++)
                if (part.getType() == MessagePattern.Part.Type.ARG_LIMIT) break
                if (pattern.partSubstringMatches(part, keyword)) return partIndex
                else if (msgStart == 0 && pattern.partSubstringMatches(part, "other")) msgStart = partIndex
                partIndex = pattern.getLimitPartIndex(partIndex)
                ++partIndex
            } while (partIndex < count)
            return msgStart
        }
    }
}
internal expect fun selectFormatReadFields(input: PluralObjectInputStream)
