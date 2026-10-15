// © 2016 and later: Unicode, Inc. and others.
// SPDX-License-Identifier: Unicode-3.0
// RuleBasedNumberFormat initialization and execution state from ICU4J 78.3.
@file:Suppress("DEPRECATION")
package io.github.heapy.icu4k.text

import io.github.heapy.icu4k.impl.PatternProps
import io.github.heapy.icu4k.impl.ICUResourceLoader
import io.github.heapy.icu4k.impl.defaultICUResourceLoader
import io.github.heapy.icu4k.impl.number.javaCompatibleDoubleString
import io.github.heapy.icu4k.math.BigDecimal
import io.github.heapy.icu4k.util.ULocale

internal interface RbnfCorePostProcessor {
    fun init(formatter: RbnfFormatterCore, rules: String)
    fun process(output: StringBuilder, ruleSet: RbnfRuleSetCore?)
}

/**
 * Mutable graph state behind RuleBasedNumberFormat. The collation-backed scanner
 * is discovered separately from the optional collate module.
 */
internal class RbnfFormatterCore private constructor(
    val locale: ULocale?,
    private val scannerProviderFactory: () -> RbnfLenientScannerProvider,
    private val postProcessorFactory: (String) -> RbnfCorePostProcessor,
) : RbnfSubstitutionOwner {
    private var ruleSets: Array<RbnfRuleSetCore?>? = null
    private var ruleSetsMap: MutableMap<String, RbnfRuleSetCore>? = null
    private var defaultRuleSet: RbnfRuleSetCore? = null
    private var roundingMode = BigDecimal.ROUND_UNNECESSARY
    private var scannerProvider: RbnfLenientScannerProvider? = null
    private var lookedForScanner = false
    private var decimalFormatSymbols: DecimalFormatSymbols? = null
    private var decimalFormat: DecimalFormat? = null
    private var defaultInfinityRule: RbnfRuleCore? = null
    private var defaultNaNRule: RbnfRuleCore? = null
    private var lenientParse = false
    private var lenientParseRules: String? = null
    private var postProcessRules: String? = null
    private var postProcessor: RbnfCorePostProcessor? = null
    private var ruleSetDisplayNames: Map<String?, Array<String>>? = null
    private var publicRuleSetNames: Array<String>? = null
    private var capitalization = RbnfCapitalizationCore(locale)
    internal var facadeOwner: RuleBasedNumberFormat? = null

    /** Allows the eventual facade's virtual rounding-mode query without changing stored mode. */
    var roundingModeReader: () -> Int = { roundingMode }
    var lenientParseReader: () -> Boolean = { lenientParse }
    internal fun rawLenientParseEnabled(): Boolean = lenientParse
    var scannerProviderReader: () -> RbnfLenientScannerProvider? = { getLenientScannerProvider() }
    var scannerProviderPublisher: (RbnfLenientScannerProvider?) -> Unit = { setLenientScannerProvider(it) }
    internal fun restoreRoundingMode(mode: Int) { roundingMode = mode }

    private fun init(description: String?, localizations: Array<Array<String>>?) {
        initLocalizations(localizations)
        val buffer = stripWhitespace(description)
        lenientParseRules = extractSpecial(buffer, "%%lenient-parse:")
        postProcessRules = extractSpecial(buffer, "%%post-process:")
        var count = 1
        var p = 0
        while (true) {
            p = buffer.indexOf(";%", p)
            if (p == -1) break
            ++count
            p += 2
        }
        ruleSets = arrayOfNulls(count)
        ruleSetsMap = HashMap(count * 2 + 1)
        defaultRuleSet = null
        var publicCount = 0
        val descriptions = Array(count) { "" }
        var current = 0
        var start = 0
        while (current < count) {
            p = buffer.indexOf(";%", start)
            if (p < 0) p = buffer.length - 1
            descriptions[current] = buffer.toString().formatterSlice(start, p + 1)
            val set = RbnfRuleSetCore(this, descriptions, current)
            ruleSets!![current] = set
            val name = set.getName()
            ruleSetsMap!![name] = set
            if (!name.startsWith("%%")) {
                ++publicCount
                // Source precedence is intentional: ordinal/duration overwrite an earlier choice.
                if ((defaultRuleSet == null && name == "%spellout-numbering") || name == "%digits-ordinal" || name == "%duration") {
                    defaultRuleSet = set
                }
            }
            ++current
            start = p + 1
        }
        if (defaultRuleSet == null) {
            for (i in count - 1 downTo 0) if (!setAt(i).getName().startsWith("%%")) { defaultRuleSet = setAt(i); break }
        }
        if (defaultRuleSet == null) defaultRuleSet = setAt(count - 1)
        for (i in 0..<count) setAt(i).parseRules(descriptions[i])
        val publicNames = arrayOfNulls<String>(publicCount)
        publicCount = 0
        for (i in count - 1 downTo 0) if (!setAt(i).getName().startsWith("%%")) publicNames[publicCount++] = setAt(i).getName()
        val localizedNames = publicRuleSetNames
        if (localizedNames != null) {
            for (name in localizedNames) {
                val nullableName: String? = name
                if (publicNames.none { nullableName!!.equals(it) }) throw IllegalArgumentException("did not find public rule set: $name")
            }
            defaultRuleSet = findRuleSet(arrayAt(localizedNames, 0))
        } else {
            @Suppress("UNCHECKED_CAST")
            publicRuleSetNames = publicNames as Array<String>
        }
    }
    private fun initLocalizations(localizations: Array<Array<String>>?) {
        if (localizations != null) {
            val first: Array<String>? = arrayAt(localizations, 0)
            publicRuleSetNames = first!!.copyOf()
            val map = HashMap<String?, Array<String>>()
            for (i in 1..<localizations.size) {
                val nullableRow: Array<String>? = localizations[i]
                val row = nullableRow!!
                val loc: String? = arrayAt(row, 0)
                val names = Array(row.size - 1) { "" }
                if (names.size != publicRuleSetNames!!.size) {
                    throw IllegalArgumentException("public name length: ${publicRuleSetNames!!.size} != localized names[$i] length: ${names.size}")
                }
                row.copyInto(names, 0, 1, row.size)
                map[loc] = names
            }
            if (map.isNotEmpty()) ruleSetDisplayNames = map
        }
    }
    private fun setAt(index: Int): RbnfRuleSetCore = arrayAt(ruleSets!!, index)!!
    fun getRuleSetNames(): Array<String> = publicRuleSetNames!!.copyOf()
    fun getRuleSetDisplayNameLocales(): Array<ULocale>? {
        val map = ruleSetDisplayNames ?: return null
        val locales = map.keys.toMutableList()
        locales.sortWith(Comparator { a, b -> a!!.compareTo(b!!, ignoreCase = true) })
        return Array(locales.size) { ULocale(locales[it]!!) }
    }
    private fun getNameListForLocale(loc: ULocale?): Array<String>? {
        val map = ruleSetDisplayNames
        if (loc != null && map != null) {
            val locales = arrayOf(loc.getBaseName(), ULocale.getDefault(ULocale.Category.DISPLAY).getBaseName())
            for (candidate in locales) {
                var name = candidate
                while (name.isNotEmpty()) {
                    val names = map[name]
                    if (names != null) return names
                    name = ULocale.getFallback(name)
                }
            }
        }
        return null
    }
    internal fun localizedDisplayNames(loc: ULocale?): Array<String>? = getNameListForLocale(loc)?.copyOf()
    fun getRuleSetDisplayNames(loc: ULocale?): Array<String> {
        val names = getNameListForLocale(loc)
        if (names != null) return names.copyOf()
        return getRuleSetNames().also { result ->
            for (i in result.indices) { val name: String? = result[i]; result[i] = name!!.formatterSlice(1) }
        }
    }
    fun getRuleSetDisplayName(ruleSetName: String?, loc: ULocale?): String {
        val names = publicRuleSetNames!!
        for (i in names.indices) {
            val name: String? = names[i]
            if (name!!.equals(ruleSetName)) {
                val display = getNameListForLocale(loc)
                if (display != null) return arrayAt(display, i)
                return name.formatterSlice(1)
            }
        }
        throw IllegalArgumentException("unrecognized rule set name: $ruleSetName")
    }
    fun setDefaultRuleSet(name: String?) {
        if (name == null) {
            if (publicRuleSetNames!!.isNotEmpty()) defaultRuleSet = findRuleSet(publicRuleSetNames!![0])
            else {
                defaultRuleSet = null
                for (i in ruleSets!!.size - 1 downTo 0) {
                    val candidate = setAt(i).getName()
                    if (candidate == "%spellout-numbering" || candidate == "%digits-ordinal" || candidate == "%duration") {
                        defaultRuleSet = setAt(i)
                        return
                    }
                }
                for (i in ruleSets!!.size - 1 downTo 0) if (setAt(i).isPublic()) { defaultRuleSet = setAt(i); break }
            }
        } else if (name.startsWith("%%")) throw IllegalArgumentException("cannot use private rule set: $name")
        else defaultRuleSet = findRuleSet(name)
    }
    fun getDefaultRuleSetName(): String = if (defaultRuleSet != null && defaultRuleSet!!.isPublic()) defaultRuleSet!!.getName() else ""
    override fun getDefaultRuleSet(): RbnfRuleSetCore? = defaultRuleSet
    override fun findRuleSet(name: String): RbnfRuleSetCore = ruleSetsMap!![name] ?: throw IllegalArgumentException("No rule set named $name")
    override fun makeRules(description: String, ruleSet: RbnfRuleSetCore, predecessor: RbnfRule?, rules: MutableList<RbnfRule>) =
        RbnfRuleCore.makeRules(description, ruleSet, predecessor as RbnfRuleCore?, this, rules)
    override fun makeSubstitution(position: Int, rule: RbnfRuleCore, predecessor: RbnfRuleCore?, ruleSet: RbnfRuleSetCore, token: String) =
        RbnfSubstitutionCore.makeSubstitution(position, rule, predecessor, ruleSet, this, token)
    override fun createPluralFormat(type: PluralRules.PluralType, pattern: String) =
        PluralFormatCore(PluralRules.forLocale(locale, type), getDecimalFormat()).also { it.applyPattern(pattern) }
    override fun getDecimalFormatSymbols(): DecimalFormatSymbols {
        if (decimalFormatSymbols == null) decimalFormatSymbols = DecimalFormatSymbols(locale)
        return decimalFormatSymbols!!
    }
    override fun getDecimalFormat(): DecimalFormat {
        if (decimalFormat == null) {
            val pattern = NumberFormat.getPatternForStyle(locale, NumberFormat.NUMBERSTYLE)
            decimalFormat = DecimalFormat(pattern, getDecimalFormatSymbols())
        }
        return decimalFormat!!
    }
    override fun getDefaultInfinityRule(): RbnfRuleCore {
        if (defaultInfinityRule == null) defaultInfinityRule = RbnfRuleCore(this, "Inf: ${getDecimalFormatSymbols().getInfinity()}")
        return defaultInfinityRule!!
    }
    override fun getDefaultNaNRule(): RbnfRuleCore {
        if (defaultNaNRule == null) defaultNaNRule = RbnfRuleCore(this, "NaN: ${getDecimalFormatSymbols().getNaN()}")
        return defaultNaNRule!!
    }
    fun setDecimalFormatSymbols(symbols: DecimalFormatSymbols?) {
        if (symbols != null) {
            decimalFormatSymbols = symbols.clone()
            decimalFormat?.setDecimalFormatSymbols(decimalFormatSymbols)
            if (defaultInfinityRule != null) { defaultInfinityRule = null; getDefaultInfinityRule() }
            if (defaultNaNRule != null) { defaultNaNRule = null; getDefaultNaNRule() }
            for (i in ruleSets!!.indices) setAt(i).setDecimalFormatSymbols(decimalFormatSymbols)
        }
    }
    fun setRoundingMode(mode: Int) {
        if (mode < BigDecimal.ROUND_UP || mode > BigDecimal.ROUND_UNNECESSARY) throw IllegalArgumentException("Invalid rounding mode: $mode")
        roundingMode = mode
    }
    override fun getRoundingMode(): Int = roundingModeReader()
    fun setLenientParseMode(enabled: Boolean) { lenientParse = enabled }
    override fun lenientParseEnabled(): Boolean = lenientParseReader()
    fun setLenientScannerProvider(provider: RbnfLenientScannerProvider?) { scannerProvider = provider }
    fun getLenientScannerProvider(): RbnfLenientScannerProvider? {
        if (scannerProvider == null && lenientParse && !lookedForScanner) {
            try {
                lookedForScanner = true
                val provider = scannerProviderFactory()
                scannerProviderPublisher(provider)
            } catch (_: Exception) { /* Source suppresses provider discovery failure after the first attempt. */ }
        }
        return scannerProvider
    }
    override fun getLenientScanner(): RbnfLenientScanner? {
        if (lenientParse) {
            val provider = scannerProviderReader()
            if (provider != null) return provider.get(locale, lenientParseRules)
        }
        return null
    }
    fun setContext(context: DisplayContext?, setParentContext: (DisplayContext?) -> Unit) = capitalization.setContext(context, setParentContext)
    fun adjustForContext(result: String?, readContext: () -> DisplayContext?): String? = capitalization.adjustForContext(result, readContext)
    fun format(number: Long): String = formatRaw(number, defaultRuleSet)
    fun format(number: Double, maximumFractionDigits: () -> Int): String = formatRaw(number, defaultRuleSet, maximumFractionDigits)
    fun format(number: Long, name: String?): String {
        if (name!!.startsWith("%%")) throw IllegalArgumentException("Can't use internal rule set")
        return formatRaw(number, findRuleSet(name))
    }
    fun format(number: Double, name: String?, maximumFractionDigits: () -> Int): String {
        if (name!!.startsWith("%%")) throw IllegalArgumentException("Can't use internal rule set")
        return formatRaw(number, findRuleSet(name), maximumFractionDigits)
    }
    private fun formatRaw(number: Long, set: RbnfRuleSetCore?): String {
        val result = StringBuilder()
        if (number == Long.MIN_VALUE) result.append(getDecimalFormat().format(Long.MIN_VALUE))
        else set!!.format(number, result, 0, 0)
        postProcess(result, set)
        return result.toString()
    }
    private fun formatRaw(input: Double, set: RbnfRuleSetCore?, maximumFractionDigits: () -> Int): String {
        var number = input
        val result = StringBuilder()
        if (getRoundingMode() != BigDecimal.ROUND_UNNECESSARY && !number.isNaN() && !number.isInfinite()) {
            number = BigDecimal(javaCompatibleDoubleString(number)).setScale(maximumFractionDigits(), roundingMode).toDouble()
        }
        set!!.format(number, result, 0, 0)
        postProcess(result, set)
        return result.toString()
    }
    fun parse(text: String?, position: ParsePosition?): Number? {
        // Java evaluates parsePosition.getIndex before invoking substring on a null text receiver.
        val start = position!!.index
        val workingText = text!!.formatterSlice(start)
        val workingPos = ParsePosition(0)
        var result: Number? = ZERO
        val highWater = ParsePosition(workingPos.index)
        for (i in ruleSets!!.size - 1 downTo 0) {
            val set = setAt(i)
            if (!set.isPublic() || !set.isParseable()) continue
            val candidate = set.parse(workingText, workingPos, Double.MAX_VALUE, 0, 0)
            if (workingPos.index > highWater.index) { result = candidate; highWater.index = workingPos.index }
            if (workingPos.errorIndex > highWater.errorIndex) highWater.errorIndex = workingPos.errorIndex
            if (highWater.index == workingText.length) break
            workingPos.index = 0
        }
        position.index += highWater.index
        if (highWater.index == 0) position.errorIndex = position.index + highWater.errorIndex
        return result
    }
    private fun postProcess(result: StringBuilder, set: RbnfRuleSetCore?) {
        val rules = postProcessRules ?: return
        if (postProcessor == null) {
            val ix = rules.indexOf(';').let { if (it == -1) rules.length else it }
            val className = rules.formatterSlice(0, ix).trim { it <= ' ' }
            try {
                postProcessor = postProcessorFactory(className)
                postProcessor!!.init(this, rules)
            } catch (_: Exception) {
                postProcessor = null
                postProcessRules = null
                return
            }
        }
        postProcessor!!.process(result, set)
    }
    override fun toString(): String = buildString { for (i in ruleSets!!.indices) append(setAt(i).toString()) }
    fun sameRulesAs(other: RbnfFormatterCore): Boolean {
        if (!locale!!.equals(other.locale) || lenientParse != other.lenientParse) return false
        if (ruleSets!!.size != other.ruleSets!!.size) return false
        for (i in ruleSets!!.indices) if (setAt(i) != other.setAt(i)) return false
        return true
    }
    /** Format.clone is shallow: rules and substitutions retain their original formatter owner. */
    fun copy(): RbnfFormatterCore = RbnfFormatterCore(locale, scannerProviderFactory, postProcessorFactory).also {
        it.ruleSets = ruleSets; it.ruleSetsMap = ruleSetsMap; it.defaultRuleSet = defaultRuleSet
        it.roundingMode = roundingMode; it.scannerProvider = scannerProvider; it.lookedForScanner = lookedForScanner
        it.decimalFormatSymbols = decimalFormatSymbols; it.decimalFormat = decimalFormat
        it.defaultInfinityRule = defaultInfinityRule; it.defaultNaNRule = defaultNaNRule
        it.lenientParse = lenientParse; it.lenientParseRules = lenientParseRules
        it.postProcessRules = postProcessRules; it.postProcessor = postProcessor
        it.ruleSetDisplayNames = ruleSetDisplayNames; it.publicRuleSetNames = publicRuleSetNames
        it.capitalization = capitalization.copy()
    }
    companion object {
        private val ZERO: Number = 0L
        fun fromDescription(description: String?, localizations: Array<Array<String>>? = null,
                            locale: ULocale? = ULocale.getDefault(ULocale.Category.FORMAT),
                            scannerProviderFactory: () -> RbnfLenientScannerProvider,
                            postProcessorFactory: (String) -> RbnfCorePostProcessor = ::builtinPostProcessor): RbnfFormatterCore =
            RbnfFormatterCore(locale, scannerProviderFactory, postProcessorFactory).also { it.init(description, localizations) }
        fun fromLocale(locale: ULocale?, format: Int, scannerProviderFactory: () -> RbnfLenientScannerProvider,
                       loader: ICUResourceLoader? = defaultICUResourceLoader(), onBundleLocale: (ULocale) -> Unit = {},
                       postProcessorFactory: (String) -> RbnfCorePostProcessor = ::builtinPostProcessor): RbnfFormatterCore {
            val formatter = RbnfFormatterCore(locale, scannerProviderFactory, postProcessorFactory)
            val data = RbnfRuleData.load(locale, format, loader, onBundleLocale)
            formatter.init(data.description, data.localizations)
            return formatter
        }
        internal fun builtinPostProcessor(name: String): RbnfCorePostProcessor {
            if (name != "com.ibm.icu.text.RBNFChinesePostProcessor") throw IllegalArgumentException("No RBNF post processor named $name")
            return object : RbnfCorePostProcessor {
                private val processor = RbnfChinesePostProcessor()
                override fun init(formatter: RbnfFormatterCore, rules: String) {}
                override fun process(output: StringBuilder, ruleSet: RbnfRuleSetCore?) { processor.process(output, ruleSet!!.getName()) }
            }
        }
        private fun stripWhitespace(description: String?): StringBuilder {
            val length = description!!.length
            val result = StringBuilder()
            var start = 0
            while (start < length) {
                while (start < length && (PatternProps.isWhiteSpace(description[start].code) || description[start] == ';')) ++start
                val p = description.indexOf(';', start)
                if (p == -1) { result.append(description.formatterSlice(start, length)); break }
                else if (p < length) { val end = p + 1; result.append(description.formatterSlice(start, end)); start = end }
            }
            return result
        }
        private fun extractSpecial(description: StringBuilder, name: String): String? {
            val lp = description.indexOf(name)
            if (lp != -1 && (lp == 0 || description[lp - 1] == ';')) {
                var end = description.indexOf(";%", lp)
                if (end == -1) end = description.length - 1
                var start = lp + name.length
                while (start < end && PatternProps.isWhiteSpace(description[start].code)) ++start
                val result = description.toString().formatterSlice(start, end)
                description.deleteRange(lp, end + 1)
                return result
            }
            return null
        }
        private fun <T> arrayAt(array: Array<T>, index: Int): T {
            if (index !in array.indices) throw IndexOutOfBoundsException()
            return array[index]
        }
    }
}
private fun String.formatterSlice(start: Int, end: Int = length): String {
    if (start < 0 || end > length || start > end) throw IndexOutOfBoundsException()
    return substring(start, end)
}
