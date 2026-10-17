// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2003-2016, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.UTS46
import io.github.heapy.icu4k.impl.IDNA2003

/** Internationalized domain-name processing. Instances can be shared; Info and destination are caller-owned. */
abstract class IDNA protected constructor() {
    abstract fun labelToASCII(label: CharSequence, dest: StringBuilder, info: Info): StringBuilder
    abstract fun labelToUnicode(label: CharSequence, dest: StringBuilder, info: Info): StringBuilder
    abstract fun nameToASCII(name: CharSequence, dest: StringBuilder, info: Info): StringBuilder
    abstract fun nameToUnicode(name: CharSequence, dest: StringBuilder, info: Info): StringBuilder

    class Info {
        internal val errors: MutableSet<Error> = ErrorSet()
        internal val labelErrors: MutableSet<Error> = ErrorSet()
        internal var transDiff=false
        internal var biDi=false
        internal var okBiDi=true
        fun hasErrors(): Boolean = errors.isNotEmpty()
        fun getErrors(): MutableSet<Error> = errors
        fun isTransitionalDifferent(): Boolean = transDiff
        internal fun reset() { errors.clear();labelErrors.clear();transDiff=false;biDi=false;okBiDi=true }
    }
    enum class Error {
        EMPTY_LABEL, LABEL_TOO_LONG, DOMAIN_NAME_TOO_LONG, LEADING_HYPHEN,
        TRAILING_HYPHEN, HYPHEN_3_4, LEADING_COMBINING_MARK, DISALLOWED,
        PUNYCODE, LABEL_HAS_DOT, INVALID_ACE_LABEL, BIDI, CONTEXTJ,
        CONTEXTO_PUNCTUATION, CONTEXTO_DIGITS
    }
    protected fun resetInfo(info: Info) = info.reset()
    protected fun hasCertainErrors(info: Info, errors: Set<Error>): Boolean = info.errors.any { it in errors }
    protected fun hasCertainLabelErrors(info: Info, errors: Set<Error>): Boolean = info.labelErrors.any { it in errors }
    protected fun addLabelError(info: Info, error: Error) { info.labelErrors.add(error) }
    protected fun promoteAndResetLabelErrors(info: Info) { info.errors.addAll(info.labelErrors);info.labelErrors.clear() }
    protected fun addError(info: Info, error: Error) { info.errors.add(error) }
    protected fun setTransitionalDifferent(info: Info) { info.transDiff=true }
    protected fun setBiDi(info: Info) { info.biDi=true }
    protected fun isBiDi(info: Info): Boolean = info.biDi
    protected fun setNotOkBiDi(info: Info) { info.okBiDi=false }
    protected fun isOkBiDi(info: Info): Boolean = info.okBiDi
    companion object {
        const val DEFAULT=0x30
        const val ALLOW_UNASSIGNED=1
        const val USE_STD3_RULES=2
        const val CHECK_BIDI=4
        const val CHECK_CONTEXTJ=8
        const val NONTRANSITIONAL_TO_ASCII=0x10
        const val NONTRANSITIONAL_TO_UNICODE=0x20
        const val CHECK_CONTEXTO=0x40
        fun getUTS46Instance(options: Int): IDNA = UTS46(options)
        fun convertToASCII(src: String,options: Int): StringBuilder = convertToASCII(UCharacterIterator.getInstance(src),options)
        fun convertToASCII(src: StringBuilder,options: Int): StringBuilder = convertToASCII(UCharacterIterator.getInstance(src),options)
        fun convertToASCII(src: UCharacterIterator,options: Int): StringBuilder = IDNA2003.convertToASCII(src,options)
        fun convertIDNToASCII(src: String,options: Int): StringBuilder = IDNA2003.convertIDNToASCII(src,options)
        fun convertIDNToASCII(src: StringBuilder,options: Int): StringBuilder = convertIDNToASCII(src.toString(),options)
        fun convertIDNToASCII(src: UCharacterIterator,options: Int): StringBuilder = convertIDNToASCII(src.getText(),options)
        fun convertToUnicode(src: String,options: Int): StringBuilder = convertToUnicode(UCharacterIterator.getInstance(src),options)
        fun convertToUnicode(src: StringBuilder,options: Int): StringBuilder = convertToUnicode(UCharacterIterator.getInstance(src),options)
        fun convertToUnicode(src: UCharacterIterator,options: Int): StringBuilder = IDNA2003.convertToUnicode(src,options)
        fun convertIDNToUnicode(src: String,options: Int): StringBuilder = IDNA2003.convertIDNToUnicode(src,options)
        fun convertIDNToUnicode(src: StringBuilder,options: Int): StringBuilder = convertIDNToUnicode(src.toString(),options)
        fun convertIDNToUnicode(src: UCharacterIterator,options: Int): StringBuilder = convertIDNToUnicode(src.getText(),options)
        fun compare(s1: String?,s2: String?,options: Int): Int {
            if(s1==null || s2==null)throw IllegalArgumentException("One of the source buffers is null")
            return IDNA2003.compare(s1,s2,options)
        }
        fun compare(s1: StringBuilder?,s2: StringBuilder?,options: Int): Int {
            if(s1==null || s2==null)throw IllegalArgumentException("One of the source buffers is null")
            return IDNA2003.compare(s1.toString(),s2.toString(),options)
        }
        fun compare(s1: UCharacterIterator?,s2: UCharacterIterator?,options: Int): Int {
            if(s1==null || s2==null)throw IllegalArgumentException("One of the source buffers is null")
            return IDNA2003.compare(s1.getText(),s2.getText(),options)
        }
    }
}

/** EnumSet's ordinal iteration and snapshot iterator, including remove after external mutation. */
private class ErrorSet : AbstractMutableSet<IDNA.Error>() {
    private var bits=0
    override val size: Int get() = bits.countOneBits()
    override fun contains(element: IDNA.Error): Boolean = bits and (1 shl element.ordinal)!=0
    override fun add(element: IDNA.Error): Boolean { val old=bits;bits=bits or (1 shl element.ordinal);return bits!=old }
    override fun remove(element: IDNA.Error): Boolean { val old=bits;bits=bits and (1 shl element.ordinal).inv();return bits!=old }
    override fun clear() { bits=0 }
    override fun iterator(): MutableIterator<IDNA.Error> = object : MutableIterator<IDNA.Error> {
        var remaining=bits
        var last=0
        override fun hasNext(): Boolean = remaining!=0
        override fun next(): IDNA.Error {
            if(remaining==0)throw NoSuchElementException()
            last=remaining and -remaining;remaining=remaining and last.inv()
            return IDNA.Error.entries[last.countTrailingZeroBits()]
        }
        override fun remove() {
            if(last==0)throw IllegalStateException()
            bits=bits and last.inv();last=0
        }
    }
}
