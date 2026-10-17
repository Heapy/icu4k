// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2012, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Input views used by ICU's stateless, shared recognizers. Raw storage may exceed rawLength for streams. */
internal class CharsetDetectionInput(
    val raw: ByteArray,
    val rawLength: Int = raw.size,
    val filtered: ByteArray = raw,
    val filteredLength: Int = if(filtered===raw)rawLength else filtered.size,
) {
    init {require(rawLength in 0..raw.size);require(filteredLength in 0..filtered.size)}
}
internal data class CharsetCandidate(val name: String,val confidence: Int,val language: String?)
internal abstract class CharsetRecognizer {
    abstract fun getName(): String
    open fun getLanguage(): String? = null
    abstract fun match(input: CharsetDetectionInput): CharsetCandidate?
    protected fun candidate(confidence: Int): CharsetCandidate? = if(confidence==0)null else CharsetCandidate(getName(),confidence,getLanguage())
}
