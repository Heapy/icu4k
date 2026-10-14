// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.util

import kotlin.jvm.JvmField
import io.github.heapy.icu4k.text.LocaleDisplayNames
import io.github.heapy.icu4k.impl.LocaleDefaults
import io.github.heapy.icu4k.impl.ICUResourceBundle
import io.github.heapy.icu4k.impl.LocaleIDParser
import io.github.heapy.icu4k.impl.LocaleIDs
import io.github.heapy.icu4k.impl.SoftCache
import io.github.heapy.icu4k.impl.locale.*

/** ICU locale identity and BCP47 conversion. Locale services are being ported from ICU4J. */
class ULocale private constructor(localeID: String, normalize: Boolean) : Comparable<ULocale> {
    private val localeID = if (normalize) getName(localeID) else localeID
    constructor(localeID: String) : this(localeID, true)
    constructor(a: String?, b: String?) : this(a, b, null)
    constructor(a: String?, b: String?, c: String?) : this(lscvToID(a, b, c, ""))
    internal var platformLocale: Any? = null
    private val base: BaseLocale by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (this.localeID.isEmpty()) BaseLocale.ROOT else LocaleIDParser(this.localeID).let {
            BaseLocale.getInstance(it.getLanguage(), it.getScript(), it.getCountry(), it.getVariant())
        }
    }
    private val extensions: LocaleExtensions by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val keywords = getKeywords()
        if (keywords == null) LocaleExtensions.EMPTY_EXTENSIONS else {
            val builder = InternalLocaleBuilder()
            while (keywords.hasNext()) {
                val key = keywords.next()
                val value = getKeywordValue(key)!!
                if (key == "attribute") {
                    for (attribute in value.split('-', '_')) try { builder.addUnicodeLocaleAttribute(attribute) } catch (_: LocaleSyntaxException) { }
                } else if (key.length >= 2) {
                    val bcpKey = toUnicodeLocaleKey(key); val bcpType = toUnicodeLocaleType(key, value)
                    if (bcpKey != null && bcpType != null) try { builder.setUnicodeLocaleKeyword(bcpKey, bcpType) } catch (_: LocaleSyntaxException) { }
                } else if (key.length == 1 && key[0] != UNICODE_LOCALE_EXTENSION) {
                    try { builder.setExtension(key[0], value.replace('_', '-')) } catch (_: LocaleSyntaxException) { }
                }
            }
            builder.getLocaleExtensions()
        }
    }
    class Type private constructor() {
        internal companion object { fun create(): Type = Type() }
    }
    fun clone(): ULocale = this
    fun getName(): String = localeID
    fun getBaseName(): String = getBaseName(localeID)
    fun getLanguage(): String = base.getLanguage()
    fun getScript(): String = base.getScript()
    fun getCountry(): String = base.getRegion()
    fun getVariant(): String = base.getVariant()
    fun getKeywords(): MutableIterator<String>? = getKeywords(localeID)
    fun getKeywordValue(key: String?): String? = getKeywordValue(localeID, key)
    fun setKeywordValue(key: String?, value: String?): ULocale = ULocale(setKeywordValue(localeID, key, value), false)
    fun getFallback(): ULocale? = if (localeID.isEmpty() || localeID[0] == '@') null else ULocale(fallbackString(localeID), false)
    fun getDisplayLanguage(): String? = getDisplayLanguage(getDefault(Category.DISPLAY))
    fun getDisplayLanguage(displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale).languageDisplayName(getLanguage())
    fun getDisplayLanguageWithDialect(): String? = getDisplayLanguageWithDialect(getDefault(Category.DISPLAY))
    fun getDisplayLanguageWithDialect(displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale).languageDisplayName(getBaseName())
    fun getDisplayScript(): String? = getDisplayScript(getDefault(Category.DISPLAY))
    fun getDisplayScript(displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale).scriptDisplayName(getScript())
    @Deprecated("This API is ICU internal only.")
    fun getDisplayScriptInContext(): String? = getDisplayScriptInContext(getDefault(Category.DISPLAY))
    @Deprecated("This API is ICU internal only.")
    fun getDisplayScriptInContext(displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale).scriptDisplayNameInContext(getScript())
    fun getDisplayCountry(): String? = getDisplayCountry(getDefault(Category.DISPLAY))
    fun getDisplayCountry(displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale).regionDisplayName(getCountry())
    fun getDisplayVariant(): String? = getDisplayVariant(getDefault(Category.DISPLAY))
    fun getDisplayVariant(displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale).variantDisplayName(getVariant())
    fun getDisplayName(): String? = getDisplayName(getDefault(Category.DISPLAY))
    fun getDisplayName(displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale).localeDisplayName(this)
    fun getDisplayNameWithDialect(): String? = getDisplayNameWithDialect(getDefault(Category.DISPLAY))
    fun getDisplayNameWithDialect(displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale, LocaleDisplayNames.DialectHandling.DIALECT_NAMES).localeDisplayName(this)
    fun getDisplayKeywordValue(keyword: String?): String? = getDisplayKeywordValue(keyword, getDefault(Category.DISPLAY))
    fun getDisplayKeywordValue(keyword: String?, displayLocale: ULocale?): String? {
        val key = AsciiUtil.toLowerString(keyword!!.trim { it <= ' ' })
        val value = getKeywordValue(key)
        return LocaleDisplayNames.getInstance(displayLocale).keyValueDisplayName(key, value)
    }
    fun getISO3Language(): String = getISO3Language(localeID)
    fun getISO3Country(): String = getISO3Country(localeID)
    fun getExtension(key: Char): String? {
        if (!LocaleExtensions.isValidKey(key)) throw IllegalArgumentException("Invalid extension key: $key")
        return extensions.getExtensionValue(key)
    }
    fun getExtensionKeys(): Set<Char> = extensions.getKeys()
    fun getUnicodeLocaleAttributes(): Set<String> = extensions.getUnicodeLocaleAttributes()
    fun getUnicodeLocaleKeys(): Set<String> = extensions.getUnicodeLocaleKeys()
    fun getUnicodeLocaleType(key: String): String? {
        if (!LocaleExtensions.isValidUnicodeLocaleKey(key)) throw IllegalArgumentException("Invalid Unicode locale key: $key")
        return extensions.getUnicodeLocaleType(key)
    }
    override fun toString(): String = localeID
    override fun hashCode(): Int = localeID.hashCode()
    override fun equals(other: Any?): Boolean = this === other || other is ULocale && localeID == other.localeID
    override fun compareTo(other: ULocale): Int {
        if (this === other) return 0
        var cmp = getLanguage().compareTo(other.getLanguage())
        if (cmp == 0) cmp = getScript().compareTo(other.getScript())
        if (cmp == 0) cmp = getCountry().compareTo(other.getCountry())
        if (cmp == 0) cmp = getVariant().compareTo(other.getVariant())
        if (cmp != 0) return cmp.compareTo(0)
        val a = getKeywords(); val b = other.getKeywords()
        if (a == null) return if (b == null) 0 else -1
        if (b == null) return 1
        while (a.hasNext()) {
            if (!b.hasNext()) return 1
            val ak = a.next(); val bk = b.next()
            val keys = ak.compareTo(bk); if (keys != 0) return keys.compareTo(0)
            val av = getKeywordValue(ak); val bv = other.getKeywordValue(bk)
            val values = if (av == null) { if (bv == null) 0 else -1 } else if (bv == null) 1 else av.compareTo(bv)
            if (values != 0) return values.compareTo(0)
        }
        return if (b.hasNext()) -1 else 0
    }
    fun toLanguageTag(): String {
        var b = base; var e = extensions
        if (b.getVariant().equals("POSIX", ignoreCase = true)) {
            b = BaseLocale.getInstance(b.getLanguage(), b.getScript(), b.getRegion(), "")
            if (e.getUnicodeLocaleType("va") == null) {
                val builder = InternalLocaleBuilder()
                try { builder.setLocale(BaseLocale.ROOT, e); builder.setUnicodeLocaleKeyword("va", "posix"); e = builder.getLocaleExtensions() }
                catch (error: LocaleSyntaxException) { throw RuntimeException(error) }
            }
        }
        val tag = LanguageTag.parseLocale(b, e)
        return buildString {
            if (tag.getLanguage().isNotEmpty()) append(LanguageTag.canonicalizeLanguage(tag.getLanguage()))
            if (tag.getScript().isNotEmpty()) append('-').append(LanguageTag.canonicalizeScript(tag.getScript()))
            if (tag.getRegion().isNotEmpty()) append('-').append(LanguageTag.canonicalizeRegion(tag.getRegion()))
            for (variant in tag.getVariants().sorted()) append('-').append(LanguageTag.canonicalizeVariant(variant))
            for (extension in tag.getExtensions()) append('-').append(LanguageTag.canonicalizeExtension(extension))
            if (tag.getPrivateuse().isNotEmpty()) {
                if (isEmpty()) append("und")
                append("-x-").append(LanguageTag.canonicalizePrivateuse(tag.getPrivateuse()))
            }
        }
    }
    class Builder {
        private val builder = InternalLocaleBuilder()
        private fun mutate(block: () -> Unit): Builder {
            try { block() } catch (error: LocaleSyntaxException) { throw IllformedLocaleException(error.message, error.getErrorIndex()) }
            return this
        }
        fun setLocale(locale: ULocale): Builder = mutate { builder.setLocale(locale.base, locale.extensions) }
        fun setLanguageTag(tag: String): Builder {
            val status = ParseStatus(); val parsed = LanguageTag.parse(tag, status)
            if (status.isError()) throw IllformedLocaleException(status.getErrorMessage(), status.getErrorIndex())
            builder.setLanguageTag(parsed); return this
        }
        fun setLanguage(value: String?): Builder = mutate { builder.setLanguage(value) }
        fun setScript(value: String?): Builder = mutate { builder.setScript(value) }
        fun setRegion(value: String?): Builder = mutate { builder.setRegion(value) }
        fun setVariant(value: String?): Builder = mutate { builder.setVariant(value) }
        fun setExtension(key: Char, value: String?): Builder = mutate { builder.setExtension(key, value) }
        fun setUnicodeLocaleKeyword(key: String, value: String?): Builder = mutate { builder.setUnicodeLocaleKeyword(key, value) }
        fun addUnicodeLocaleAttribute(value: String?): Builder = mutate { builder.addUnicodeLocaleAttribute(value) }
        fun removeUnicodeLocaleAttribute(value: String?): Builder = mutate { builder.removeUnicodeLocaleAttribute(value) }
        fun clear(): Builder { builder.clear(); return this }
        fun clearExtensions(): Builder { builder.clearExtensions(); return this }
        fun build(): ULocale = getInstance(builder.getBaseLocale(), builder.getLocaleExtensions())
    }
    @Deprecated("This API is ICU internal only.")
    open class RegionValidateMap {
        protected var map: IntArray? = validRegions.copyOf()
        fun isSet(region: String?): Boolean {
            val index = value(region)
            return index >= 0 && (io.github.heapy.icu4k.impl.checkedArrayElement(map!!, index / 32) and (1 shl (index % 32))) != 0
        }
        fun equals(that: RegionValidateMap?): Boolean {
            val other = that!!
            return map?.contentEquals(other.map) ?: (other.map == null)
        }
        protected open fun value(region: String?): Int {
            val input = region!!
            if (input.length != 2 || input.any { it !in 'a'..'z' && it !in 'A'..'Z' }) return -1
            val lower = io.github.heapy.icu4k.impl.supplementalRegionCase(input, false)
            return (lower[0].code - 'a'.code) * 26 + lower[1].code - 'a'.code
        }
        companion object {
            private val validRegions = intArrayOf(
                0xeedf597c.toInt(), 0xdeddbdef.toInt(), 0x15943f3f.toInt(), 0x0e00d580.toInt(),
                0xb0095c00.toInt(), 0x0015fb9f.toInt(), 0x781c068d.toInt(), 0x0340400f.toInt(),
                0xf42b1d00.toInt(), 0xfd4f8141.toInt(), 0x25d7fffc.toInt(), 0x0100084b.toInt(),
                0x538f3c40.toInt(), 0x40000001.toInt(), 0xfdf15100.toInt(), 0x9fbb7ae7.toInt(),
                0x0410419a.toInt(), 0x00408557.toInt(), 0x00004002.toInt(), 0x00100001.toInt(),
                0x00400408.toInt(), 0x00000001.toInt()
            )
            @Suppress("DEPRECATION")
            var BUILTIN: RegionValidateMap? = RegionValidateMap()
        }
    }
    @Deprecated("This API is ICU internal only.")
    enum class Minimize { FAVOR_SCRIPT, FAVOR_REGION }
    enum class Category { DISPLAY, FORMAT }
    enum class AvailableType { DEFAULT, ONLY_LEGACY_ALIASES, WITH_LEGACY_ALIASES }
    companion object {
        fun getDisplayLanguage(localeID: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayLanguage(ULocale(displayLocaleID!!))
        fun getDisplayLanguage(localeID: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayLanguage(displayLocale)
        fun getDisplayLanguageWithDialect(localeID: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayLanguageWithDialect(ULocale(displayLocaleID!!))
        fun getDisplayLanguageWithDialect(localeID: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayLanguageWithDialect(displayLocale)
        fun getDisplayScript(localeID: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayScript(ULocale(displayLocaleID!!))
        fun getDisplayScript(localeID: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayScript(displayLocale)
    @Deprecated("This API is ICU internal only.")
        fun getDisplayScriptInContext(localeID: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayScriptInContext(ULocale(displayLocaleID!!))
    @Deprecated("This API is ICU internal only.")
        fun getDisplayScriptInContext(localeID: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayScriptInContext(displayLocale)
        fun getDisplayCountry(localeID: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayCountry(ULocale(displayLocaleID!!))
        fun getDisplayCountry(localeID: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayCountry(displayLocale)
        fun getDisplayVariant(localeID: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayVariant(ULocale(displayLocaleID!!))
        fun getDisplayVariant(localeID: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayVariant(displayLocale)
        fun getDisplayName(localeID: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayName(ULocale(displayLocaleID!!))
        fun getDisplayName(localeID: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayName(displayLocale)
        fun getDisplayNameWithDialect(localeID: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayNameWithDialect(ULocale(displayLocaleID!!))
        fun getDisplayNameWithDialect(localeID: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayNameWithDialect(displayLocale)
        fun getDisplayKeyword(keyword: String?): String? = getDisplayKeyword(keyword, getDefault(Category.DISPLAY))
        fun getDisplayKeyword(keyword: String?, displayLocaleID: String?): String? = getDisplayKeyword(keyword, ULocale(displayLocaleID!!))
        fun getDisplayKeyword(keyword: String?, displayLocale: ULocale?): String? = LocaleDisplayNames.getInstance(displayLocale).keyDisplayName(keyword)
        fun getDisplayKeywordValue(localeID: String?, keyword: String?, displayLocaleID: String?): String? = ULocale(localeID!!).getDisplayKeywordValue(keyword, ULocale(displayLocaleID!!))
        fun getDisplayKeywordValue(localeID: String?, keyword: String?, displayLocale: ULocale?): String? = ULocale(localeID!!).getDisplayKeywordValue(keyword, displayLocale)
        @JvmField var ACTUAL_LOCALE: Type? = Type.create()
        @JvmField var VALID_LOCALE: Type? = Type.create()
        @Deprecated("This API is ICU internal only.")
        @Suppress("DEPRECATION")
        fun getRegionForSupplementalData(locale: ULocale?, inferRegion: Boolean): String {
            val loc = locale!!
            getRegionFromKey(loc, "rg")?.let { return it }
            var region = loc.getCountry()
            if (region.isEmpty() && inferRegion) {
                getRegionFromKey(loc, "sd")?.let { return it }
                region = addLikelySubtags(loc).getCountry()
            }
            return region
        }
        @Suppress("DEPRECATION")
        private fun getRegionFromKey(locale: ULocale, key: String): String? {
            val subdivision = locale.getKeywordValue(key) ?: return null
            if (subdivision.length !in 3..6) return null
            val region = io.github.heapy.icu4k.impl.supplementalRegionCase(subdivision.substring(0, 2), true)
            return if (RegionValidateMap.BUILTIN!!.isSet(region)) region else null
        }
        fun createCanonical(localeID: String?): ULocale = ULocale(canonicalize(localeID), false)
        fun createCanonical(locale: ULocale?): ULocale = createCanonical(locale!!.getName())
        fun canonicalize(localeID: String?): String {
            var parser = LocaleIDParser(localeID!!, true)
            val baseName = parser.getBaseName()
            if (localeID.isEmpty()) return ""
            val replacement = canonicalizeMap[baseName]
            if (replacement != null) parser.setBaseName(replacement)
            else if (parser.getLanguage() == "nb" && parser.getVariant() == "NY")
                parser.setBaseName(lscvToID("nn", parser.getScript(), parser.getCountry(), null))
            val name = parser.getName()
            if (name != "c" && name != "en" && name != "en_US" && name !in knownCanonicalized) {
                val alias = LocaleAliasReplacer(parser.getLanguage(), parser.getScript(), parser.getCountry(),
                    AsciiUtil.toLowerString(parser.getVariant()), name.substring(parser.getBaseName().length)).replace()
                if (alias != null) parser = LocaleIDParser(alias)
            }
            return parser.getName()
        }
        private val canonicalizeMap = mapOf(
            "art__LOJBAN" to "jbo",
            "cel__GAULISH" to "cel__GAULISH",
            "de__1901" to "de__1901",
            "de__1906" to "de__1906",
            "en__BOONT" to "en__BOONT",
            "en__SCOUSE" to "en__SCOUSE",
            "hy__AREVELA" to "hy",
            "hy__AREVMDA" to "hyw",
            "sl__ROZAJ" to "sl__ROZAJ",
            "zh__GUOYU" to "zh",
            "zh__HAKKA" to "hak",
            "zh__XIANG" to "hsn",
            "zh_GAN" to "gan",
            "zh_MIN" to "zh__MIN",
            "zh_MIN_NAN" to "nan",
            "zh_WUU" to "wuu",
            "zh_YUE" to "yue"
        )
        private val knownCanonicalized by lazy { setOf(
            "af", "af_ZA", "am", "am_ET", "ar", "ar_001", "as", "as_IN", "az", "az_AZ",
            "be", "be_BY", "bg", "bg_BG", "bn", "bn_IN", "bs", "bs_BA", "ca", "ca_ES",
            "cs", "cs_CZ", "cy", "cy_GB", "da", "da_DK", "de", "de_DE", "el", "el_GR",
            "en", "en_GB", "en_US", "es", "es_419", "es_ES", "et", "et_EE", "eu", "eu_ES",
            "fa", "fa_IR", "fi", "fi_FI", "fil", "fil_PH", "fr", "fr_FR", "ga", "ga_IE",
            "gl", "gl_ES", "gu", "gu_IN", "he", "he_IL", "hi", "hi_IN", "hr", "hr_HR",
            "hu", "hu_HU", "hy", "hy_AM", "id", "id_ID", "is", "is_IS", "it", "it_IT",
            "ja", "ja_JP", "jv", "jv_ID", "ka", "ka_GE", "kk", "kk_KZ", "km", "km_KH",
            "kn", "kn_IN", "ko", "ko_KR", "ky", "ky_KG", "lo", "lo_LA", "lt", "lt_LT",
            "lv", "lv_LV", "mk", "mk_MK", "ml", "ml_IN", "mn", "mn_MN", "mr", "mr_IN",
            "ms", "ms_MY", "my", "my_MM", "nb", "nb_NO", "ne", "ne_NP", "nl", "nl_NL",
            "no", "or", "or_IN", "pa", "pa_IN", "pl", "pl_PL", "ps", "ps_AF", "pt",
            "pt_BR", "pt_PT", "ro", "ro_RO", "ru", "ru_RU", "sd", "sd_IN", "si", "si_LK",
            "sk", "sk_SK", "sl", "sl_SI", "so", "so_SO", "sq", "sq_AL", "sr", "sr_Cyrl_RS",
            "sr_Latn", "sr_RS", "sv", "sv_SE", "sw", "sw_TZ", "ta", "ta_IN", "te", "te_IN",
            "th", "th_TH", "tk", "tk_TM", "tr", "tr_TR", "uk", "uk_UA", "ur", "ur_PK",
            "uz", "uz_UZ", "vi", "vi_VN", "yue", "yue_Hant", "yue_Hant_HK", "yue_HK", "zh", "zh_CN",
            "zh_Hans", "zh_Hans_CN", "zh_Hant", "zh_Hant_TW", "zh_TW", "zu", "zu_ZA"
        ) }
        fun addLikelySubtags(locale: ULocale?): ULocale {
            val loc = locale!!
            val trailing = likelyTrailing(loc.localeID)
            val max = LikelySubtags.INSTANCE.makeMaximizedLsrFrom(ULocale(loc.getLanguage(), loc.getScript(), loc.getCountry()), true)
            return ULocale(likelyTagString(max.language, max.script, max.region, trailing))
        }
        @Suppress("DEPRECATION")
        fun minimizeSubtags(locale: ULocale?): ULocale = minimizeSubtags(locale, Minimize.FAVOR_REGION)
        @Deprecated("This API is ICU internal only.")
        @Suppress("DEPRECATION")
        fun minimizeSubtags(locale: ULocale?, favor: Minimize?): ULocale {
            val loc = locale!!
            val trailing = likelyTrailing(loc.localeID)
            val min = LikelySubtags.INSTANCE.minimizeSubtags(loc.getLanguage(), loc.getScript(), loc.getCountry(), favor)
            return ULocale(likelyTagString(min.language, min.script, min.region, trailing))
        }
        private fun likelyTrailing(id: String): String? {
            val variant = LocaleIDParser(id).getVariant()
            val index = if (variant.isNotEmpty()) id.indexOf(variant).let { if (it > 0) it - 1 else it }
                else id.indexOf('@').let { if (it < 0) id.length else it }
            return if (index < id.length) id.substring(index) else null
        }
        private fun likelyTagString(language: String, script: String, region: String, trailing: String?): String = buildString {
            append(language.ifEmpty { "und" })
            if (script.isNotEmpty()) append('_').append(script)
            if (region.isNotEmpty()) append('_').append(region)
            if (trailing != null && trailing.length > 1) {
                val separators = if (trailing[0] == '_') { if (trailing[1] == '_') 2 else 0 } else 1
                if (region.isNotEmpty()) append(if (separators == 2) trailing.substring(1) else trailing)
                else { if (separators == 1) append('_'); append(trailing) }
            }
        }
        fun getDefault(): ULocale = LocaleDefaults.get()
        fun getDefault(category: Category?): ULocale = LocaleDefaults.get(category!!)
        fun setDefault(locale: ULocale?) = LocaleDefaults.set(locale!!)
        fun setDefault(category: Category?, locale: ULocale?) = LocaleDefaults.set(category, locale!!)

        fun getAvailableLocales(): Array<ULocale> = ICUResourceBundle.getAvailableULocales()!!.copyOf()
        fun getAvailableLocalesByType(type: AvailableType?): Collection<ULocale> {
            if (type == null) throw IllegalArgumentException()
            val locales = if (type == AvailableType.WITH_LEGACY_ALIASES) {
                val result = ArrayList<ULocale>()
                result.addAll(ICUResourceBundle.getAvailableULocales(AvailableType.DEFAULT)!!)
                result.addAll(ICUResourceBundle.getAvailableULocales(AvailableType.ONLY_LEGACY_ALIASES)!!)
                result
            } else ICUResourceBundle.getAvailableULocales(type)!!.asList()
            return localeReadOnlyList(locales)
        }

        const val PRIVATE_USE_EXTENSION = 'x'
        const val UNICODE_LOCALE_EXTENSION = 'u'
        private val nameCache = object : SoftCache<String, String, Unit?>() { override fun createInstance(key: String, data: Unit?): String = LocaleIDParser(key).getName() }
        val ROOT = ULocale("")
        val ENGLISH = ULocale("en"); val FRENCH = ULocale("fr"); val GERMAN = ULocale("de"); val ITALIAN = ULocale("it")
        val JAPANESE = ULocale("ja"); val KOREAN = ULocale("ko"); val CHINESE = ULocale("zh")
        val SIMPLIFIED_CHINESE = ULocale("zh_Hans"); val TRADITIONAL_CHINESE = ULocale("zh_Hant")
        val FRANCE = ULocale("fr_FR"); val GERMANY = ULocale("de_DE"); val ITALY = ULocale("it_IT"); val JAPAN = ULocale("ja_JP"); val KOREA = ULocale("ko_KR")
        val CHINA = ULocale("zh_Hans_CN"); val PRC = CHINA; val TAIWAN = ULocale("zh_Hant_TW")
        val UK = ULocale("en_GB"); val US = ULocale("en_US"); val CANADA = ULocale("en_CA"); val CANADA_FRENCH = ULocale("fr_CA")
        fun getName(localeID: String): String {
            var input = localeID
            if (!localeID.contains('@') && shortestSubtagLength(localeID) == 1) {
                if ('_' in localeID && localeID[1] != '_' && localeID[1] != '-') input = localeID.replace('_', '-')
                input = forLanguageTag(input).getName()
                if (input.isEmpty()) input = localeID
            } else if (localeID.equals("root", ignoreCase = true)) input = ""
            else if (localeID.length >= 3 && localeID.regionMatches(0, "und", 0, 3, ignoreCase = true)) {
                if (localeID.length == 3) input = "" else if (localeID[3] == '-' || localeID[3] == '_') input = localeID.substring(3)
            }
            return nameCache.getInstance(input, null)!!
        }
        private fun shortestSubtagLength(id: String): Int {
            var length = id.length; var reset = true; var count = 0
            for (c in id) if (c != '_' && c != '-') { if (reset) { reset = false; count = 0 }; count++ }
            else { if (count != 0 && count < length) length = count; reset = true }
            return length
        }
        private fun lscvToID(language: String?, script: String?, country: String?, variant: String?): String = buildString {
            if (!language.isNullOrEmpty()) append(language)
            if (!script.isNullOrEmpty()) append('_').append(script)
            if (!country.isNullOrEmpty()) append('_').append(country)
            if (!variant.isNullOrEmpty()) { if (country.isNullOrEmpty()) append('_'); append('_').append(variant) }
        }
        fun getLanguage(id: String): String = LocaleIDParser(id).getLanguage()
        fun getScript(id: String): String = LocaleIDParser(id).getScript()
        fun getCountry(id: String): String = LocaleIDParser(id).getCountry()
        fun getVariant(id: String): String = LocaleIDParser(id).getVariant()
        fun getBaseName(id: String): String = if ('@' !in id) id else LocaleIDParser(id).getBaseName()
        fun getKeywords(id: String): MutableIterator<String>? = LocaleIDParser(id).getKeywords()
        fun getKeywordValue(id: String, key: String?): String? = LocaleIDParser(id).getKeywordValue(key)
        fun setKeywordValue(id: String, key: String?, value: String?): String = LocaleIDParser(id).also { it.setKeywordValue(key, value) }.getName()
        fun getISO3Language(id: String): String = LocaleIDs.getISO3Language(getLanguage(id))
        fun getISO3Country(id: String): String = LocaleIDs.getISO3Country(getCountry(id))
        fun getISOLanguages(): Array<String> = LocaleIDs.getISOLanguages()
        fun getISOCountries(): Array<String> = LocaleIDs.getISOCountries()
        fun getFallback(id: String): String = fallbackString(getName(id))
        private fun fallbackString(id: String): String {
            val extensions = id.indexOf('@').let { if (it < 0) id.length else it }
            var last = id.lastIndexOf('_', extensions)
            if (last < 0) last = 0 else while (last > 0 && id[last - 1] == '_') last--
            return id.substring(0, last) + id.substring(extensions)
        }
        fun toUnicodeLocaleKey(key: String): String? = KeyTypeData.toBcpKey(key) ?: if (UnicodeLocaleExtension.isKey(key)) AsciiUtil.toLowerString(key) else null
        fun toUnicodeLocaleType(key: String, value: String): String? = KeyTypeData.toBcpType(key, value) ?: if (UnicodeLocaleExtension.isType(value)) AsciiUtil.toLowerString(value) else null
        fun toLegacyKey(key: String): String? = KeyTypeData.toLegacyKey(key) ?: if (key.isNotEmpty() && AsciiUtil.isAlphaNumericString(key)) AsciiUtil.toLowerString(key) else null
        fun toLegacyType(key: String, value: String): String? = KeyTypeData.toLegacyType(key, value) ?: if (value.split('_', '/', '-').all { it.isNotEmpty() && AsciiUtil.isAlphaNumericString(it) }) AsciiUtil.toLowerString(value) else null
        fun forLanguageTag(tag: String): ULocale {
            val builder = InternalLocaleBuilder().setLanguageTag(LanguageTag.parse(tag))
            return getInstance(builder.getBaseLocale(), builder.getLocaleExtensions())
        }
        private fun getInstance(base: BaseLocale, extensions: LocaleExtensions): ULocale {
            var id = lscvToID(base.getLanguage(), base.getScript(), base.getRegion(), base.getVariant())
            val keywords = HashMap<String, String>()
            for (key in extensions.getKeys()) {
                val extension = extensions.getExtension(key)!!
                if (extension is UnicodeLocaleExtension) {
                    for (unicodeKey in extension.getUnicodeLocaleKeys()) {
                        val type = extension.getUnicodeLocaleType(unicodeKey)!!
                        val legacyKey = toLegacyKey(unicodeKey)!!
                        val legacyType = toLegacyType(unicodeKey, type.ifEmpty { "yes" })!!
                        if (legacyKey == "va" && legacyType == "posix" && base.getVariant().isEmpty()) id += "_POSIX"
                        else keywords[legacyKey] = legacyType
                    }
                    if (extension.getUnicodeLocaleAttributes().isNotEmpty()) keywords["attribute"] = extension.getUnicodeLocaleAttributes().joinToString("-")
                } else keywords[key.toString()] = extension.getValue()!!
            }
            if (keywords.isNotEmpty()) id += keywords.keys.sorted().joinToString(";", "@") { "$it=${keywords.getValue(it)}" }
            return ULocale(id)
        }
    }
}
