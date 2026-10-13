// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

/** Normalization implementation being ported; currently Hangul and UTF-16 primitives. */
internal class Normalizer2Impl {
    private var minDecompNoCP = 0
    private var minCompNoMaybeCP = 0
    private var minLcccCP = 0
    private var minYesNo = 0
    private var minYesNoMappingsOnly = 0
    private var minNoNo = 0
    private var minNoNoCompBoundaryBefore = 0
    private var minNoNoCompNoMaybeCC = 0
    private var minNoNoEmpty = 0
    private var limitNoNo = 0
    private var minMaybeNo = 0
    private var minMaybeNoCombinesFwd = 0
    private var minMaybeYes = 0
    private var centerNoNoDelta = 0
    private lateinit var dataVersion: io.github.heapy.icu4k.util.VersionInfo
    private lateinit var normTrie: io.github.heapy.icu4k.util.CodePointTrie
    private var extraData: String? = null
    private lateinit var smallFCD: ByteArray
    fun load(name: String): Normalizer2Impl = load(ICUBinary.getRequiredData(name))
    fun load(bytes: io.github.heapy.icu4k.util.BinaryBuffer): Normalizer2Impl {
        dataVersion = ICUBinary.readHeaderAndDataVersion(bytes, 0x4e726d32, ICUBinary.Authenticate { it[0].toInt() == 5 })
        val count = bytes.readInt() / 4
        if (count <= 18) throw io.github.heapy.icu4k.util.ICUUncheckedIOException("Normalizer2 data: not enough indexes")
        val indexes = IntArray(count)
        indexes[0] = count * 4
        for (i in 1 until count) indexes[i] = bytes.readInt()
        minDecompNoCP = indexes[8]
        minCompNoMaybeCP = indexes[9]
        minLcccCP = indexes[18]
        minYesNo = indexes[10]
        minYesNoMappingsOnly = indexes[14]
        minNoNo = indexes[11]
        minNoNoCompBoundaryBefore = indexes[15]
        minNoNoCompNoMaybeCC = indexes[16]
        minNoNoEmpty = indexes[17]
        limitNoNo = indexes[12]
        minMaybeNo = indexes[20]
        minMaybeNoCombinesFwd = indexes[21]
        minMaybeYes = indexes[13]
        centerNoNoDelta = (minMaybeNo shr 3) - 0x40 - 1
        val triePosition = bytes.position
        normTrie = io.github.heapy.icu4k.util.CodePointTrie.fromBinary(io.github.heapy.icu4k.util.CodePointTrie.Type.FAST, io.github.heapy.icu4k.util.CodePointTrie.ValueWidth.BITS_16, bytes)
        val trieLength = bytes.position - triePosition
        if (trieLength > indexes[1] - indexes[0]) throw io.github.heapy.icu4k.util.ICUUncheckedIOException("Normalizer2 data: not enough bytes for normTrie")
        ICUBinary.skipBytes(bytes, indexes[1] - indexes[0] - trieLength)
        val numChars = (indexes[2] - indexes[1]) / 2
        if (numChars != 0) extraData = ICUBinary.getString(bytes, numChars, 0)
        smallFCD = bytes.readBytes(256)
        return this
    }
    fun getNorm16(c: Int): Int = if (UTF16Plus.isLeadSurrogate(c)) 1 else normTrie[c]
    fun getRawNorm16(c: Int): Int = normTrie[c]

