// © 2017 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl.number

import io.github.heapy.icu4k.impl.FormattedStringBuilder
import io.github.heapy.icu4k.impl.StandardPlural
import io.github.heapy.icu4k.impl.number.Modifier.Signum
import io.github.heapy.icu4k.number.SignDisplay
import io.github.heapy.icu4k.number.UnitWidth
import io.github.heapy.icu4k.text.DecimalFormatSymbols
import io.github.heapy.icu4k.text.FormatField
import io.github.heapy.icu4k.text.NumberFormatField
import io.github.heapy.icu4k.text.PluralRules
import io.github.heapy.icu4k.util.Currency
import kotlin.jvm.JvmField

/** Quantity-dependent affix rendering and its precomputed immutable modifier store. */
open class MutablePatternModifier(@JvmField internal val isStrong: Boolean) : Modifier, AffixUtils.SymbolProvider, MicroPropsGenerator {
    @JvmField internal var patternInfo: AffixPatternProvider? = null
    @JvmField internal var field: NumberFormatField? = null
    @JvmField internal var signDisplay: SignDisplay? = null
    @JvmField internal var perMilleReplacesPercent = false
    @JvmField internal var approximately = false
    @JvmField internal var symbols: DecimalFormatSymbols? = null
    @JvmField internal var unitWidth: UnitWidth? = null
    @JvmField internal var currency: Currency? = null
    @JvmField internal var rules: PluralRules? = null
    @JvmField internal var signum: Signum? = null
    @JvmField internal var plural: StandardPlural? = null
    @JvmField internal var parent: MicroPropsGenerator? = null
    @JvmField internal var currentAffix: StringBuilder? = null

    open fun setPatternInfo(patternInfo: AffixPatternProvider?, field: NumberFormatField?) {
        this.patternInfo = patternInfo; this.field = field
    }
    open fun setPatternAttributes(signDisplay: SignDisplay?, perMille: Boolean, approximately: Boolean) {
        this.signDisplay = signDisplay; perMilleReplacesPercent = perMille; this.approximately = approximately
    }
    open fun setSymbols(symbols: DecimalFormatSymbols?, currency: Currency?, unitWidth: UnitWidth?, rules: PluralRules?) {
        assertMutablePatternModifier { (rules != null) == needsPlurals() }
        this.symbols = symbols; this.currency = currency; this.unitWidth = unitWidth; this.rules = rules
    }
    open fun setNumberProperties(signum: Signum?, plural: StandardPlural?) {
        assertMutablePatternModifier { (plural != null) == needsPlurals() }
        this.signum = signum; this.plural = plural
    }
    open fun needsPlurals(): Boolean = patternInfo!!.containsSymbolType(AffixUtils.TYPE_CURRENCY_TRIPLE)

    open fun createImmutable(): ImmutablePatternModifier {
        val a = FormattedStringBuilder(); val b = FormattedStringBuilder()
        if (needsPlurals()) {
            val store = AdoptingModifierStore()
            for (plural in StandardPlural.VALUES) {
                for (sign in listOf(Signum.POS, Signum.POS_ZERO, Signum.NEG_ZERO, Signum.NEG)) {
                    setNumberProperties(sign, plural)
                    store.setModifier(sign, plural, createConstantModifier(a, b))
                }
            }
            store.freeze()
            return ImmutablePatternModifier(store, rules)
        }
        setNumberProperties(Signum.POS, null); val positive = createConstantModifier(a, b)
        setNumberProperties(Signum.POS_ZERO, null); val posZero = createConstantModifier(a, b)
        setNumberProperties(Signum.NEG_ZERO, null); val negZero = createConstantModifier(a, b)
        setNumberProperties(Signum.NEG, null); val negative = createConstantModifier(a, b)
        return ImmutablePatternModifier(AdoptingModifierStore(positive, posZero, negZero, negative), null)
    }
    private fun createConstantModifier(a: FormattedStringBuilder, b: FormattedStringBuilder): ConstantMultiFieldModifier {
        insertPrefix(a.clear(), 0); insertSuffix(b.clear(), 0)
        return if (patternInfo!!.hasCurrencySign()) CurrencySpacingEnabledModifier(a, b, !patternInfo!!.hasBody(), isStrong, symbols)
            else ConstantMultiFieldModifier(a, b, !patternInfo!!.hasBody(), isStrong)
    }

