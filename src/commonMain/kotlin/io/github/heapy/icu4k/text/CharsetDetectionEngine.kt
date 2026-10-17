// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2005-2012, International Business Machines Corporation and others. All Rights Reserved.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

/** Shared detector state; the public match/decoder and platform stream adapters build on this. */
internal class CharsetDetectionEngine {
    private var raw: ByteArray? = null
    private var rawLength = 0
    private val filtered = ByteArray(BUFFER_SIZE)
    internal var filteredLength = 0
        private set
    internal val byteStats = ShortArray(256)
    internal var c1Bytes = false
        private set
    private var stripTags = false
    private var enabledRecognizers: BooleanArray? = null
    var declaredEncoding: String? = null

    /** Keep the caller's storage, as ICU4J does. Stream adapters supply padded storage and a length. */
    fun setText(bytes: ByteArray, length: Int = bytes.size): CharsetDetectionEngine {
        require(length in 0..bytes.size)
        raw = bytes
        rawLength = length
        return this
    }

    fun inputFilterEnabled(): Boolean = stripTags
    fun enableInputFilter(filter: Boolean): Boolean {
        val previous = stripTags
        stripTags = filter
        return previous
    }

    fun getDetectableCharsets(): Array<String> = recognizers.indices
        .filter { enabledRecognizers?.get(it) ?: defaultEnabled(it) }
        .map { recognizers[it].getName() }.toTypedArray()

    fun setDetectableCharset(encoding: String?, enabled: Boolean): CharsetDetectionEngine {
        val index = recognizers.indexOfFirst { it.getName() == encoding }
        require(index >= 0) { "Invalid encoding: \"$encoding\"" }
        if (enabledRecognizers == null && enabled != defaultEnabled(index)) {
            enabledRecognizers = BooleanArray(recognizers.size) { defaultEnabled(it) }
        }
        enabledRecognizers?.set(index, enabled)
        return this
    }

    fun detect(): CharsetCandidate? = detectAll().firstOrNull()
    fun detectAll(): Array<CharsetCandidate> {
        val input = mungeInput()
        val matches = recognizers.mapIndexedNotNull { index, recognizer ->
            if (enabledRecognizers?.get(index) ?: defaultEnabled(index)) recognizer.match(input) else null
        }
        // Source stably sorts ascending, then reverses: ties reverse registration order.
        return matches.sortedBy { it.confidence }.asReversed().toTypedArray()
    }

    internal fun mungeInput(): CharsetDetectionInput {
        val bytes = raw ?: throw NullPointerException()
        var destination = 0
        var inMarkup = false
        var openTags = 0
        var badTags = 0
        if (stripTags) {
            var source = 0
            while (source < rawLength && destination < filtered.size) {
                val b = bytes[source++]
                if (b == '<'.code.toByte()) {
                    if (inMarkup) badTags++
                    inMarkup = true
                    openTags++
                }
                if (!inMarkup) filtered[destination++] = b
                if (b == '>'.code.toByte()) inMarkup = false
            }
            filteredLength = destination
        }
        if (openTags < 5 || openTags / 5 < badTags || (filteredLength < 100 && rawLength > 600)) {
            filteredLength = minOf(rawLength, BUFFER_SIZE)
            bytes.copyInto(filtered, endIndex = filteredLength)
        }
        byteStats.fill(0)
        for (i in 0..<filteredLength) {
            val value = filtered[i].toInt() and 255
            byteStats[value] = (byteStats[value] + 1).toShort()
        }
        c1Bytes = (0x80..0x9f).any { byteStats[it] != 0.toShort() }
        return CharsetDetectionInput(bytes, rawLength, filtered, filteredLength, c1Bytes)
    }

    companion object {
        const val BUFFER_SIZE = 8000
        private val recognizers: List<CharsetRecognizer> by lazy {
            val sbcs = CharsetSbcsTables.recognizers().associateBy { it.getName() }
            listOf(
                CharsetRecog_UTF8(),
                CharsetRecog_Unicode.CharsetRecog_UTF_16_BE(),
                CharsetRecog_Unicode.CharsetRecog_UTF_16_LE(),
                CharsetRecog_Unicode.CharsetRecog_UTF_32_BE(),
                CharsetRecog_Unicode.CharsetRecog_UTF_32_LE(),
                CharsetRecog_mbcs.CharsetRecog_sjis(),
                CharsetRecog_2022.CharsetRecog_2022JP(),
                CharsetRecog_2022.CharsetRecog_2022CN(),
                CharsetRecog_2022.CharsetRecog_2022KR(),
                CharsetRecog_mbcs.CharsetRecog_gb_18030(),
                CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_euc_jp(),
                CharsetRecog_mbcs.CharsetRecog_euc.CharsetRecog_euc_kr(),
                CharsetRecog_mbcs.CharsetRecog_big5(),
            ) + listOf(
                "ISO-8859-1", "ISO-8859-2", "ISO-8859-5", "ISO-8859-6", "ISO-8859-7",
                "ISO-8859-8-I", "ISO-8859-8", "windows-1251", "windows-1256", "KOI8-R",
                "ISO-8859-9", "IBM424_rtl", "IBM424_ltr", "IBM420_rtl", "IBM420_ltr",
            ).map { sbcs.getValue(it) }
        }
        private fun defaultEnabled(index: Int): Boolean = index < 24
        fun getAllDetectableCharsets(): Array<String> = recognizers.map { it.getName() }.toTypedArray()
    }
}
