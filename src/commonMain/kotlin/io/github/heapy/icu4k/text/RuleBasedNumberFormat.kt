// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// Port of ICU4J 78.3 RuleBasedNumberFormat.
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.math.BigDecimal as IcuDecimal
import io.github.heapy.icu4k.util.BigDecimal
import io.github.heapy.icu4k.util.BigInteger
import io.github.heapy.icu4k.util.ULocale
import kotlin.jvm.Transient

/** Formats and parses numbers with named rule sets or pinned ICU locale rules. */
open class RuleBasedNumberFormat private constructor() : NumberFormat() {
    @Transient internal var runtime: RbnfFormatterCore? = null
    private var locale: ULocale? = null
    private var roundingMode = IcuDecimal.ROUND_UNNECESSARY
    private val core: RbnfFormatterCore get() = runtime!!

    constructor(description: String?) : this(description, null, ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(description: String?, localizations: Array<Array<String>>?) : this(description, localizations, ULocale.getDefault(ULocale.Category.FORMAT))
    constructor(description: String?, locale: ULocale?) : this(description, null, locale)
    constructor(description: String?, localizations: Array<Array<String>>?, locale: ULocale?) : this() {
        this.locale = locale
        runtime = RbnfFormatterCore.fromDescription(description, localizations, locale,
            scannerProviderFactory = ::createRbnfScannerProvider, postProcessorFactory = ::createRbnfPostProcessor)
        bindOwner()
    }
    constructor(locale: ULocale?, format: Int) : this() {
        this.locale = locale
        runtime = RbnfFormatterCore.fromLocale(locale, format, ::createRbnfScannerProvider,
            onBundleLocale = { setLocale(it, it) }, postProcessorFactory = ::createRbnfPostProcessor)
        bindOwner()
    }
    constructor(format: Int) : this(ULocale.getDefault(ULocale.Category.FORMAT), format)

    private fun bindOwner() {
        core.facadeOwner = this
        core.roundingModeReader = { getRoundingMode() }
        core.lenientParseReader = { lenientParseEnabled() }
        core.scannerProviderReader = { getLenientScannerProvider() }
        core.scannerProviderPublisher = { setLenientScannerProvider(it) }
    }
    open fun getRuleSetNames(): Array<String> = core.getRuleSetNames()
    open fun getRuleSetDisplayNameLocales(): Array<ULocale>? = core.getRuleSetDisplayNameLocales()
    open fun getRuleSetDisplayNames(locale: ULocale?): Array<String> {
        core.localizedDisplayNames(locale)?.let { return it }
        return getRuleSetNames().also { names ->
            for (i in names.indices) {
                val name: String? = names[i]
                if (name!!.isEmpty()) throw IndexOutOfBoundsException()
                names[i] = name.substring(1)
            }
        }
    }
    open fun getRuleSetDisplayNames(): Array<String> = getRuleSetDisplayNames(ULocale.getDefault(ULocale.Category.DISPLAY))
    open fun getRuleSetDisplayName(name: String?, locale: ULocale?): String? = core.getRuleSetDisplayName(name, locale)
    open fun getRuleSetDisplayName(name: String?): String? = getRuleSetDisplayName(name, ULocale.getDefault(ULocale.Category.DISPLAY))
    open fun setDefaultRuleSet(name: String?) = core.setDefaultRuleSet(name)
    open fun getDefaultRuleSetName(): String = core.getDefaultRuleSetName()
    open fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?) = core.setDecimalFormatSymbols(symbols)
    open fun setLenientParseMode(enabled: Boolean) = core.setLenientParseMode(enabled)
    open fun lenientParseEnabled(): Boolean = core.rawLenientParseEnabled()
    open fun setLenientScannerProvider(provider: RbnfLenientScannerProvider?) = core.setLenientScannerProvider(provider)
    open fun getLenientScannerProvider(): RbnfLenientScannerProvider? = core.getLenientScannerProvider()
    override fun setRoundingMode(value: Int) { core.setRoundingMode(value); roundingMode = value }
    override fun getRoundingMode(): Int = roundingMode
    override fun setContext(value: DisplayContext?) = core.setContext(value) { super.setContext(it) }
    private fun adjust(text: String): String = core.adjustForContext(text) { getContext(DisplayContext.Type.CAPITALIZATION) }!!
    open fun format(number: Long, ruleSet: String?): String = adjust(core.format(number, ruleSet))
    open fun format(number: Double, ruleSet: String?): String = adjust(core.format(number, ruleSet) { getMaximumFractionDigits() })
    override fun format(number: Long, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        if (output!!.isEmpty()) output.append(adjust(core.format(number))) else output.append(core.format(number))
        return output
    }
    override fun format(number: Double, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        if (output!!.isEmpty()) output.append(adjust(core.format(number) { getMaximumFractionDigits() }))
        else output.append(core.format(number) { getMaximumFractionDigits() })
        return output
    }
    override fun format(number: BigInteger?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = format(IcuDecimal(number), output, position)
    override fun format(number: BigDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder = format(IcuDecimal(number), output, position)
    override fun format(number: IcuDecimal?, output: StringBuilder?, position: FormatFieldPosition?): StringBuilder {
        if (MIN_VALUE.compareTo(number!!) > 0 || MAX_VALUE.compareTo(number) < 0) return core.getDecimalFormat().format(number, output, position)
        if (number.scale() == 0) return format(number.toLong(), output, position)
        return format(number.toDouble(), output, position)
    }
    override fun parse(text: String?, position: ParsePosition?): Number? = core.parse(text, position)
    override fun equals(other: Any?): Boolean = other is RuleBasedNumberFormat && core.sameRulesAs(other.core)
    override fun hashCode(): Int = super.hashCode()
    override fun toString(): String = core.toString()
    public override fun clone(): RuleBasedNumberFormat {
        val result = (cloneHost() as RuleBasedNumberFormat?) ?: RuleBasedNumberFormat().also {
            copyNumberFormatTo(it); it.locale = locale; it.roundingMode = roundingMode
        }
        result.runtime = core.copy()
        result.bindOwner()
        return result
    }
    private fun writeObject(output: PluralObjectOutputStream) = writeRbnf(output, this, locale, roundingMode)
    private fun readObject(input: PluralObjectInputStream) = readRbnf(input, this)

    /** Source readObject restores raw mode without validation and keeps the temporary graph owner. */
    internal fun restoreFromStream(description: String, streamLocale: ULocale?, mode: Int) {
        roundingMode = mode
        val temporary = RuleBasedNumberFormat(description, streamLocale)
        runtime = temporary.core.copy()
        core.restoreRoundingMode(mode)
        locale = temporary.locale
        bindOwner()
    }
    companion object {
        const val SPELLOUT = 1
        const val ORDINAL = 2
        @Deprecated("ICU 78") const val DURATION = 3
        const val NUMBERING_SYSTEM = 4
        private const val serialVersionUID = -7664252765575395068L
        // Virtual fields reproduce the source stream descriptor; custom data does not write defaults.
        private val serialPersistentFields: Array<RbnfObjectStreamField> = rbnfSerializationFields()
        private val MAX_VALUE = IcuDecimal.valueOf(Long.MAX_VALUE)
        private val MIN_VALUE = IcuDecimal.valueOf(Long.MIN_VALUE)
    }
}
internal expect class RbnfObjectStreamField
internal expect fun rbnfSerializationFields(): Array<RbnfObjectStreamField>
internal expect fun createRbnfScannerProvider(): RbnfLenientScannerProvider
internal expect fun createRbnfPostProcessor(name: String): RbnfCorePostProcessor
internal expect fun writeRbnf(output: PluralObjectOutputStream, owner: RuleBasedNumberFormat, locale: ULocale?, mode: Int)
internal expect fun readRbnf(input: PluralObjectInputStream, owner: RuleBasedNumberFormat)
