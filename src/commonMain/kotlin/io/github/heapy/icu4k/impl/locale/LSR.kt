// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.locale

import io.github.heapy.icu4k.lang.UScript
import io.github.heapy.icu4k.lang.UCharacter
import io.github.heapy.icu4k.lang.UProperty

/** Language, script and region, with explicit-subtag flags used by locale matching. */
class LSR(val language: String, val script: String, val region: String, val flags: Int) {
    internal val regionIndex: Int = indexForRegion(region)
    override fun toString(): String = buildString {
        append(language)
        if (script.isNotEmpty()) append('-').append(script)
        if (region.isNotEmpty()) append('-').append(region)
    }
    fun isEquivalentTo(other: LSR): Boolean = language == other.language && script == other.script && region == other.region
    override fun equals(other: Any?): Boolean = this === other || other is LSR &&
        isEquivalentTo(other) && flags == other.flags
    override fun hashCode(): Int = (((31 + language.hashCode()) * 31 + script.hashCode()) * 31 + region.hashCode()) * 31 + flags

    /** ICU's resource encoding supports two/three-letter languages and script property codes. */
    fun encodeToIntForResource(m49: List<String>?): Int {
        val languageCode = language[0].code - 'a'.code + 1 +
            27 * (language[1].code - 'a'.code + 1) +
            if (language.length == 2) 0 else 27 * 27 * (language[2].code - 'a'.code + 1)
        val regionCode = if (region.length == 3) {
            val index = m49!!.indexOf(region)
            if (index < 0) throw IllegalStateException("Please add '$region' to M49 in LocaleDistanceMapper.java")
            index
        } else region[0].code - 'A'.code + 1 + 27 * (region[1].code - 'A'.code + 1)
        val scriptCode = UScript.getCodeFromName(script)
        return (languageCode + 27 * 27 * 27 * regionCode) or (scriptCode shl 24)
    }

    companion object {
        const val REGION_INDEX_LIMIT = 1001 + 26 * 26
        const val EXPLICIT_LSR = 7
        const val EXPLICIT_LANGUAGE = 4
        const val EXPLICIT_SCRIPT = 2
        const val EXPLICIT_REGION = 1
        const val IMPLICIT_LSR = 0
        const val DONT_CARE_FLAGS = 0
        const val DEBUG_OUTPUT = false

        fun indexForRegion(region: String): Int {
            if (region.length == 2) {
                val a = region[0] - 'A'
                val b = region[1] - 'A'
                if (a !in 0..25 || b !in 0..25) return 0
                return 26 * a + b + 1001
            }
            if (region.length == 3) {
                val a = region[0] - '0'
                val b = region[1] - '0'
                val c = region[2] - '0'
                if (a !in 0..9 || b !in 0..9 || c !in 0..9) return 0
                return (10 * a + b) * 10 + c + 1
            }
            return 0
        }
        fun decodeInts(nums: IntArray, m49: Array<String>?): Array<LSR> {
            val decoder = CachedDecoder(m49)
            return Array(nums.size) { decoder.decode(nums[it]) }
        }
    }

    private class CachedDecoder(private val m49: Array<String>?) {
        private val languages = HashMap<Int, String>(556)
        private val scripts = HashMap<Int, String>(UCharacter.getIntPropertyMaxValue(UProperty.SCRIPT))
        private val regions = HashMap<Int, String>(253)
        fun decode(encoded: Int): LSR {
            if (encoded == 0) return LSR("", "", "", IMPLICIT_LSR)
            if (encoded == 1) return LSR("skip", "script", "", IMPLICIT_LSR)
            val languageCode = (encoded and 0x00ffffff) % (27 * 27 * 27)
            val language = languages.getOrPut(languageCode) {
                buildString {
                    append(('a'.code + languageCode % 27 - 1).toChar())
                    append(('a'.code + languageCode / 27 % 27 - 1).toChar())
                    if (languageCode / (27 * 27) != 0) append(('a'.code + languageCode / (27 * 27) - 1).toChar())
                }
            }
            val scriptCode = (encoded shr 24) and 0xff
            val script = scripts.getOrPut(scriptCode) { UScript.getShortName(scriptCode)!! }
            val regionCode = ((encoded and 0x00ffffff) / (27 * 27 * 27)) % (27 * 27)
            val region = if (regionCode < 27) m49!![regionCode] else regions.getOrPut(regionCode) {
                buildString {
                    append(('A'.code + regionCode % 27 - 1).toChar())
                    append(('A'.code + regionCode / 27 % 27 - 1).toChar())
                }
            }
            return LSR(language, script, region, IMPLICIT_LSR)
        }
    }
}
