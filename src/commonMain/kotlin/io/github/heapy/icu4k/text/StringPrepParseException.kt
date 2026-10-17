// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ParseException

/** Error code and source context for StringPrep and legacy IDNA processing. */
open class StringPrepParseException : ParseException {
    private val error: Int
    private val line: Int
    private val preContext=StringBuilder()
    private val postContext=StringBuilder()
    constructor(message: String?,error: Int) : super(message,-1) {this.error=error;line=0}
    constructor(message: String?,error: Int,rules: String?,position: Int) : super(message,-1) {
        this.error=error;setContext(rules,position);line=0
    }
    constructor(message: String?,error: Int,rules: String?,position: Int,lineNumber: Int) : super(message,-1) {
        this.error=error;setContext(rules,position);line=lineNumber
    }
    override fun equals(other: Any?): Boolean = other is StringPrepParseException && other.error==error
    override fun hashCode(): Int = stringPrepExceptionHashCode()
    override fun toString(): String = "${super.message}. line:  $line. preContext:  $preContext. postContext: $postContext\n"
    private fun setContext(rules: String?,position: Int) {
        val chars=rules!!.toCharArray()
        val start=if(position<=16)0 else position-15
        // Preserve ICU4J's original context-length calculation, including short prefixes.
        val length=if(start<=16)start else 16
        appendSlice(preContext,chars,start,length)
        appendSlice(postContext,chars,position,chars.size-position)
    }
    private fun appendSlice(target: StringBuilder,chars: CharArray,start: Int,length: Int) {
        if(start<0 || length<0 || start>chars.size-length)throw IndexOutOfBoundsException()
        target.append(chars.concatToString(start,start+length))
    }
    open fun getError(): Int = error
    companion object {
        const val INVALID_CHAR_FOUND=0
        const val ILLEGAL_CHAR_FOUND=1
        const val PROHIBITED_ERROR=2
        const val UNASSIGNED_ERROR=3
        const val CHECK_BIDI_ERROR=4
        const val STD3_ASCII_RULES_ERROR=5
        const val ACE_PREFIX_ERROR=6
        const val VERIFICATION_ERROR=7
        const val LABEL_TOO_LONG_ERROR=8
        const val BUFFER_OVERFLOW_ERROR=9
        const val ZERO_LENGTH_LABEL=10
        const val DOMAIN_NAME_TOO_LONG_ERROR=11
        private const val serialVersionUID=7160264827701651255L
    }
}
internal expect fun stringPrepExceptionHashCode(): Int