    fun makeFCD(s: CharSequence, initialSrc: Int, limit: Int, buffer: ReorderingBuffer?): Int {
        var src = initialSrc
        var prevBoundary = src
        var c = 0
        var prevFCD16 = 0
        var fcd16 = 0
        while (true) {
            var prevSrc = src
            while (src != limit) {
                c = s[src].code
                if (c < minLcccCP) { prevFCD16 = c.inv(); ++src }
                else if (!singleLeadMightHaveNonZeroFCD16(c)) { prevFCD16 = 0; ++src }
                else {
                    if (UTF16Plus.isLeadSurrogate(c) && src + 1 != limit && s[src + 1].code in 0xdc00..0xdfff) c = ((c - 0xd800) shl 10) + s[src + 1].code - 0xdc00 + 0x10000
                    fcd16 = getFCD16FromNormData(c)
                    if (fcd16 <= 255) { prevFCD16 = fcd16; src += if (c > 0xffff) 2 else 1 } else break
                }
            }
            if (src != prevSrc) {
                if (src == limit) { buffer?.flushAndAppendZeroCC(s, prevSrc, src); break }
                prevBoundary = src
                if (prevFCD16 < 0) {
                    val prev = prevFCD16.inv()
                    if (prev < minDecompNoCP) prevFCD16 = 0
                    else { prevFCD16 = getFCD16FromNormData(prev); if (prevFCD16 > 1) --prevBoundary }
                } else {
                    var p = src - 1
                    if (s[p].code in 0xdc00..0xdfff && prevSrc < p && s[p - 1].code in 0xd800..0xdbff) {
                        --p
                        prevFCD16 = getFCD16FromNormData(((s[p].code - 0xd800) shl 10) + s[p + 1].code - 0xdc00 + 0x10000)
                    }
                    if (prevFCD16 > 1) prevBoundary = p
                }
                if (buffer != null) { buffer.flushAndAppendZeroCC(s, prevSrc, prevBoundary); buffer.append(s, prevBoundary, src) }
                prevSrc = src
            } else if (src == limit) break
            src += if (c > 0xffff) 2 else 1
            if (prevFCD16 and 255 <= fcd16 shr 8) {
                if (fcd16 and 255 <= 1) prevBoundary = src
                buffer?.appendZeroCC(c)
                prevFCD16 = fcd16
                continue
            }
            if (buffer == null) return prevBoundary
            buffer.removeSuffix(prevSrc - prevBoundary)
            src = findNextFCDBoundary(s, src, limit)
            decomposeShort(s, prevBoundary, src, false, false, buffer)
            prevBoundary = src
            prevFCD16 = 0
        }
        return src
    }
    fun makeFCDAndAppend(s: CharSequence, doMakeFCD: Boolean, buffer: ReorderingBuffer) {
        var src = 0
        val limit = s.length
        if (!buffer.isEmpty()) {
            val firstBoundary = findNextFCDBoundary(s, 0, limit)
            if (firstBoundary != 0) {
                val lastBoundary = findPreviousFCDBoundary(buffer.getStringBuilder(), buffer.length())
                val middle = StringBuilder(buffer.length() - lastBoundary + firstBoundary + 16)
                middle.append(buffer.getStringBuilder(), lastBoundary, buffer.length())
                buffer.removeSuffix(buffer.length() - lastBoundary)
                middle.append(s, 0, firstBoundary)
                makeFCD(middle, 0, middle.length, buffer)
                src = firstBoundary
            }
        }
        if (doMakeFCD) makeFCD(s, src, limit, buffer) else buffer.append(s, src, limit)
    }
    internal fun decomposeShort(s: CharSequence, initialSrc: Int, limit: Int, stopAtCompBoundary: Boolean, onlyContiguous: Boolean, buffer: ReorderingBuffer): Int {
        var src = initialSrc
        while (src < limit) {
            val c = codePointAt(s, src)
            if (stopAtCompBoundary && c < minCompNoMaybeCP) return src
            val norm16 = getNorm16(c)
            if (stopAtCompBoundary && norm16HasCompBoundaryBefore(norm16)) return src
            src += if (c > 0xffff) 2 else 1
            decomposeCodePoint(c, norm16, buffer)
            if (stopAtCompBoundary && norm16HasCompBoundaryAfter(norm16, onlyContiguous)) return src
        }
        return src
    }
    internal fun findPreviousCompBoundary(s: CharSequence, initialP: Int, onlyContiguous: Boolean): Int {
        var p = initialP
        while (p > 0) {
            val c = codePointBefore(s, p)
            val norm16 = getNorm16(c)
            if (norm16HasCompBoundaryAfter(norm16, onlyContiguous)) break
            p -= if (c > 0xffff) 2 else 1
            if (c < minCompNoMaybeCP || norm16HasCompBoundaryBefore(norm16)) break
        }
        return p
    }
    internal fun findNextCompBoundary(s: CharSequence, initialP: Int, limit: Int, onlyContiguous: Boolean): Int {
        var p = initialP
        while (p < limit) {
            val c = codePointAt(s, p)
            val norm16 = normTrie[c]
            if (c < minCompNoMaybeCP || norm16HasCompBoundaryBefore(norm16)) break
            p += if (c > 0xffff) 2 else 1
            if (norm16HasCompBoundaryAfter(norm16, onlyContiguous)) break
        }
        return p
    }
    internal fun findPreviousFCDBoundary(s: CharSequence, initialP: Int): Int {
        var p = initialP
        while (p > 0) {
            val c = codePointBefore(s, p)
            if (c < minDecompNoCP) break
            val norm16 = getNorm16(c)
            if (norm16HasDecompBoundaryAfter(norm16)) break
            p -= if (c > 0xffff) 2 else 1
            if (norm16HasDecompBoundaryBefore(norm16)) break
        }
        return p
    }
    internal fun findNextFCDBoundary(s: CharSequence, initialP: Int, limit: Int): Int {
        var p = initialP
        while (p < limit) {
            val c = codePointAt(s, p)
            if (c < minLcccCP) break
            val norm16 = getNorm16(c)
            if (norm16HasDecompBoundaryBefore(norm16)) break
            p += if (c > 0xffff) 2 else 1
            if (norm16HasDecompBoundaryAfter(norm16)) break
        }
        return p
    }
    private fun getPreviousTrailCC(s: CharSequence, start: Int, p: Int): Int = if (start == p) 0 else getFCD16(codePointBefore(s, p))
    private fun hasCompBoundaryBefore(s: CharSequence, p: Int, limit: Int): Boolean = p == limit || hasCompBoundaryBefore(codePointAt(s, p))
    private fun hasCompBoundaryAfter(s: CharSequence, start: Int, p: Int, contiguous: Boolean): Boolean = start == p || hasCompBoundaryAfter(codePointBefore(s, p), contiguous)

    fun compose(s: CharSequence, initialSrc: Int, limit: Int, onlyContiguous: Boolean, doCompose: Boolean, buffer: ReorderingBuffer): Boolean {
        var src = initialSrc
        var prevBoundary = src
        val trie = normTrie as io.github.heapy.icu4k.util.CodePointTrie.Fast
        while (true) {
            var prevSrc: Int
            var c: Int
            var norm16 = 0
            while (true) {
                if (src == limit) {
                    if (prevBoundary != limit && doCompose) buffer.append(s, prevBoundary, limit)
                    return true
                }
                c = s[src].code
                if (c < minCompNoMaybeCP) { ++src; continue }
                norm16 = trie.bmpGet(c)
                if (norm16 < minNoNo) { ++src; continue }
                prevSrc = src++
                if (!UTF16Plus.isLeadSurrogate(c)) break
                if (src != limit && s[src].code in 0xdc00..0xdfff) {
                    c = ((c - 0xd800) shl 10) + s[src++].code - 0xdc00 + 0x10000
                    norm16 = trie.suppGet(c)
                    if (norm16 >= minNoNo) break
                }
            }
            if (norm16 < minMaybeNo) {
                if (!doCompose) return false
                if (isAlgorithmicNoNo(norm16)) {
                    if (norm16HasCompBoundaryAfter(norm16, onlyContiguous) || hasCompBoundaryBefore(s, src, limit)) {
                        if (prevBoundary != prevSrc) buffer.append(s, prevBoundary, prevSrc)
                        buffer.append(mapAlgorithmic(c, norm16), 0)
                        prevBoundary = src
                        continue
                    }
                } else if (norm16 < minNoNoCompBoundaryBefore) {
                    if (norm16HasCompBoundaryAfter(norm16, onlyContiguous) || hasCompBoundaryBefore(s, src, limit)) {
                        if (prevBoundary != prevSrc) buffer.append(s, prevBoundary, prevSrc)
                        var mapping = getData(norm16)
                        val length = extraData!![mapping++].code and MAPPING_LENGTH_MASK
                        buffer.append(extraData!!, mapping, mapping + length)
                        prevBoundary = src
                        continue
                    }
                } else if (norm16 >= minNoNoEmpty) {
                    if (hasCompBoundaryBefore(s, src, limit) || hasCompBoundaryAfter(s, prevBoundary, prevSrc, onlyContiguous)) {
                        if (prevBoundary != prevSrc) buffer.append(s, prevBoundary, prevSrc)
                        prevBoundary = src
                        continue
                    }
                }
            } else if (norm16 == JAMO_VT && prevBoundary != prevSrc) {
                val prev = s[prevSrc - 1].code
                if (c < Hangul.JAMO_T_BASE) {
                    val l = (prev - Hangul.JAMO_L_BASE).toChar().code
                    if (l < Hangul.JAMO_L_COUNT) {
                        if (!doCompose) return false
                        var t = if (src != limit) s[src].code - Hangul.JAMO_T_BASE else -1
                        if (t > 0 && t < Hangul.JAMO_T_COUNT) ++src
                        else t = if (hasCompBoundaryBefore(s, src, limit)) 0 else -1
                        if (t >= 0) {
                            val syllable = Hangul.HANGUL_BASE + (l * Hangul.JAMO_V_COUNT + c - Hangul.JAMO_V_BASE) * Hangul.JAMO_T_COUNT + t
                            --prevSrc
                            if (prevBoundary != prevSrc) buffer.append(s, prevBoundary, prevSrc)
                            buffer.append(syllable.toChar())
                            prevBoundary = src
                            continue
                        }
                    }
                } else if (Hangul.isHangulLV(prev)) {
                    if (!doCompose) return false
                    --prevSrc
                    if (prevBoundary != prevSrc) buffer.append(s, prevBoundary, prevSrc)
                    buffer.append((prev + c - Hangul.JAMO_T_BASE).toChar())
                    prevBoundary = src
                    continue
                }
            } else if (norm16 > JAMO_VT) {
                var cc = getCCFromNormalYesOrMaybe(norm16)
                if (onlyContiguous && getPreviousTrailCC(s, prevBoundary, prevSrc) > cc) {
                    if (!doCompose) return false
                } else {
                    var n16: Int
                    while (true) {
                        if (src == limit) {
                            if (doCompose) buffer.append(s, prevBoundary, limit)
                            return true
                        }
                        val prevCC = cc
                        c = codePointAt(s, src)
                        n16 = normTrie[c]
                        if (n16 >= MIN_YES_YES_WITH_CC) {
                            cc = getCCFromNormalYesOrMaybe(n16)
                            if (prevCC > cc) {
                                if (!doCompose) return false
                                break
                            }
                        } else break
                        src += if (c > 0xffff) 2 else 1
                    }
                    if (norm16HasCompBoundaryBefore(n16)) {
                        if (n16 < minNoNo) src += if (c > 0xffff) 2 else 1
                        continue
                    }
                }
            }
            if (prevBoundary != prevSrc && !norm16HasCompBoundaryBefore(norm16)) {
                c = codePointBefore(s, prevSrc)
                norm16 = normTrie[c]
                if (!norm16HasCompBoundaryAfter(norm16, onlyContiguous)) prevSrc -= if (c > 0xffff) 2 else 1
            }
            if (doCompose && prevBoundary != prevSrc) buffer.append(s, prevBoundary, prevSrc)
            val recomposeStartIndex = buffer.length()
            decomposeShort(s, prevSrc, src, false, onlyContiguous, buffer)
            src = decomposeShort(s, src, limit, true, onlyContiguous, buffer)
            recompose(buffer, recomposeStartIndex, onlyContiguous)
            if (!doCompose) {
                if (!buffer.equals(s, prevSrc, src)) return false
                buffer.remove()
            }
            prevBoundary = src
        }
    }

