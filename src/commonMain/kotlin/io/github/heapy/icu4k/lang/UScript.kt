// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.lang

import io.github.heapy.icu4k.impl.UCharacterProperty
import io.github.heapy.icu4k.util.ULocale

/** Script values and Script_Extensions backed by pinned ICU data. Includes locale and script-name selection. */
class UScript private constructor() {
    enum class ScriptUsage { NOT_ENCODED, UNKNOWN, EXCLUDED, LIMITED_USE, ASPIRATIONAL, RECOMMENDED }
    companion object {
        private val scriptProps = intArrayOf(
            10485824, // Zyyy
            10486543, // Zinh
            27264552, // Arab
            77595953, // Armn
            10488213, // Beng
            39858437, // Bopo
            73405380, // Cher
            71304162, // Copt
            77595695, // Cyrl
            71369748, // Dsrt
            10488069, // Deva
            10490528, // Ethi
            10490067, // Geor
            4260656, // Goth
            77595561, // Grek
            10488469, // Gujr
            10488341, // Guru
            44063575, // Hani
            10529792, // Hang
            27264464, // Hebr
            44052555, // Hira
            10488981, // Knda
            44052651, // Kana
            44046208, // Khmr
            44043941, // Laoo
            77594700, // Latn
            10489109, // Mlym
            4200486, // Mong
            44044288, // Mymr
            4200079, // Ogam
            4260608, // Ital
            10488597, // Orya
            4200096, // Runr
            10489221, // Sinh
            23070480, // Syrc
            10488725, // Taml
            10488853, // Telu
            27264908, // Thaa
            44043799, // Thai
            10489664, // Tibt
            6296768, // Cans
            39887496, // Yiii
            4200195, // Tglg
            4200227, // Hano
            4200259, // Buhd
            4200291, // Tagb
            2107406, // Brai
            21039104, // Cprt
            6297856, // Limb
            4259840, // Linb
            4260992, // Osma
            4260944, // Shaw
            39852368, // Tale
            4260736, // Ugar
            0,
            4200960, // Bugi
            71314432, // Glag
            21039616, // Khar
            6334464, // Sylo
            39852416, // Talu
            6303024, // Tfng
            4260768, // Xpeo
            6298373, // Bali
            6298560, // Batk
            0,
            4263941, // Brah
            6334976, // Cham
            0,
            0,
            0,
            0,
            4272467, // Egyp
            0,
            44063575, // Hans
            44063575, // Hant
            4287260, // Hmng
            88149153, // Hung
            0,
            6334852, // Java
            6334730, // Kali
            0,
            0,
            6298624, // Lepc
            4261447, // Lina
            23070784, // Mand
            0,
            21039488, // Mero
            23070666, // Nkoo
            21040128, // Orkh
            4260715, // Perm
            4237376, // Phag
            21039360, // Phnx
            6385408, // Plrd
            0,
            0,
            0,
            0,
            0,
            0,
            6333769, // Vaii
            0,
            4268032, // Xsux
            0,
            2162128, // Zzzz
            4260512, // Cari
            44052555, // Jpan
            39852576, // Lana
            4260480, // Lyci
            21039392, // Lydi
            6298714, // Olck
            4237616, // Rjng
            6334594, // Saur
            4315216, // Sgnw
            6298499, // Sund
            0,
            6335424, // Mtei
            21039168, // Armi
            21039872, // Avst
            6361347, // Cakm
            10529792, // Kore
            4264067, // Kthi
            21039832, // Mani
            21039968, // Phli
            21040015, // Phlp
            0,
            21039936, // Prti
            20973568, // Samr
            39889536, // Tavt
            0,
            0,
            6334112, // Bamu
            6333648, // Lisu
            0,
            21039712, // Sarb
            4287206, // Bass
            4308000, // Dupl
            4261120, // Elba
            4264725, // Gran
            0,
            0,
            21096450, // Mend
            21039520, // Merc
            21039765, // Narb
            21039254, // Nbat
            21039219, // Palm
            4264638, // Sind
            71375028, // Wara
            0,
            0,
            4287055, // Mroo
            37859780, // Nshu
            4264323, // Shrd
            4264144, // Sora
            4265600, // Takr
            37847593, // Tang
            0,
            4277248, // Hluw
            4264456, // Khoj
            4265092, // Tirh
            4261175, // Aghb
            4264274, // Mahj
            37820183, // Ahom
            21039348, // Hatr
            4265486, // Modi
            4264591, // Mult
            4266688, // Pauc
            4265358, // Sidd
            90302729, // Adlm
            4267022, // Bhks
            4267122, // Marc
            6362130, // Newa
            73467061, // Osge
            44063575, // Hanb
            10490130, // Jamo
            0,
            4267280, // Gonm
            4266588, // Soyo
            4266507, // Zanb
            4265995, // Dogr
            4267377, // Gong
            4267749, // Maka
            71396928, // Medf
            23137554, // Rohg
            21040962, // Sogd
            21040921, // Sogo
            21041137, // Elym
            6414600, // Hmnp
            4266446, // Nand
            6415073, // Wcho
            21041087, // Chrs
            4266252, // Diak
            37850213, // Kits
            21040776, // Yezi
            4272101, // Cpmn
            21041020, // Ougr
            4287164, // Tnsa
            4317840, // Toto
            71370114, // Vith
            37822235, // Kawi
            4318438, // Nagm
            0,
            88149341, // Gara
            4284700, // Gukh
            4287813, // Krai
            4318672, // Onao
            4266948, // Sunu
            4261314, // Todr
            4264850, // Tutg
            71397025, // Berf
            21039440, // Sidt
            37873365, // Tayo
            4267462, // Tols
        )
        private fun getScriptProps(script: Int): Int = if (script in scriptProps.indices) scriptProps[script] else 0
        fun getSampleString(script: Int): String {
            val sample = getScriptProps(script) and 0x1fffff
            return if (sample == 0) "" else io.github.heapy.icu4k.text.UTF16.valueOf(sample)
        }
        fun getUsage(script: Int): ScriptUsage = ScriptUsage.entries[(getScriptProps(script) shr 21) and 7]
        fun isRightToLeft(script: Int): Boolean = getScriptProps(script) and (1 shl 24) != 0
        fun breaksBetweenLetters(script: Int): Boolean = getScriptProps(script) and (1 shl 25) != 0
        fun isCased(script: Int): Boolean = getScriptProps(script) and (1 shl 26) != 0

        const val INVALID_CODE: Int = -1
        const val COMMON: Int = 0
        const val INHERITED: Int = 1
        const val ARABIC: Int = 2
        const val ARMENIAN: Int = 3
        const val BENGALI: Int = 4
        const val BOPOMOFO: Int = 5
        const val CHEROKEE: Int = 6
        const val COPTIC: Int = 7
        const val CYRILLIC: Int = 8
        const val DESERET: Int = 9
        const val DEVANAGARI: Int = 10
        const val ETHIOPIC: Int = 11
        const val GEORGIAN: Int = 12
        const val GOTHIC: Int = 13
        const val GREEK: Int = 14
        const val GUJARATI: Int = 15
        const val GURMUKHI: Int = 16
        const val HAN: Int = 17
        const val HANGUL: Int = 18
        const val HEBREW: Int = 19
        const val HIRAGANA: Int = 20
        const val KANNADA: Int = 21
        const val KATAKANA: Int = 22
        const val KHMER: Int = 23
        const val LAO: Int = 24
        const val LATIN: Int = 25
        const val MALAYALAM: Int = 26
        const val MONGOLIAN: Int = 27
        const val MYANMAR: Int = 28
        const val OGHAM: Int = 29
        const val OLD_ITALIC: Int = 30
        const val ORIYA: Int = 31
        const val RUNIC: Int = 32
        const val SINHALA: Int = 33
        const val SYRIAC: Int = 34
        const val TAMIL: Int = 35
        const val TELUGU: Int = 36
        const val THAANA: Int = 37
        const val THAI: Int = 38
        const val TIBETAN: Int = 39
        const val CANADIAN_ABORIGINAL: Int = 40
        const val UCAS: Int = CANADIAN_ABORIGINAL
        const val YI: Int = 41
        const val TAGALOG: Int = 42
        const val HANUNOO: Int = 43
        const val BUHID: Int = 44
        const val TAGBANWA: Int = 45
        const val BRAILLE: Int = 46
        const val CYPRIOT: Int = 47
        const val LIMBU: Int = 48
        const val LINEAR_B: Int = 49
        const val OSMANYA: Int = 50
        const val SHAVIAN: Int = 51
        const val TAI_LE: Int = 52
        const val UGARITIC: Int = 53
        const val KATAKANA_OR_HIRAGANA: Int = 54
        const val BUGINESE: Int = 55
        const val GLAGOLITIC: Int = 56
        const val KHAROSHTHI: Int = 57
        const val SYLOTI_NAGRI: Int = 58
        const val NEW_TAI_LUE: Int = 59
        const val TIFINAGH: Int = 60
        const val OLD_PERSIAN: Int = 61
        const val BALINESE: Int = 62
        const val BATAK: Int = 63
        const val BLISSYMBOLS: Int = 64
        const val BRAHMI: Int = 65
        const val CHAM: Int = 66
        const val CIRTH: Int = 67
        const val OLD_CHURCH_SLAVONIC_CYRILLIC: Int = 68
        const val DEMOTIC_EGYPTIAN: Int = 69
        const val HIERATIC_EGYPTIAN: Int = 70
        const val EGYPTIAN_HIEROGLYPHS: Int = 71
        const val KHUTSURI: Int = 72
        const val SIMPLIFIED_HAN: Int = 73
        const val TRADITIONAL_HAN: Int = 74
        const val PAHAWH_HMONG: Int = 75
        const val OLD_HUNGARIAN: Int = 76
        const val HARAPPAN_INDUS: Int = 77
        const val JAVANESE: Int = 78
        const val KAYAH_LI: Int = 79
        const val LATIN_FRAKTUR: Int = 80
        const val LATIN_GAELIC: Int = 81
        const val LEPCHA: Int = 82
        const val LINEAR_A: Int = 83
        const val MANDAIC: Int = 84
        const val MANDAEAN: Int = MANDAIC
        const val MAYAN_HIEROGLYPHS: Int = 85
        const val MEROITIC_HIEROGLYPHS: Int = 86
        const val MEROITIC: Int = MEROITIC_HIEROGLYPHS
        const val NKO: Int = 87
        const val ORKHON: Int = 88
        const val OLD_PERMIC: Int = 89
        const val PHAGS_PA: Int = 90
        const val PHOENICIAN: Int = 91
        const val MIAO: Int = 92
        const val PHONETIC_POLLARD: Int = MIAO
        const val RONGORONGO: Int = 93
        const val SARATI: Int = 94
        const val ESTRANGELO_SYRIAC: Int = 95
        const val WESTERN_SYRIAC: Int = 96
        const val EASTERN_SYRIAC: Int = 97
        const val TENGWAR: Int = 98
        const val VAI: Int = 99
        const val VISIBLE_SPEECH: Int = 100
        const val CUNEIFORM: Int = 101
        const val UNWRITTEN_LANGUAGES: Int = 102
        const val UNKNOWN: Int = 103
        const val CARIAN: Int = 104
        const val JAPANESE: Int = 105
        const val LANNA: Int = 106
        const val LYCIAN: Int = 107
        const val LYDIAN: Int = 108
        const val OL_CHIKI: Int = 109
        const val REJANG: Int = 110
        const val SAURASHTRA: Int = 111
        const val SIGN_WRITING: Int = 112
        const val SUNDANESE: Int = 113
        const val MOON: Int = 114
        const val MEITEI_MAYEK: Int = 115
        const val IMPERIAL_ARAMAIC: Int = 116
        const val AVESTAN: Int = 117
        const val CHAKMA: Int = 118
        const val KOREAN: Int = 119
        const val KAITHI: Int = 120
        const val MANICHAEAN: Int = 121
        const val INSCRIPTIONAL_PAHLAVI: Int = 122
        const val PSALTER_PAHLAVI: Int = 123
        const val BOOK_PAHLAVI: Int = 124
        const val INSCRIPTIONAL_PARTHIAN: Int = 125
        const val SAMARITAN: Int = 126
        const val TAI_VIET: Int = 127
        const val MATHEMATICAL_NOTATION: Int = 128
        const val SYMBOLS: Int = 129
        const val BAMUM: Int = 130
        const val LISU: Int = 131
        const val NAKHI_GEBA: Int = 132
        const val OLD_SOUTH_ARABIAN: Int = 133
        const val BASSA_VAH: Int = 134
        const val DUPLOYAN: Int = 135
        const val DUPLOYAN_SHORTAND: Int = DUPLOYAN
        const val ELBASAN: Int = 136
        const val GRANTHA: Int = 137
        const val KPELLE: Int = 138
        const val LOMA: Int = 139
        const val MENDE: Int = 140
        const val MEROITIC_CURSIVE: Int = 141
        const val OLD_NORTH_ARABIAN: Int = 142
        const val NABATAEAN: Int = 143
        const val PALMYRENE: Int = 144
        const val KHUDAWADI: Int = 145
        const val SINDHI: Int = KHUDAWADI
        const val WARANG_CITI: Int = 146
        const val AFAKA: Int = 147
        const val JURCHEN: Int = 148
        const val MRO: Int = 149
        const val NUSHU: Int = 150
        const val SHARADA: Int = 151
        const val SORA_SOMPENG: Int = 152
        const val TAKRI: Int = 153
        const val TANGUT: Int = 154
        const val WOLEAI: Int = 155
        const val ANATOLIAN_HIEROGLYPHS: Int = 156
        const val KHOJKI: Int = 157
        const val TIRHUTA: Int = 158
        const val CAUCASIAN_ALBANIAN: Int = 159
        const val MAHAJANI: Int = 160
        const val AHOM: Int = 161
        const val HATRAN: Int = 162
        const val MODI: Int = 163
        const val MULTANI: Int = 164
        const val PAU_CIN_HAU: Int = 165
        const val SIDDHAM: Int = 166
        const val ADLAM: Int = 167
        const val BHAIKSUKI: Int = 168
        const val MARCHEN: Int = 169
        const val NEWA: Int = 170
        const val OSAGE: Int = 171
        const val HAN_WITH_BOPOMOFO: Int = 172
        const val JAMO: Int = 173
        const val SYMBOLS_EMOJI: Int = 174
        const val MASARAM_GONDI: Int = 175
        const val SOYOMBO: Int = 176
        const val ZANABAZAR_SQUARE: Int = 177
        const val DOGRA: Int = 178
        const val GUNJALA_GONDI: Int = 179
        const val MAKASAR: Int = 180
        const val MEDEFAIDRIN: Int = 181
        const val HANIFI_ROHINGYA: Int = 182
        const val SOGDIAN: Int = 183
        const val OLD_SOGDIAN: Int = 184
        const val ELYMAIC: Int = 185
        const val NYIAKENG_PUACHUE_HMONG: Int = 186
        const val NANDINAGARI: Int = 187
        const val WANCHO: Int = 188
        const val CHORASMIAN: Int = 189
        const val DIVES_AKURU: Int = 190
        const val KHITAN_SMALL_SCRIPT: Int = 191
        const val YEZIDI: Int = 192
        const val CYPRO_MINOAN: Int = 193
        const val OLD_UYGHUR: Int = 194
        const val TANGSA: Int = 195
        const val TOTO: Int = 196
        const val VITHKUQI: Int = 197
        const val KAWI: Int = 198
        const val NAG_MUNDARI: Int = 199
        const val ARABIC_NASTALIQ: Int = 200
        const val GARAY: Int = 201
        const val GURUNG_KHEMA: Int = 202
        const val KIRAT_RAI: Int = 203
        const val OL_ONAL: Int = 204
        const val SUNUWAR: Int = 205
        const val TODHRI: Int = 206
        const val TULU_TIGALARI: Int = 207
        const val BERIA_ERFE: Int = 208
        const val SIDETIC: Int = 209
        const val TAI_YO: Int = 210
        const val TOLONG_SIKI: Int = 211
        const val TRADITIONAL_HAN_WITH_LATIN: Int = 212
        const val CODE_LIMIT: Int = 213
        private fun getCodesFromLocale(locale: ULocale): IntArray? {
            val language = locale.getLanguage()
            if (language == "ja") return intArrayOf(KATAKANA, HIRAGANA, HAN)
            if (language == "ko") return intArrayOf(HANGUL, HAN)
            val script = locale.getScript()
            if (language == "zh" && script == "Hant") return intArrayOf(HAN, BOPOMOFO)
            if (script.isNotEmpty()) {
                var code = getCodeFromName(script)
                if (code != INVALID_CODE) {
                    if (code == SIMPLIFIED_HAN || code == TRADITIONAL_HAN) code = HAN
                    return intArrayOf(code)
                }
            }
            return null
        }

        fun getCode(locale: ULocale): IntArray? =
            getCodesFromLocale(locale) ?: getCodesFromLocale(ULocale.addLikelySubtags(locale))

        fun getCode(nameOrAbbrOrLocale: String): IntArray? {
            var triedCode = false
            var separator = nameOrAbbrOrLocale.indexOf('_')
            if (separator < 0) separator = nameOrAbbrOrLocale.indexOf('-')
            if (separator < 0 || (separator == 3 && nameOrAbbrOrLocale.length > 8) ||
                (separator == 7 && nameOrAbbrOrLocale.length == 11)) {
                val code = getCodeFromName(nameOrAbbrOrLocale)
                if (code != UProperty.UNDEFINED) return intArrayOf(code)
                triedCode = true
            }
            getCode(ULocale(nameOrAbbrOrLocale))?.let { return it }
            if (!triedCode) {
                val code = getCodeFromName(nameOrAbbrOrLocale)
                if (code != UProperty.UNDEFINED) return intArrayOf(code)
            }
            return null
        }

        fun getCodeFromName(nameOrAbbr: String): Int = UCharacter.getPropertyValueEnumNoThrow(UProperty.SCRIPT, nameOrAbbr)
        fun getName(scriptCode: Int): String? = UCharacter.getPropertyValueName(UProperty.SCRIPT, scriptCode, UProperty.NameChoice.LONG)
        fun getShortName(scriptCode: Int): String? = UCharacter.getPropertyValueName(UProperty.SCRIPT, scriptCode, UProperty.NameChoice.SHORT)
        fun getScript(codepoint: Int): Int {
            require(codepoint in 0..0x10ffff) { codepoint.toString() }
            val props = UCharacterProperty.INSTANCE
            val scriptX = props.getAdditional(codepoint, 0) and UCharacterProperty.SCRIPT_X_MASK
            val codeOrIndex = scriptX and UCharacterProperty.MAX_SCRIPT
            return when {
                scriptX < UCharacterProperty.SCRIPT_X_WITH_COMMON -> codeOrIndex
                scriptX < UCharacterProperty.SCRIPT_X_WITH_INHERITED -> COMMON
                scriptX < UCharacterProperty.SCRIPT_X_WITH_OTHER -> INHERITED
                else -> props.m_scriptExtensions_[codeOrIndex].code
            }
        }
        fun hasScript(c: Int, sc: Int): Boolean {
            val props = UCharacterProperty.INSTANCE
            val scriptX = props.getAdditional(c, 0) and UCharacterProperty.SCRIPT_X_MASK
            val codeOrIndex = scriptX and UCharacterProperty.MAX_SCRIPT
            if (scriptX < UCharacterProperty.SCRIPT_X_WITH_COMMON) return sc == codeOrIndex
            val extensions = props.m_scriptExtensions_
            var index = codeOrIndex
            if (scriptX >= UCharacterProperty.SCRIPT_X_WITH_OTHER) index = extensions[index + 1].code
            if (sc > 0x7fff) return false
            while (sc > extensions[index].code) ++index
            return sc == extensions[index].code and 0x7fff
        }
        /** Clears and fills a common set in place of JVM BitSet; returns the original signed result. */
        fun getScriptExtensions(c: Int, set: MutableSet<Int>): Int {
            set.clear()
            val props = UCharacterProperty.INSTANCE
            val scriptX = props.getAdditional(c, 0) and UCharacterProperty.SCRIPT_X_MASK
            val codeOrIndex = scriptX and UCharacterProperty.MAX_SCRIPT
            if (scriptX < UCharacterProperty.SCRIPT_X_WITH_COMMON) { set.add(codeOrIndex); return codeOrIndex }
            val extensions = props.m_scriptExtensions_
            var index = codeOrIndex
            if (scriptX >= UCharacterProperty.SCRIPT_X_WITH_OTHER) index = extensions[index + 1].code
            var length = 0
            do {
                val sx = extensions[index++].code
                set.add(sx and 0x7fff)
                ++length
            } while (sx < 0x8000)
            return -length
        }
    }
}
