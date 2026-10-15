// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.Transient

/** Locale-sensitive plural messages, following ICU4J's PluralFormat contract. */
open class PluralFormat private constructor(@Suppress("UNUSED_PARAMETER") empty: Unit) : UFormat() {
    private var ulocale: ULocale? = null
    private var pluralRules: PluralRules? = null
    private var pattern: String? = null
    private var parsedValues: Map<String, String>? = null
    private var numberFormat: NumberFormat? = null
    @Transient private var runtime: PluralFormatCore? = null
    private val core: PluralFormatCore get() = runtime!!

    constructor() : this(Unit) { init(null, PluralRules.PluralType.CARDINAL, ULocale.getDefault(ULocale.Category.FORMAT), null) }
    constructor(locale: ULocale?) : this(Unit) { init(null, PluralRules.PluralType.CARDINAL, locale, null) }
    constructor(rules: PluralRules?) : this(Unit) { init(rules, PluralRules.PluralType.CARDINAL, ULocale.getDefault(ULocale.Category.FORMAT), null) }
    constructor(locale: ULocale?, rules: PluralRules?) : this(Unit) { init(rules, PluralRules.PluralType.CARDINAL, locale, null) }
    constructor(locale: ULocale?, type: PluralRules.PluralType?) : this(Unit) { init(null, type, locale, null) }
    constructor(pattern: String?) : this() { applyPattern(pattern) }
    constructor(locale: ULocale?, pattern: String?) : this(locale) { applyPattern(pattern) }
    constructor(rules: PluralRules?, pattern: String?) : this(rules) { applyPattern(pattern) }
    constructor(locale: ULocale?, rules: PluralRules?, pattern: String?) : this(locale, rules) { applyPattern(pattern) }
    constructor(locale: ULocale?, type: PluralRules.PluralType?, pattern: String?) : this(locale, type) { applyPattern(pattern) }
    internal constructor(locale: ULocale?, type: PluralRules.PluralType?, pattern: String?, format: NumberFormat?) : this(Unit) {
        init(null, type, locale, format)
        applyPattern(pattern)
    }
    private fun init(rules: PluralRules?, type: PluralRules.PluralType?, locale: ULocale?, format: NumberFormat?) {
        ulocale = locale
        pluralRules = rules ?: PluralRules.forLocale(ulocale, type)
        if (runtime == null) runtime = PluralFormatCore(pluralRules, numberFormat)
        core.rules = pluralRules
        pattern = null
        core.resetPattern()
        numberFormat = format ?: NumberFormat.getInstance(ulocale)
        core.numberFormat = numberFormat
    }
    open fun applyPattern(pattern: String?) {
        try { core.applyPattern(pattern) } finally { this.pattern = core.pattern }
    }
    open fun toPattern(): String? = pattern
    fun format(number: Double): String = core.format(number)
    override fun format(input: Any?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        val formatted = core.formatAny(input)
        return output!!.append(formatted)
    }
    open fun parse(text: String?, position: ParsePosition?): Number? = throw UnsupportedOperationException()
    override fun parseObject(source: String?, position: ParsePosition?): Any? = throw UnsupportedOperationException()
    internal fun parseType(source: String?, scanner: RbnfLenientScanner?, position: FormatFieldPosition?): String? = core.parseType(source, scanner, position)
    @Deprecated("Create a formatter with the desired locale instead.")
    open fun setLocale(locale: ULocale?) { init(null, PluralRules.PluralType.CARDINAL, locale ?: ULocale.getDefault(ULocale.Category.FORMAT), null) }
    open fun setNumberFormat(format: NumberFormat?) { numberFormat = format; core.numberFormat = format }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as PluralFormat
        return ulocale == other.ulocale && pluralRules == other.pluralRules && core.msgPattern == other.core.msgPattern && numberFormat == other.numberFormat
    }
    open fun equals(other: PluralFormat?): Boolean = equals(other as Any?)
    override fun hashCode(): Int = pluralRules!!.hashCode() xor parsedValues!!.hashCode()
    override fun toString(): String = "locale=$ulocale, rules='$pluralRules', pattern='$pattern', format='$numberFormat'"
    public override fun clone(): PluralFormat {
        val result = (cloneHost() as PluralFormat?) ?: PluralFormat(Unit).also {
            copyLocaleTo(it)
            it.ulocale = ulocale; it.pluralRules = pluralRules; it.pattern = pattern
            it.parsedValues = parsedValues; it.numberFormat = numberFormat
        }
        result.runtime = core.copy()
        return result
    }
    private fun readObject(input: PluralObjectInputStream) {
        pluralFormatReadFields(input)
        runtime = PluralFormatCore(pluralRules, numberFormat)
        parsedValues = null
        if (pattern != null) applyPattern(pattern)
    }
    private companion object { private const val serialVersionUID: Long = 1L }
}
internal expect fun pluralFormatReadFields(input: PluralObjectInputStream)