    fun composeAndAppend(s: CharSequence, doCompose: Boolean, onlyContiguous: Boolean, buffer: ReorderingBuffer) {
        var src = 0
        val limit = s.length
        if (!buffer.isEmpty()) {
            val firstStarter = findNextCompBoundary(s, 0, limit, onlyContiguous)
            if (firstStarter != 0) {
                val lastStarter = findPreviousCompBoundary(buffer.getStringBuilder(), buffer.length(), onlyContiguous)
                val middle = StringBuilder().append(buffer.getStringBuilder(), lastStarter, buffer.length())
                buffer.removeSuffix(buffer.length() - lastStarter)
                middle.append(s, 0, firstStarter)
                compose(middle, 0, middle.length, onlyContiguous, true, buffer)
                src = firstStarter
            }
        }
        if (doCompose) compose(s, src, limit, onlyContiguous, true, buffer)
        else buffer.append(s, src, limit)
    }

    /** Packed quick-check span (bits 31..1) and maybe flag (bit 0). */
    fun composeQuickCheck(s: CharSequence, initialSrc: Int, limit: Int, onlyContiguous: Boolean, doSpan: Boolean): Int {
        var src = initialSrc
        var qcResult = 0
        var prevBoundary = src
        val trie = normTrie as io.github.heapy.icu4k.util.CodePointTrie.Fast
        while (true) {
            var prevSrc: Int
            var c: Int
            var norm16 = 0
            while (true) {
                if (src == limit) return (src shl 1) or qcResult
                c = s[src].code
                if (c < minCompNoMaybeCP) { ++src; continue }
                norm16 = trie.bmpGet(c)
                if (norm16 < minNoNo) { ++src; continue }
                prevSrc = src++
                if (!UTF16Plus.isLeadSurrogate(c)) break
                if (src != limit && s[src].code in 0xdc00..0xdfff) {
                    c = ((c - 0xd800) shl 10) + s[src++].code - 0xdc00 + 0x10000
                    norm16 = trie.suppGet(c)
                    if (norm16 >= minNoNo) break
                }
            }
            var prevNorm16 = INERT
            if (prevBoundary != prevSrc) {
                prevBoundary = prevSrc
                if (!norm16HasCompBoundaryBefore(norm16)) {
                    c = codePointBefore(s, prevSrc)
                    val n16 = getNorm16(c)
                    if (!norm16HasCompBoundaryAfter(n16, onlyContiguous)) {
                        prevBoundary -= if (c > 0xffff) 2 else 1
                        prevNorm16 = n16
                    }
                }
            }
            if (norm16 >= minMaybeNo) {
                var fcd16 = getFCD16FromMaybeOrNonZeroCC(norm16)
                var cc = (fcd16 shr 8) and 255
                if (!(onlyContiguous && cc != 0 && getTrailCCFromCompYesAndZeroCC(prevNorm16) > cc)) {
                    while (true) {
                        if (norm16 < MIN_YES_YES_WITH_CC) {
                            if (!doSpan) qcResult = 1 else return prevBoundary shl 1
                        }
                        if (src == limit) return (src shl 1) or qcResult
                        val prevCC = fcd16 and 255
                        c = codePointAt(s, src)
                        norm16 = getNorm16(c)
                        if (norm16 < minMaybeNo) break
                        fcd16 = getFCD16FromMaybeOrNonZeroCC(norm16)
                        cc = (fcd16 shr 8) and 255
                        if (!(prevCC <= cc || cc == 0)) break
                        src += if (c > 0xffff) 2 else 1
                    }
                    if (norm16 < minNoNo) {
                        prevBoundary = src
                        src += if (c > 0xffff) 2 else 1
                        continue
                    }
                }
            }
            return prevBoundary shl 1
        }
    }
    private fun codePointBefore(s: CharSequence, i: Int): Int {
        val c = s[i - 1].code
        return if (c in 0xdc00..0xdfff && i > 1 && s[i - 2].code in 0xd800..0xdbff) ((s[i - 2].code - 0xd800) shl 10) + c - 0xdc00 + 0x10000 else c
    }
    internal fun recompose(buffer: ReorderingBuffer, recomposeStartIndex: Int, onlyContiguous: Boolean) {
        val sb = buffer.getStringBuilder()
        var p = recomposeStartIndex
        if (p == sb.length) return
        var compositionsList = -1
        var starter = -1
        var starterIsSupplementary = false
        var prevCC = 0
        while (true) {
            val c = codePointAt(sb, p)
            p += if (c > 0xffff) 2 else 1
            val norm16 = getNorm16(c)
            val cc = getCCFromYesOrMaybeYes(norm16)
            if (norm16 >= minMaybeNo && norm16 <= JAMO_VT && compositionsList >= 0 && (prevCC < cc || prevCC == 0)) {
                if (norm16 == JAMO_VT) {
                    if (c < Hangul.JAMO_T_BASE) {
                        val prev = (sb[starter].code - Hangul.JAMO_L_BASE).toChar().code
                        if (prev < Hangul.JAMO_L_COUNT) {
                            val pRemove = p - 1
                            var syllable = Hangul.HANGUL_BASE + (prev * Hangul.JAMO_V_COUNT + c - Hangul.JAMO_V_BASE) * Hangul.JAMO_T_COUNT
                            if (p != sb.length) {
                                val t = (sb[p].code - Hangul.JAMO_T_BASE).toChar().code
                                if (t < Hangul.JAMO_T_COUNT) { ++p; syllable += t }
                            }
                            sb[starter] = syllable.toChar()
                            sb.deleteRange(pRemove, p)
                            p = pRemove
                        }
                    }
                    if (p == sb.length) break
                    compositionsList = -1
                    continue
                }
                val compositeAndFwd = combine(compositionsList, c)
                if (compositeAndFwd >= 0) {
                    val composite = compositeAndFwd shr 1
                    val pRemove = p - if (c > 0xffff) 2 else 1
                    sb.deleteRange(pRemove, p)
                    p = pRemove
                    if (starterIsSupplementary) {
                        if (composite > 0xffff) {
                            sb[starter] = io.github.heapy.icu4k.text.UTF16.getLeadSurrogate(composite)
                            sb[starter + 1] = io.github.heapy.icu4k.text.UTF16.getTrailSurrogate(composite)
                        } else {
                            // Preserve the original branch's replacement with c.
                            sb[starter] = c.toChar()
                            sb.deleteAt(starter + 1)
                            starterIsSupplementary = false
                            --p
                        }
                    } else if (composite > 0xffff) {
                        starterIsSupplementary = true
                        sb[starter] = io.github.heapy.icu4k.text.UTF16.getLeadSurrogate(composite)
                        sb.insert(starter + 1, io.github.heapy.icu4k.text.UTF16.getTrailSurrogate(composite))
                        ++p
                    } else sb[starter] = composite.toChar()
                    if (p == sb.length) break
                    compositionsList = if (compositeAndFwd and 1 != 0) getCompositionsListForComposite(getRawNorm16(composite)) else -1
                    continue
                }
            }
            prevCC = cc
            if (p == sb.length) break
            if (cc == 0) {
                compositionsList = getCompositionsListForDecompYes(norm16)
                if (compositionsList >= 0) {
                    starterIsSupplementary = c > 0xffff
                    starter = p - if (starterIsSupplementary) 2 else 1
                }
            } else if (onlyContiguous) compositionsList = -1
        }
        buffer.flush()
    }
    fun composePair(a: Int, initialB: Int): Int {
        val norm16 = getNorm16(a)
        var b = initialB
        var list: Int
        if (norm16 == INERT) return -1
        if (norm16 < minYesNoMappingsOnly) {
            if (norm16 == JAMO_L) {
                b -= Hangul.JAMO_V_BASE
                return if (b in 0 until Hangul.JAMO_V_COUNT) Hangul.HANGUL_BASE + ((a - Hangul.JAMO_L_BASE) * Hangul.JAMO_V_COUNT + b) * Hangul.JAMO_T_COUNT else -1
            }
            if (norm16 == minYesNo) {
                b -= Hangul.JAMO_T_BASE
                return if (b > 0 && b < Hangul.JAMO_T_COUNT) a + b else -1
            }
            list = norm16 shr OFFSET_SHIFT
            if (norm16 > minYesNo) list += 1 + (extraData!![list].code and MAPPING_LENGTH_MASK)
        } else {
            if (norm16 < minMaybeNoCombinesFwd || norm16 >= MIN_NORMAL_MAYBE_YES) return -1
            list = getDataForMaybe(norm16)
            if (norm16 < minMaybeYes) list += 1 + (extraData!![list].code and MAPPING_LENGTH_MASK)
        }
        if (b < 0 || b > 0x10ffff) return -1
        return combine(list, b) shr 1
    }
    private fun combine(initialList: Int, trail: Int): Int {
        val data = extraData!!
        var list = initialList
        var firstUnit: Int
        if (trail < COMP_1_TRAIL_LIMIT) {
            val key1 = trail shl 1
            while (true) {
                firstUnit = data[list].code
                if (key1 <= firstUnit) break
                list += 2 + (firstUnit and COMP_1_TRIPLE)
            }
            if (key1 == firstUnit and COMP_1_TRAIL_MASK) return if (firstUnit and COMP_1_TRIPLE != 0) (data[list + 1].code shl 16) or data[list + 2].code else data[list + 1].code
        } else {
            val key1 = COMP_1_TRAIL_LIMIT + ((trail shr COMP_1_TRAIL_SHIFT) and COMP_1_TRIPLE.inv())
            val key2 = (trail shl COMP_2_TRAIL_SHIFT) and 0xffff
            while (true) {
                firstUnit = data[list].code
                if (key1 > firstUnit) list += 2 + (firstUnit and COMP_1_TRIPLE)
                else if (key1 == firstUnit and COMP_1_TRAIL_MASK) {
                    val secondUnit = data[list + 1].code
                    if (key2 > secondUnit) {
                        if (firstUnit and COMP_1_LAST_TUPLE != 0) break
                        list += 3
                    } else if (key2 == secondUnit and COMP_2_TRAIL_MASK) return ((secondUnit and COMP_2_TRAIL_MASK.inv()) shl 16) or data[list + 2].code
                    else break
                } else break
            }
        }
        return -1
    }
    private fun getCompositionsListForDecompYes(norm16: Int): Int = if (norm16 < JAMO_L || norm16 >= MIN_NORMAL_MAYBE_YES) -1 else getData(norm16)
    private fun getCompositionsListForComposite(norm16: Int): Int {
        val list = getData(norm16)
        return list + 1 + (extraData!![list].code and MAPPING_LENGTH_MASK)
    }
    fun decompose(s: CharSequence, src: Int, limit: Int, dest: StringBuilder, destLengthEstimate: Int) {
        val capacity = if (destLengthEstimate < 0) limit - src else destLengthEstimate
        dest.setLength(0)
        decompose(s, src, limit, ReorderingBuffer(this, dest, capacity))
    }
    fun decompose(s: CharSequence, initialSrc: Int, limit: Int, buffer: ReorderingBuffer?): Int {
        var src = initialSrc
        var c = 0; var norm16 = 0
        var prevBoundary = src; var prevCC = 0
        val trie = normTrie as io.github.heapy.icu4k.util.CodePointTrie.Fast
        while (true) {
            val prevSrc = src
            while (src != limit) {
                c = s[src].code
                if (c < minDecompNoCP) { ++src; continue }
                norm16 = trie.bmpGet(c)
                if (isMostDecompYesAndZeroCC(norm16)) { ++src; continue }
                if (!UTF16Plus.isLeadSurrogate(c)) break
                if (src + 1 != limit && s[src + 1].code in 0xdc00..0xdfff) {
                    c = ((c - 0xd800) shl 10) + s[src + 1].code - 0xdc00 + 0x10000
                    norm16 = trie.suppGet(c)
                    if (isMostDecompYesAndZeroCC(norm16)) src += 2 else break
                } else ++src
            }
            if (src != prevSrc) {
                if (buffer != null) buffer.flushAndAppendZeroCC(s, prevSrc, src)
                else { prevCC = 0; prevBoundary = src }
            }
            if (src == limit) break
            src += if (c > 0xffff) 2 else 1
            if (buffer != null) decomposeCodePoint(c, norm16, buffer)
            else {
                if (isDecompYes(norm16)) {
                    val cc = getCCFromYesOrMaybeYes(norm16)
                    if (prevCC <= cc || cc == 0) {
                        prevCC = cc
                        if (cc <= 1) prevBoundary = src
                        continue
                    }
                }
                return prevBoundary
            }
        }
        return src
    }
    private fun isMostDecompYesAndZeroCC(norm16: Int): Boolean = norm16 < minYesNo || norm16 == MIN_NORMAL_MAYBE_YES || norm16 == JAMO_VT
    private fun decomposeCodePoint(initialC: Int, initialNorm16: Int, buffer: ReorderingBuffer) {
        var c = initialC; var norm16 = initialNorm16
        if (norm16 >= limitNoNo) {
            if (norm16 >= minMaybeYes) { buffer.append(c, getCCFromYesOrMaybeYes(norm16)); return }
            if (norm16 < minMaybeNo) { c = mapAlgorithmic(c, norm16); norm16 = getRawNorm16(c) }
        }
        if (norm16 < minYesNo) buffer.append(c, 0)
        else if (norm16 == minYesNo || isHangulLVT(norm16)) Hangul.decompose(c, buffer)
        else {
            var mapping = getData(norm16)
            val data = extraData!!
            val firstUnit = data[mapping].code
            val length = firstUnit and MAPPING_LENGTH_MASK
            val trailCC = firstUnit shr 8
            val leadCC = if (firstUnit and MAPPING_HAS_CCC_LCCC_WORD != 0) data[mapping - 1].code shr 8 else 0
            ++mapping
            buffer.append(data, mapping, mapping + length, true, leadCC, trailCC)
        }
    }
    fun decomposeAndAppend(s: CharSequence, doDecompose: Boolean, buffer: ReorderingBuffer) {
        val limit = s.length
        if (limit == 0) return
        if (doDecompose) { decompose(s, 0, limit, buffer); return }
        var src = 0
        var c = codePointAt(s, src)
        val firstCC = getCC(getNorm16(c))
        var prevCC = firstCC; var cc = firstCC
        while (cc != 0) {
            prevCC = cc
            src += if (c > 0xffff) 2 else 1
            if (src >= limit) break
            c = codePointAt(s, src)
            cc = getCC(getNorm16(c))
        }
        buffer.append(s, 0, src, false, firstCC, prevCC)
        buffer.append(s, src, limit)
    }
    private fun codePointAt(s: CharSequence, i: Int): Int {
        val c = s[i].code
        return if (c in 0xd800..0xdbff && i + 1 < s.length && s[i + 1].code in 0xdc00..0xdfff) ((c - 0xd800) shl 10) + s[i + 1].code - 0xdc00 + 0x10000 else c
    }
    fun hasDecompBoundaryBefore(c: Int): Boolean = c < minLcccCP ||
        (c <= 0xffff && !singleLeadMightHaveNonZeroFCD16(c)) || norm16HasDecompBoundaryBefore(getNorm16(c))
    fun norm16HasDecompBoundaryBefore(norm16: Int): Boolean {
        if (norm16 < minNoNoCompNoMaybeCC) return true
        if (norm16 >= limitNoNo) return norm16 <= MIN_NORMAL_MAYBE_YES || norm16 == JAMO_VT
        val mapping = norm16 shr OFFSET_SHIFT
        val data = extraData!!
        return data[mapping].code and MAPPING_HAS_CCC_LCCC_WORD == 0 || data[mapping - 1].code and 0xff00 == 0
    }
    fun hasDecompBoundaryAfter(c: Int): Boolean {
        if (c < minDecompNoCP || (c <= 0xffff && !singleLeadMightHaveNonZeroFCD16(c))) return true
        return norm16HasDecompBoundaryAfter(getNorm16(c))
    }
    fun norm16HasDecompBoundaryAfter(norm16: Int): Boolean {
        if (norm16 <= minYesNo || isHangulLVT(norm16)) return true
        if (norm16 >= limitNoNo) {
            if (norm16 >= minMaybeYes) return norm16 <= MIN_NORMAL_MAYBE_YES || norm16 == JAMO_VT
            if (norm16 < minMaybeNo) return norm16 and DELTA_TCCC_MASK <= DELTA_TCCC_1
        }
        val mapping = getData(norm16)
        val data = extraData!!
        val firstUnit = data[mapping].code
        if (firstUnit > 0x1ff) return false
        if (firstUnit <= 0xff) return true
        return firstUnit and MAPPING_HAS_CCC_LCCC_WORD == 0 || data[mapping - 1].code and 0xff00 == 0
    }
    fun isDecompInert(c: Int): Boolean {
        val norm16 = getNorm16(c)
        return norm16 < minYesNo || norm16 == JAMO_VT || (norm16 >= minMaybeYes && norm16 <= MIN_NORMAL_MAYBE_YES)
    }
    fun hasCompBoundaryBefore(c: Int): Boolean = c < minCompNoMaybeCP || norm16HasCompBoundaryBefore(getNorm16(c))
    private fun norm16HasCompBoundaryBefore(norm16: Int): Boolean = norm16 < minNoNoCompNoMaybeCC || isAlgorithmicNoNo(norm16)
    fun hasCompBoundaryAfter(c: Int, onlyContiguous: Boolean): Boolean = norm16HasCompBoundaryAfter(getNorm16(c), onlyContiguous)
    private fun norm16HasCompBoundaryAfter(norm16: Int, onlyContiguous: Boolean): Boolean =
        norm16 and HAS_COMP_BOUNDARY_AFTER != 0 && (!onlyContiguous || isTrailCC01ForCompBoundaryAfter(norm16))
    private fun isTrailCC01ForCompBoundaryAfter(norm16: Int): Boolean = norm16 == INERT ||
        (if (isAlgorithmicNoNo(norm16)) norm16 and DELTA_TCCC_MASK <= DELTA_TCCC_1 else extraData!![norm16 shr OFFSET_SHIFT].code <= 0x1ff)
    fun isCompInert(c: Int, onlyContiguous: Boolean): Boolean {
        val norm16 = getNorm16(c)
        return norm16 < minNoNo && norm16 and HAS_COMP_BOUNDARY_AFTER != 0 &&
            (!onlyContiguous || norm16 == INERT || extraData!![norm16 shr OFFSET_SHIFT].code <= 0x1ff)
    }
    fun hasFCDBoundaryBefore(c: Int): Boolean = hasDecompBoundaryBefore(c)
    fun hasFCDBoundaryAfter(c: Int): Boolean = hasDecompBoundaryAfter(c)
    fun isFCDInert(c: Int): Boolean = getFCD16(c) <= 1
    fun getDecomposition(initialCodePoint: Int): String? {
        var c = initialCodePoint
        if (c < minDecompNoCP) return null
        var norm16 = getNorm16(c)
        if (norm16 >= minMaybeYes) return null
        var decomp = -1
        if (isAlgorithmicNoNo(norm16)) {
            c = mapAlgorithmic(c, norm16)
            decomp = c
            norm16 = getRawNorm16(c)
        }
        if (norm16 < minYesNo) return if (decomp < 0) null else io.github.heapy.icu4k.text.UTF16.valueOf(decomp)
        if (norm16 == minYesNo || isHangulLVT(norm16)) return buildString { Hangul.decompose(c, this) }
        var mapping = getData(norm16)
        val data = extraData!!
        val length = data[mapping++].code and MAPPING_LENGTH_MASK
        return data.substring(mapping, mapping + length)
    }
    fun getRawDecomposition(c: Int): String? {
        if (c < minDecompNoCP) return null
        val norm16 = getNorm16(c)
        if (isDecompYes(norm16)) return null
        if (norm16 == minYesNo || isHangulLVT(norm16)) return buildString { Hangul.getRawDecomposition(c, this) }
        if (isAlgorithmicNoNo(norm16)) return io.github.heapy.icu4k.text.UTF16.valueOf(mapAlgorithmic(c, norm16))
        var mapping = getData(norm16)
        val data = extraData!!
        val firstUnit = data[mapping].code
        val length = firstUnit and MAPPING_LENGTH_MASK
        if (firstUnit and MAPPING_HAS_RAW_MAPPING != 0) {
            val rawMapping = mapping - ((firstUnit shr 7) and 1) - 1
            val rm0 = data[rawMapping]
            if (rm0.code <= MAPPING_LENGTH_MASK) return data.substring(rawMapping - rm0.code, rawMapping)
            mapping += 3
            return StringBuilder(length - 1).append(rm0).append(data, mapping, mapping + length - 2).toString()
        }
        ++mapping
        return data.substring(mapping, mapping + length)
    }
    fun getFCD16(c: Int): Int {
        if (c < minDecompNoCP) return 0
        if (c <= 0xffff && !singleLeadMightHaveNonZeroFCD16(c)) return 0
        return getFCD16FromNormData(c)
    }
    fun getFCD16FromNormData(initialCodePoint: Int): Int {
        var c = initialCodePoint
        var norm16 = getNorm16(c)
        if (norm16 >= limitNoNo) {
            if (norm16 >= MIN_NORMAL_MAYBE_YES) {
                norm16 = getCCFromNormalYesOrMaybe(norm16)
                return norm16 or (norm16 shl 8)
            } else if (norm16 >= minMaybeYes) return 0
            else if (norm16 < minMaybeNo) {
                val deltaTrailCC = norm16 and DELTA_TCCC_MASK
                if (deltaTrailCC <= DELTA_TCCC_1) return deltaTrailCC shr OFFSET_SHIFT
                c = mapAlgorithmic(c, norm16)
                norm16 = getRawNorm16(c)
            }
        }
        if (norm16 <= minYesNo || isHangulLVT(norm16)) return 0
        val mapping = getData(norm16)
        val firstUnit = extraData!![mapping].code
        var fcd16 = firstUnit shr 8
        if (firstUnit and MAPPING_HAS_CCC_LCCC_WORD != 0) fcd16 = fcd16 or (extraData!![mapping - 1].code and 0xff00)
        return fcd16
    }
    private fun getFCD16FromMaybeOrNonZeroCC(initialNorm16: Int): Int {
        if (initialNorm16 >= MIN_NORMAL_MAYBE_YES) {
            val cc = getCCFromNormalYesOrMaybe(initialNorm16)
            return cc or (cc shl 8)
        }
        if (initialNorm16 >= minMaybeYes) return 0
        return extraData!![getDataForMaybe(initialNorm16)].code shr 8
    }
    internal fun getTrailCCFromCompYesAndZeroCC(norm16: Int): Int = if (norm16 <= minYesNo) 0 else extraData!![norm16 shr OFFSET_SHIFT].code shr 8
    private fun mapAlgorithmic(c: Int, norm16: Int): Int = c + (norm16 shr DELTA_SHIFT) - centerNoNoDelta
    private fun isHangulLVT(norm16: Int): Boolean = norm16 == (minYesNoMappingsOnly or HAS_COMP_BOUNDARY_AFTER)
    private fun getDataForMaybe(norm16: Int): Int = (norm16 - minMaybeNo + limitNoNo) shr OFFSET_SHIFT
    private fun getData(norm16: Int): Int = (if (norm16 >= minMaybeNo) norm16 - minMaybeNo + limitNoNo else norm16) shr OFFSET_SHIFT
    fun getCompQuickCheck(norm16: Int): Int = when {
        norm16 < minNoNo || norm16 >= MIN_YES_YES_WITH_CC -> 1
        norm16 >= minMaybeNo -> 2
        else -> 0
    }
    fun isAlgorithmicNoNo(norm16: Int): Boolean = norm16 >= limitNoNo && norm16 < minMaybeNo
    fun isCompNo(norm16: Int): Boolean = norm16 >= minNoNo && norm16 < minMaybeNo
    fun isDecompYes(norm16: Int): Boolean = norm16 < minYesNo || norm16 >= minMaybeYes
    fun getCC(norm16: Int): Int {
        if (norm16 >= MIN_NORMAL_MAYBE_YES) return getCCFromNormalYesOrMaybe(norm16)
        if (norm16 < minNoNo || norm16 >= limitNoNo) return 0
        val mapping = norm16 shr OFFSET_SHIFT
        return if (extraData!![mapping].code and MAPPING_HAS_CCC_LCCC_WORD != 0) extraData!![mapping - 1].code and 255 else 0
    }
    fun getCCFromYesOrMaybeYesCP(c: Int): Int = if (c < minCompNoMaybeCP) 0 else getCCFromYesOrMaybeYes(getNorm16(c))
    /** Input is a BMP single-or-lead code unit. */
    fun singleLeadMightHaveNonZeroFCD16(lead: Int): Boolean {
        val bits = smallFCD[lead shr 8].toInt()
        return bits != 0 && (bits shr ((lead shr 5) and 7)) and 1 != 0
    }
    companion object {
        const val MIN_YES_YES_WITH_CC = 0xfe02
        const val JAMO_VT = 0xfe00
        const val MIN_NORMAL_MAYBE_YES = 0xfc00
        const val JAMO_L = 2
        const val INERT = 1
        const val HAS_COMP_BOUNDARY_AFTER = 1
        const val OFFSET_SHIFT = 1
        const val DELTA_TCCC_0 = 0
        const val DELTA_TCCC_1 = 2
        const val DELTA_TCCC_GT_1 = 4
        const val DELTA_TCCC_MASK = 6
        const val DELTA_SHIFT = 3
        const val MAX_DELTA = 0x40
        const val MAPPING_HAS_CCC_LCCC_WORD = 0x80
        const val MAPPING_HAS_RAW_MAPPING = 0x40
        const val MAPPING_LENGTH_MASK = 0x1f
        const val COMP_1_LAST_TUPLE = 0x8000
        const val COMP_1_TRIPLE = 1
        const val COMP_1_TRAIL_LIMIT = 0x3400
        const val COMP_1_TRAIL_MASK = 0x7ffe
        const val COMP_1_TRAIL_SHIFT = 9
        const val COMP_2_TRAIL_SHIFT = 6
        const val COMP_2_TRAIL_MASK = 0xffc0
        fun getCCFromNormalYesOrMaybe(norm16: Int): Int = (norm16 shr OFFSET_SHIFT) and 255
        fun getCCFromYesOrMaybeYes(norm16: Int): Int = if (norm16 >= MIN_NORMAL_MAYBE_YES) getCCFromNormalYesOrMaybe(norm16) else 0
    }
    class ReorderingBuffer(private val impl: Normalizer2Impl, private val app: Appendable, destCapacity: Int) : Appendable {
        private val appIsStringBuilder = app is StringBuilder
        private val str: StringBuilder = if (app is StringBuilder) app else StringBuilder()
        private var reorderStart = 0
        private var lastCC = 0
        private var codePointStart = 0
        private var codePointLimit = 0
        init {
            if (appIsStringBuilder) {
                str.ensureCapacity(destCapacity)
                if (str.isNotEmpty()) {
                    setIterator()
                    lastCC = previousCC()
                    if (lastCC > 1) while (previousCC() > 1) { }
                    reorderStart = codePointLimit
                }
            }
        }
        fun isEmpty(): Boolean = str.isEmpty()
        fun length(): Int = str.length
        fun getLastCC(): Int = lastCC
        fun getStringBuilder(): StringBuilder = str
        fun equals(s: CharSequence, start: Int, limit: Int): Boolean = UTF16Plus.equal(str, 0, str.length, s, start, limit)
        fun append(c: Int, cc: Int) {
            if (lastCC <= cc || cc == 0) {
                str.append(io.github.heapy.icu4k.text.UTF16.valueOf(c))
                lastCC = cc
                if (cc <= 1) reorderStart = str.length
            } else insert(c, cc)
        }
        fun append(s: CharSequence, start: Int, limit: Int, isNFD: Boolean, initialLeadCC: Int, trailCC: Int) {
            if (start == limit) return
            var leadCC = initialLeadCC
            if (lastCC <= leadCC || leadCC == 0) {
                if (trailCC <= 1) reorderStart = str.length + limit - start
                else if (leadCC <= 1) reorderStart = str.length + 1
                str.append(s, start, limit)
                lastCC = trailCC
            } else {
                var index = start
                var c = codePointAt(s, index)
                index += if (c > 0xffff) 2 else 1
                insert(c, leadCC)
                while (index < limit) {
                    c = codePointAt(s, index)
                    index += if (c > 0xffff) 2 else 1
                    leadCC = if (index < limit) {
                        if (isNFD) getCCFromYesOrMaybeYes(impl.getNorm16(c)) else impl.getCC(impl.getNorm16(c))
                    } else trailCC
                    append(c, leadCC)
                }
            }
        }
        override fun append(c: Char): ReorderingBuffer {
            str.append(c); lastCC = 0; reorderStart = str.length
            return this
        }
        fun appendZeroCC(c: Int) { str.append(io.github.heapy.icu4k.text.UTF16.valueOf(c)); lastCC = 0; reorderStart = str.length }
        override fun append(value: CharSequence?): ReorderingBuffer {
            val s = value ?: throw NullPointerException()
            if (s.isNotEmpty()) { str.append(s); lastCC = 0; reorderStart = str.length }
            return this
        }
        override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): ReorderingBuffer {
            val s = value ?: throw NullPointerException()
            if (startIndex != endIndex) { str.append(s, startIndex, endIndex); lastCC = 0; reorderStart = str.length }
            return this
        }
        fun flush() {
            if (appIsStringBuilder) reorderStart = str.length
            else { app.append(str); str.setLength(0); reorderStart = 0 }
            lastCC = 0
        }
        fun flushAndAppendZeroCC(s: CharSequence, start: Int, limit: Int): ReorderingBuffer {
            if (appIsStringBuilder) { str.append(s, start, limit); reorderStart = str.length }
            else { app.append(str).append(s, start, limit); str.setLength(0); reorderStart = 0 }
            lastCC = 0
            return this
        }
        fun remove() { str.setLength(0); lastCC = 0; reorderStart = 0 }
        fun removeSuffix(suffixLength: Int) { str.deleteRange(str.length - suffixLength, str.length); lastCC = 0; reorderStart = str.length }
        private fun insert(c: Int, cc: Int) {
            setIterator(); skipPrevious()
            while (previousCC() > cc) { }
            str.insert(codePointLimit, io.github.heapy.icu4k.text.UTF16.valueOf(c))
            if (cc <= 1) reorderStart = codePointLimit + if (c > 0xffff) 2 else 1
        }
        private fun setIterator() { codePointStart = str.length }
        private fun skipPrevious() {
            codePointLimit = codePointStart
            codePointStart -= if (codePointBefore(str, codePointStart) > 0xffff) 2 else 1
        }
        private fun previousCC(): Int {
            codePointLimit = codePointStart
            if (reorderStart >= codePointStart) return 0
            val c = codePointBefore(str, codePointStart)
            codePointStart -= if (c > 0xffff) 2 else 1
            return impl.getCCFromYesOrMaybeYesCP(c)
        }
        private fun codePointAt(s: CharSequence, i: Int): Int {
            val c = s[i].code
            return if (c in 0xd800..0xdbff && i + 1 < s.length && s[i + 1].code in 0xdc00..0xdfff) ((c - 0xd800) shl 10) + s[i + 1].code - 0xdc00 + 0x10000 else c
        }
        private fun codePointBefore(s: CharSequence, i: Int): Int {
            val c = s[i - 1].code
            return if (c in 0xdc00..0xdfff && i > 1 && s[i - 2].code in 0xd800..0xdbff) ((s[i - 2].code - 0xd800) shl 10) + c - 0xdc00 + 0x10000 else c
        }
    }
    object Hangul {
        const val JAMO_L_BASE = 0x1100
        const val JAMO_L_END = 0x1112
        const val JAMO_V_BASE = 0x1161
        const val JAMO_V_END = 0x1175
        const val JAMO_T_BASE = 0x11a7
        const val JAMO_T_END = 0x11c2
        const val HANGUL_BASE = 0xac00
        const val HANGUL_END = 0xd7a3
        const val JAMO_L_COUNT = 19
        const val JAMO_V_COUNT = 21
        const val JAMO_T_COUNT = 28
        const val JAMO_L_LIMIT = JAMO_L_BASE + JAMO_L_COUNT
        const val JAMO_V_LIMIT = JAMO_V_BASE + JAMO_V_COUNT
        const val JAMO_VT_COUNT = JAMO_V_COUNT * JAMO_T_COUNT
        const val HANGUL_COUNT = JAMO_L_COUNT * JAMO_V_COUNT * JAMO_T_COUNT
        const val HANGUL_LIMIT = HANGUL_BASE + HANGUL_COUNT
        fun isHangul(c: Int): Boolean = c >= HANGUL_BASE && c < HANGUL_LIMIT
        fun isHangulLV(c: Int): Boolean {
            val offset = c - HANGUL_BASE
            return offset >= 0 && offset < HANGUL_COUNT && offset % JAMO_T_COUNT == 0
        }
        fun isJamoL(c: Int): Boolean = c >= JAMO_L_BASE && c < JAMO_L_LIMIT
        fun isJamoV(c: Int): Boolean = c >= JAMO_V_BASE && c < JAMO_V_LIMIT
        fun isJamoT(c: Int): Boolean { val t = c - JAMO_T_BASE; return t > 0 && t < JAMO_T_COUNT }
        fun isJamo(c: Int): Boolean = c >= JAMO_L_BASE && c <= JAMO_T_END && (c <= JAMO_L_END || c in JAMO_V_BASE..JAMO_V_END || c > JAMO_T_BASE)
        /** Input must be a Hangul syllable. Appends its recursive decomposition. */
        fun decompose(c: Int, buffer: Appendable): Int {
            var offset = c - HANGUL_BASE
            val trail = offset % JAMO_T_COUNT
            offset /= JAMO_T_COUNT
            buffer.append((JAMO_L_BASE + offset / JAMO_V_COUNT).toChar())
            buffer.append((JAMO_V_BASE + offset % JAMO_V_COUNT).toChar())
            if (trail == 0) return 2
            buffer.append((JAMO_T_BASE + trail).toChar())
            return 3
        }
        /** Input must be a Hangul syllable. The raw decomposition always has two units. */
        fun getRawDecomposition(c: Int, buffer: Appendable) {
            var offset = c - HANGUL_BASE
            val trail = offset % JAMO_T_COUNT
            if (trail == 0) {
                offset /= JAMO_T_COUNT
                buffer.append((JAMO_L_BASE + offset / JAMO_V_COUNT).toChar())
                buffer.append((JAMO_V_BASE + offset % JAMO_V_COUNT).toChar())
            } else {
                buffer.append((c - trail).toChar())
                buffer.append((JAMO_T_BASE + trail).toChar())
            }
        }
    }
    object UTF16Plus {
        fun isLeadSurrogate(c: Int): Boolean = c and -1024 == 0xd800
        fun isTrailSurrogate(c: Int): Boolean = c and -1024 == 0xdc00
        fun isSurrogate(c: Int): Boolean = c and -2048 == 0xd800
        fun isSurrogateLead(c: Int): Boolean = c and 0x400 == 0
        fun equal(s1: CharSequence, s2: CharSequence): Boolean {
            if (s1 === s2) return true
            if (s1.length != s2.length) return false
            for (i in s1.indices) if (s1[i] != s2[i]) return false
            return true
        }
        fun equal(s1: CharSequence, start1: Int, limit1: Int, s2: CharSequence, start2: Int, limit2: Int): Boolean {
            if (limit1 - start1 != limit2 - start2) return false
            if (s1 === s2 && start1 == start2) return true
            var i = start1; var j = start2
            while (i < limit1) if (s1[i++] != s2[j++]) return false
            return true
        }
    }
}