    open class ImmutablePatternModifier internal constructor(@JvmField internal val pm: AdoptingModifierStore,
        @JvmField internal val rules: PluralRules?) : MicroPropsGenerator {
        @JvmField internal var parent: MicroPropsGenerator? = null
        open fun addToChain(parent: MicroPropsGenerator?): ImmutablePatternModifier { this.parent = parent; return this }
        override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
            val micros = parent!!.processQuantity(quantity)!!
            if (micros.rounder != null) micros.rounder!!.apply(quantity)
            if (micros.modMiddle != null) return micros
            applyToMicros(micros, quantity)
            return micros
        }
        open fun applyToMicros(micros: MicroProps?, quantity: DecimalQuantity?) {
            if (rules == null) {
                // Java evaluates the RHS before a null field-assignment receiver fails.
                val modifier = pm.getModifierWithoutPlural(quantity!!.signum())
                micros!!.modMiddle = modifier
            } else {
                val pluralForm = RoundingUtils.getPluralSafe(micros!!.rounder, rules, quantity)
                val modifier = pm.getModifier(quantity!!.signum(), pluralForm)
                micros.modMiddle = modifier
            }
        }
    }
    open fun addToChain(parent: MicroPropsGenerator?): MicroPropsGenerator { this.parent = parent; return this }
    override fun processQuantity(quantity: DecimalQuantity?): MicroProps {
        val micros = parent!!.processQuantity(quantity)!!
        if (micros.rounder != null) micros.rounder!!.apply(quantity)
        if (micros.modMiddle != null) return micros
        if (needsPlurals()) {
            val pluralForm = RoundingUtils.getPluralSafe(micros.rounder, rules, quantity)
            setNumberProperties(quantity!!.signum(), pluralForm)
        } else setNumberProperties(quantity!!.signum(), null)
        micros.modMiddle = this
        return micros
    }
    override fun apply(output: FormattedStringBuilder?, leftIndex: Int, rightIndex: Int): Int {
        val prefixLen = insertPrefix(output, leftIndex)
        val suffixLen = insertSuffix(output, rightIndex + prefixLen)
        val overwriteLen = if (!patternInfo!!.hasBody()) output!!.splice(leftIndex + prefixLen, rightIndex + prefixLen, "", 0, 0, null) else 0
        CurrencySpacingEnabledModifier.applyCurrencySpacing(output, leftIndex, prefixLen,
            rightIndex + prefixLen + overwriteLen, suffixLen, symbols)
        // Source excludes currency-spacing insertions from its reported length.
        return prefixLen + overwriteLen + suffixLen
    }
    override fun getPrefixLength(): Int { prepareAffix(true); return AffixUtils.unescapedCount(currentAffix, true, this) }
    override fun getCodePointCount(): Int {
        prepareAffix(true); val prefix = AffixUtils.unescapedCount(currentAffix, false, this)
        prepareAffix(false); return prefix + AffixUtils.unescapedCount(currentAffix, false, this)
    }
    override fun isStrong(): Boolean = isStrong
    override fun containsField(field: FormatField?): Boolean { assertMutablePatternModifier { false }; return false }
    override fun getParameters(): Modifier.Parameters? { assertMutablePatternModifier { false }; return null }
    override fun strictEquals(other: Modifier?): Boolean { assertMutablePatternModifier { false }; return false }
    private fun insertPrefix(output: FormattedStringBuilder?, position: Int): Int {
        prepareAffix(true); return AffixUtils.unescape(currentAffix, output, position, this, field)
    }
    private fun insertSuffix(output: FormattedStringBuilder?, position: Int): Int {
        prepareAffix(false); return AffixUtils.unescape(currentAffix, output, position, this, field)
    }
    private fun prepareAffix(isPrefix: Boolean) {
        if (currentAffix == null) currentAffix = StringBuilder()
        PatternStringUtils.patternInfoToStringBuilder(patternInfo, isPrefix,
            PatternStringUtils.resolveSignDisplay(signDisplay, signum), approximately, plural, perMilleReplacesPercent, currentAffix)
    }
    override fun getSymbol(type: Int): CharSequence? = when (type) {
        AffixUtils.TYPE_MINUS_SIGN -> symbols!!.getMinusSignString()
        AffixUtils.TYPE_PLUS_SIGN -> symbols!!.getPlusSignString()
        AffixUtils.TYPE_APPROXIMATELY_SIGN -> symbols!!.getApproximatelySignString()
        AffixUtils.TYPE_PERCENT -> symbols!!.getPercentString()
        AffixUtils.TYPE_PERMILLE -> symbols!!.getPerMillString()
        AffixUtils.TYPE_CURRENCY_SINGLE -> getCurrencySymbolForUnitWidth()
        AffixUtils.TYPE_CURRENCY_DOUBLE -> currency!!.getCurrencyCode()
        AffixUtils.TYPE_CURRENCY_TRIPLE -> {
            assertMutablePatternModifier { plural != null }
            val locale = symbols!!.getULocale()
            val keyword = plural!!.getKeyword()
            currency!!.getName(locale, Currency.PLURAL_LONG_NAME, keyword, null)
        }
        AffixUtils.TYPE_CURRENCY_QUAD -> "\uFFFD"
        AffixUtils.TYPE_CURRENCY_QUINT -> {
            val locale = symbols!!.getULocale()
            currency!!.getName(locale, Currency.NARROW_SYMBOL_NAME, null)
        }
        else -> throw AssertionError()
    }
    open fun getCurrencySymbolForUnitWidth(): String? {
        if (unitWidth == UnitWidth.ISO_CODE) return currency!!.getCurrencyCode()
        if (unitWidth == UnitWidth.HIDDEN) return ""
        val selector = when (unitWidth!!) {
            UnitWidth.SHORT -> Currency.SYMBOL_NAME
            UnitWidth.NARROW -> Currency.NARROW_SYMBOL_NAME
            UnitWidth.FORMAL -> Currency.FORMAL_SYMBOL_NAME
            UnitWidth.VARIANT -> Currency.VARIANT_SYMBOL_NAME
            else -> throw AssertionError()
        }
        val locale = symbols!!.getULocale()
        return currency!!.getName(locale, selector, null)
    }
}
internal expect fun assertMutablePatternModifier(condition: () -> Boolean)
